package de.kalorienapp.notify

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import de.kalorienapp.KalorienApplication
import de.kalorienapp.R
import de.kalorienapp.domain.model.DaySummary
import de.kalorienapp.ui.components.format
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Homescreen-Widget: die vier Ringe von „Heute“, „Noch X kcal“ und ein Knopf zum Erfassen.
 * Aktualisiert sich bei jeder Änderung (solange die App läuft), um Mitternacht und alle 30 Minuten.
 */
class CaloriesWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val container = (context.applicationContext as KalorienApplication).container
        container.appScope.launch {
            try {
                update(context, container.calorieNotifier.currentSummary())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun update(context: Context, summary: DaySummary?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CaloriesWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_calories)
            if (summary == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_no_profile))
                views.setTextViewText(R.id.widget_subtitle, "")
            } else {
                val remaining = summary.remainingKcal
                views.setTextViewText(
                    R.id.widget_title,
                    if (remaining >= 0) context.getString(R.string.notif_calories_left, remaining.format())
                    else context.getString(R.string.notif_calories_over, abs(remaining).format()),
                )
                views.setTextViewText(
                    R.id.widget_subtitle,
                    context.getString(
                        R.string.widget_subtitle,
                        summary.eaten.kcal.format(),
                        (summary.targets.kcal + summary.activityKcal).format(),
                    ),
                )
                views.setImageViewBitmap(R.id.widget_rings, CalorieNotifier.ringBitmap(summary, context.resources.displayMetrics.density))
            }
            views.setOnClickPendingIntent(R.id.widget_root, Notifications.openAppIntent(context, REQUEST_OPEN))
            views.setOnClickPendingIntent(
                R.id.widget_capture,
                Notifications.openAppIntent(context, REQUEST_CAPTURE, open = Notifications.OPEN_CAPTURE),
            )
            manager.updateAppWidget(ids, views)
        }

        private const val REQUEST_OPEN = 100
        private const val REQUEST_CAPTURE = 101
    }
}
