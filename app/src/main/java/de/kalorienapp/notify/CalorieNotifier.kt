package de.kalorienapp.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.graphics.toArgb
import de.kalorienapp.KalorienApplication
import de.kalorienapp.R
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import de.kalorienapp.domain.Fasting
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.repository.ActivityEntryRepository
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.data.repository.GoalRepository
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.domain.model.ActivityEntry
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.DaySummary
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.sum
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dauerhafte Benachrichtigung „Noch X kcal“ mit den vier Ringen als Bild.
 * Wird sie weggewischt (ab Android 14 möglich), zeigt sie [DismissReceiver] sofort wieder an.
 */
class CalorieNotifier(
    private val context: Context,
    private val settings: AppSettings,
    private val goals: GoalRepository,
    private val entries: FoodEntryRepository,
    private val activities: ActivityEntryRepository,
) {
    /** Angezeigter Tag; wird beim Tageswechsel (Worker, App-Start) neu gesetzt. */
    private val today = MutableStateFlow(LocalDate.now())

    /** Solange der App-Prozess läuft: bei jeder Änderung (Eintrag, Ziel, Einstellung) aktualisieren. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(scope: CoroutineScope) {
        scope.launch {
            today.flatMapLatest { day ->
                combine(
                    settings.caloriesNotification,
                    goals.current,
                    entries.entriesFor(day),
                    activities.entriesFor(day),
                    // Fasten an/aus oder Plan geändert → Fastenzeile sofort anpassen
                    combine(settings.fastingEnabled, settings.fastingConfig) { on, config -> on to config },
                ) { enabled, state, eaten, acts, _ ->
                    enabled to state?.let { summaryOf(it.targetsFor(day.year), eaten, acts) }
                }
            }.collect { (enabled, summary) ->
                // Widget immer aktualisieren, die Benachrichtigung nur wenn eingeschaltet.
                CaloriesWidget.update(context, summary)
                if (enabled && summary != null) post(summary) else cancel()
            }
        }
    }

    /** Von außen (Worker, Wegwischen, Neustart, Widget): Tag prüfen und neu anzeigen. */
    suspend fun refresh() {
        today.value = LocalDate.now()
        val summary = currentSummary()
        CaloriesWidget.update(context, summary)
        if (!settings.caloriesNotification.value || summary == null) {
            cancel()
            return
        }
        post(summary)
    }

    /** Heutige Bilanz (null ohne Profil). */
    suspend fun currentSummary(): DaySummary? {
        val day = LocalDate.now()
        val state = goals.current.first() ?: return null
        return summaryOf(state.targetsFor(day.year), entries.entriesFor(day).first(), activities.entriesFor(day).first())
    }

    fun cancel() = NotificationManagerCompat.from(context).cancel(Notifications.ID_CALORIES)

    private fun summaryOf(targets: DailyTargets, eaten: List<FoodEntry>, acts: List<ActivityEntry>) =
        GoalCalculator.daySummary(targets, eaten.map(FoodEntry::nutrients).sum(), acts.sumOf(ActivityEntry::kcal))

    @SuppressLint("MissingPermission") // canPost prüft die Erlaubnis
    private fun post(summary: DaySummary) {
        if (!Notifications.canPost(context)) return
        val remaining = summary.remainingKcal
        val title = if (remaining >= 0) {
            context.getString(R.string.notif_calories_left, remaining.format())
        } else {
            context.getString(R.string.notif_calories_over, abs(remaining).format())
        }
        val n = summary.eaten
        val t = summary.targets
        val macros = context.getString(
            R.string.notif_calories_macros,
            n.proteinG.format(), t.proteinG.format(), n.carbsG.format(), t.carbsG.format(), n.fatG.format(), t.fatG.format(),
        )
        // In der Fastenzeit steht der Hinweis vorne – zugeklappt sieht man nur eine Zeile.
        val fasting = fastingLine()
        val text = fasting ?: macros
        val bigText = if (fasting != null) "$fasting\n$macros" else macros
        val dismiss = PendingIntent.getBroadcast(
            context, 0, Intent(context, DismissReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_CALORIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setLargeIcon(ringBitmap(summary, context.resources.displayMetrics.density))
            .setColor(Neon.Cyan.toArgb())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Antippen öffnet die App, die Benachrichtigung bleibt (kein AutoCancel).
            .setContentIntent(Notifications.openAppIntent(context, Notifications.ID_CALORIES))
            .setDeleteIntent(dismiss)
            .build()
        NotificationManagerCompat.from(context).notify(Notifications.ID_CALORIES, notification)
    }

    /**
     * „Fastenzeit · Du darfst um 12:00 Uhr wieder essen“ bzw. „Heute ist Fastentag“ (5:2); null außerhalb.
     * Aktualisiert beim Fensterwechsel ([de.kalorienapp.notify.FastingWorker]), nicht sekündlich.
     */
    private fun fastingLine(): String? {
        if (!settings.fastingEnabled.value) return null
        val config = settings.fastingConfig.value
        val now = LocalDateTime.now()
        if (!config.isDaily) {
            return if (now.dayOfWeek in config.fastDays) context.getString(R.string.notif_calories_fast_day) else null
        }
        if (Fasting.isInEatingWindow(config, now.toLocalTime())) return null
        return context.getString(R.string.notif_calories_fasting, config.windowStart.format(TIME_FORMAT))
    }

    /** Weggewischt → sofort wieder anzeigen (solange der Schalter an ist). */
    class DismissReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            val container = (context.applicationContext as KalorienApplication).container
            container.appScope.launch {
                try {
                    container.calorieNotifier.refresh()
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        private const val SIZE_DP = 64
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** Die vier Ringe von „Heute“: außen kcal (Regenbogen), dann Protein, KH, Fett. */
        fun ringBitmap(summary: DaySummary, density: Float): Bitmap {
            val size = (SIZE_DP * density).roundToInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val stroke = size * 0.085f
            val gap = size * 0.035f
            val budget = (summary.targets.kcal + summary.activityKcal).coerceAtLeast(1)
            val rings = listOf(
                (summary.eaten.kcal / budget).toFloat() to null,
                ratio(summary.eaten.proteinG, summary.targets.proteinG) to Neon.Magenta.toArgb(),
                ratio(summary.eaten.carbsG, summary.targets.carbsG) to Neon.Yellow.toArgb(),
                ratio(summary.eaten.fatG, summary.targets.fatG) to Neon.Green.toArgb(),
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            rings.forEachIndexed { index, (progress, color) ->
                val inset = stroke / 2 + index * (stroke + gap)
                val rect = RectF(inset, inset, size - inset, size - inset)
                paint.shader = null
                paint.color = Neon.Track.toArgb()
                canvas.drawArc(rect, 0f, 360f, false, paint)
                val main = progress.coerceIn(0f, 1f)
                if (main > 0f) {
                    if (color == null) {
                        val colors = (Neon.Rainbow + Neon.Rainbow.first()).map { it.toArgb() }.toIntArray()
                        paint.shader = SweepGradient(size / 2f, size / 2f, colors, null).apply {
                            setLocalMatrix(android.graphics.Matrix().apply { setRotate(-90f, size / 2f, size / 2f) })
                        }
                    } else {
                        paint.color = color
                    }
                    canvas.drawArc(rect, -90f, 360f * main, false, paint)
                }
                if (progress > 1f) {
                    paint.shader = null
                    paint.color = Neon.Red.toArgb()
                    paint.strokeWidth = stroke * 0.45f
                    canvas.drawArc(rect, -90f, 360f * (progress - 1f).coerceAtMost(1f), false, paint)
                    paint.strokeWidth = stroke
                }
            }
            return bitmap
        }

        private fun ratio(eaten: Double, target: Int) = if (target > 0) (eaten / target).toFloat() else 0f
    }
}
