package de.kalorienapp.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.kalorienapp.MainActivity
import de.kalorienapp.R

/** Kanäle, IDs und Öffnen-Intents aller Benachrichtigungen. */
object Notifications {
    const val CHANNEL_CALORIES = "calories"
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_REPORTS = "reports"
    /** Im Hintergrund erkanntes Essen (KI-Warteschlange). */
    const val CHANNEL_AI = "ai_results"

    /** Eigene Gruppen: die dauerhafte Kalorien-Anzeige wird nie mit Erinnerungen zusammengefasst. */
    const val GROUP_CALORIES = "de.kalorienapp.calories"
    const val GROUP_OTHER = "de.kalorienapp.other"

    const val ID_CALORIES = 1
    const val ID_WEIGH = 2
    const val ID_REPORT = 3
    const val ID_FASTING = 4
    const val ID_MEAL = 5

    /** Extra im Öffnen-Intent: was die App anzeigen soll. */
    const val EXTRA_OPEN = "de.kalorienapp.OPEN"
    const val EXTRA_REPORT_ID = "de.kalorienapp.REPORT_ID"
    /** Macht jedes Antippen eindeutig (sonst hält die App eine zweite gleiche Erinnerung für erledigt). */
    const val EXTRA_POSTED_AT = "de.kalorienapp.POSTED_AT"
    const val OPEN_WEIGH = "weigh"
    const val OPEN_REPORT = "report"
    const val OPEN_CAPTURE = "capture"
    /** Im Hintergrund erkanntes Foto prüfen ([EXTRA_QUEUE_ID]). */
    const val OPEN_AI_RESULT = "ai_result"
    const val EXTRA_QUEUE_ID = "de.kalorienapp.QUEUE_ID"
    const val EXTRA_MEAL = "de.kalorienapp.MEAL"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                // Leise, ohne Ton/Vibration – wird bei jedem Eintrag aktualisiert.
                NotificationChannel(CHANNEL_CALORIES, context.getString(R.string.channel_calories), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_REPORTS, context.getString(R.string.channel_reports), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_AI, context.getString(R.string.channel_ai), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Öffnet die App (laufende Instanz, singleTask) – optional mit Ziel. */
    fun openAppIntent(
        context: Context,
        requestCode: Int,
        open: String? = null,
        reportId: Long? = null,
        meal: String? = null,
        queueId: String? = null,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            open?.let { putExtra(EXTRA_OPEN, it) }
            reportId?.let { putExtra(EXTRA_REPORT_ID, it) }
            meal?.let { putExtra(EXTRA_MEAL, it) }
            queueId?.let { putExtra(EXTRA_QUEUE_ID, it) }
            putExtra(EXTRA_POSTED_AT, System.currentTimeMillis())
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
