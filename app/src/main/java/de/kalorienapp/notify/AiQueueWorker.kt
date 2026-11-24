package de.kalorienapp.notify

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.kalorienapp.KalorienApplication
import de.kalorienapp.R
import de.kalorienapp.capture.AiQueue
import de.kalorienapp.capture.AnalysisResult
import de.kalorienapp.capture.AnalysisStage
import de.kalorienapp.capture.QueuedJob
import de.kalorienapp.capture.QueuedKind
import de.kalorienapp.capture.QueuedStatus
import de.kalorienapp.data.remote.gemini.GeminiError
import de.kalorienapp.data.remote.gemini.GeminiException
import de.kalorienapp.ui.components.labelRes
import kotlinx.coroutines.CancellationException

/**
 * Versucht ein abgelegtes Foto bzw. eine Beschreibung erneut erkennen zu lassen ([AiQueue]). Vorübergehende Fehler
 * (Überlastung, Kontingent, Netz) → später nochmal (WorkManager: 1, 2, 4 … Minuten, höchstens [MAX_ATTEMPTS]
 * Versuche ≈ 4 Stunden); alles andere bzw. zu lange → aufgegeben, der Nutzer kann es von Hand eintragen.
 */
class AiQueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as KalorienApplication).container
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val job = c.aiQueue.get(id) ?: return Result.success()
        if (job.status != QueuedStatus.WAITING) return Result.success()
        val noStage: (AnalysisStage) -> Unit = {}
        val uri = c.aiQueue.photoUri(job)
        try {
            val drafts = when (job.kind) {
                QueuedKind.TEXT -> c.foodAnalyzer.analyzeText(job.text.orEmpty(), noStage)
                QueuedKind.LABEL -> c.foodAnalyzer.analyzeLabel(uri, noStage)
                QueuedKind.PHOTO -> c.foodAnalyzer.analyzePhotoWithAi(uri, noStage, job.hint)
            }
            c.aiQueue.finish(id, drafts)
            notify(c.aiQueue.get(id) ?: return Result.success())
        } catch (e: CancellationException) {
            throw e
        } catch (e: GeminiException) {
            val temporary = e.error in setOf(GeminiError.NETWORK, GeminiError.OVERLOADED, GeminiError.QUOTA)
            if (temporary && runAttemptCount < MAX_ATTEMPTS) return Result.retry()
            giveUp(id, e.error.name)
        } catch (e: java.io.IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) return Result.retry()
            giveUp(id, GeminiError.NETWORK.name)
        } catch (e: Exception) {
            // Foto unlesbar o. Ä. – nicht endlos versuchen.
            giveUp(id, GeminiError.INVALID_RESPONSE.name)
        }
        return Result.success()
    }

    private suspend fun giveUp(id: String, error: String) {
        val c = (applicationContext as KalorienApplication).container
        c.aiQueue.fail(id, error)
        c.aiQueue.get(id)?.let { notify(it) }
    }

    @SuppressLint("MissingPermission") // canPost prüft die Erlaubnis
    private fun notify(job: QueuedJob) {
        val context = applicationContext
        if (!Notifications.canPost(context)) return
        val meal = context.getString(job.mealType.labelRes)
        val done = job.status == QueuedStatus.DONE
        val title = context.getString(if (done) R.string.ai_queue_notif_done else R.string.ai_queue_notif_failed)
        val text = if (done) {
            context.getString(R.string.ai_queue_notif_done_text, meal, job.drafts.joinToString { it.name })
        } else {
            context.getString(R.string.ai_queue_notif_failed_text, meal)
        }
        val notificationId = AiQueue.notificationId(job.id)
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_AI)
            .setGroup(Notifications.GROUP_OTHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(Notifications.openAppIntent(context, notificationId, open = Notifications.OPEN_AI_RESULT, queueId = job.id))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val KEY_ID = "id"
        private const val MAX_ATTEMPTS = 8
    }
}
