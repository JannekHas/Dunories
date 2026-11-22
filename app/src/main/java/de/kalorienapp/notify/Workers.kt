package de.kalorienapp.notify

import kotlinx.coroutines.sync.withLock
import de.kalorienapp.domain.WeeklySchedule
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.kalorienapp.AppContainer
import de.kalorienapp.KalorienApplication
import de.kalorienapp.R
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.db.dao.FoodEntryDao
import de.kalorienapp.data.remote.gemini.GeminiError
import de.kalorienapp.data.remote.gemini.GeminiException
import de.kalorienapp.data.repository.NotEnoughDataException
import de.kalorienapp.domain.Fasting
import de.kalorienapp.domain.MealReminderPlanner
import de.kalorienapp.domain.ReminderPlanner
import de.kalorienapp.domain.model.MealType
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Plant die wiederkehrenden Aufgaben als einmalige Arbeiten zur passenden Uhrzeit
 * (jede plant nach dem Lauf die nächste). Alles lokal über WorkManager.
 */
class WorkScheduler(
    private val context: Context,
    private val settings: AppSettings,
    private val foodDao: FoodEntryDao,
) {
    private val work get() = WorkManager.getInstance(context)

    /**
     * Beim App-Start und nach Neustart: bestehende Termine **behalten** ([ExistingWorkPolicy.KEEP]).
     * Mit REPLACE hat der App-Start, den ein Termin selbst ausgelöst hat (App war beendet), genau diesen
     * Termin abgebrochen und verschoben – so fiel z. B. die Montags-Auswertung aus.
     */
    suspend fun scheduleAll() {
        val keep = ExistingWorkPolicy.KEEP
        scheduleMealReminder(keep)
        scheduleFasting(keep)
        scheduleMidnight(keep)
        scheduleWeightReminder(policy = keep)
        scheduleWeekly(policy = keep)
        scheduleCloudBackup()
    }

    /** Cloud-Sicherung einmal täglich (nur mit Netz; der Worker tut nichts ohne Konto). */
    fun scheduleCloudBackup() {
        val request = androidx.work.PeriodicWorkRequestBuilder<CloudBackupWorker>(Duration.ofHours(24))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(NAME_CLOUD, androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Beim Verlassen der App: gleich sichern (höchstens alle paar Stunden, siehe [CloudBackupWorker]). */
    fun cloudBackupSoon() {
        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniqueWork(NAME_CLOUD_NOW, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelCloudBackup() {
        work.cancelUniqueWork(NAME_CLOUD)
        work.cancelUniqueWork(NAME_CLOUD_NOW)
    }

    /** Eintrage-Erinnerung: zur nächsten gelernten Erinnerungszeit (siehe [MealReminderPlanner]). */
    suspend fun scheduleMealReminder(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val next = if (settings.mealReminders.value) MealReminderPlanner.next(mealReminderTimes(), LocalDateTime.now()) else null
        if (next == null) {
            work.cancelUniqueWork(NAME_MEAL)
            return
        }
        enqueue<MealReminderWorker>(NAME_MEAL, next.first, policy = policy)
    }

    /** Erinnerungszeiten aus den Einträgen der letzten 3 Wochen. */
    suspend fun mealReminderTimes(): Map<MealType, LocalTime> {
        val zone = ZoneId.systemDefault()
        val rows = foodDao.mealTimes(LocalDate.now().minusDays(21).toEpochDay()).mapNotNull { row ->
            val created = Instant.ofEpochMilli(row.createdAt).atZone(zone)
            val day = LocalDate.ofEpochDay(row.dateEpochDay)
            // Nachgetragene Mahlzeiten verraten nichts über die übliche Zeit.
            if (created.toLocalDate() == day) Triple(day, row.mealType, created.toLocalTime()) else null
        }
        return MealReminderPlanner.reminderTimes(MealReminderPlanner.typicalTimes(MealReminderPlanner.samples(rows)))
    }

    /** Intervallfasten: Benachrichtigung zum nächsten Beginn/Ende des Essensfensters. */
    fun scheduleFasting(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val next = if (settings.fastingEnabled.value) Fasting.nextTransition(settings.fastingConfig.value, LocalDateTime.now()) else null
        if (next == null) {
            work.cancelUniqueWork(NAME_FASTING)
            return
        }
        enqueue<FastingWorker>(NAME_FASTING, next.first, policy = policy)
    }

    /** Kurz nach Mitternacht: Kalorien-Benachrichtigung auf den neuen Tag umstellen. */
    fun scheduleMidnight(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val next = LocalDate.now().plusDays(1).atTime(0, 1)
        enqueue<MidnightWorker>(NAME_MIDNIGHT, next, policy = policy)
    }

    /** Täglich zur Erinnerungszeit (30 min vor der üblichen ersten Mahlzeit) prüfen, ob Wiegen fällig ist. */
    suspend fun scheduleWeightReminder(fromTomorrow: Boolean = false, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        if (settings.weightReminderDays.value == null) {
            work.cancelUniqueWork(NAME_WEIGH)
            return
        }
        val time = ReminderPlanner.reminderTime(typicalFirstEntryTime())
        val todayAt = LocalDate.now().atTime(time)
        val next = if (fromTomorrow || !todayAt.isAfter(LocalDateTime.now())) todayAt.plusDays(1) else todayAt
        enqueue<WeightReminderWorker>(NAME_WEIGH, next, policy = policy)
    }

    /**
     * Montags 7 Uhr: Auswertung der letzten Woche (braucht Internet). Ist die fällige Woche noch nicht
     * erledigt (Montag ausgefallen), wird sie sofort nachgeholt – sobald Internet da ist.
     */
    fun scheduleWeekly(afterRun: Boolean = false, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        if (!settings.weeklyReport.value) {
            work.cancelUniqueWork(NAME_WEEKLY)
            work.cancelUniqueWork(NAME_WEEKLY_CATCH_UP)
            return
        }
        val now = LocalDateTime.now()
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        if (!afterRun && WeeklySchedule.needsCatchUp(now, settings.weeklyDoneFor)) {
            enqueue<WeeklyReportWorker>(NAME_WEEKLY_CATCH_UP, now, network, ExistingWorkPolicy.KEEP)
        }
        enqueue<WeeklyReportWorker>(NAME_WEEKLY, WeeklySchedule.nextRun(now), network, policy)
    }

    /** Übliche Uhrzeit des ersten Eintrags der letzten 3 Wochen (nur am selben Tag erfasste Einträge). */
    private suspend fun typicalFirstEntryTime(): LocalTime? {
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now().minusDays(21)
        val times = foodDao.firstEntryPerDay(from.toEpochDay()).mapNotNull { row ->
            val created = Instant.ofEpochMilli(row.firstCreatedAt).atZone(zone)
            // Nachträglich für einen anderen Tag eingetragene Mahlzeiten verfälschen die Uhrzeit.
            created.toLocalTime().takeIf { created.toLocalDate().toEpochDay() == row.dateEpochDay }
        }
        return ReminderPlanner.typicalFirstEntryTime(times)
    }

    private inline fun <reified W : CoroutineWorker> enqueue(
        name: String,
        at: LocalDateTime,
        constraints: Constraints = Constraints.NONE,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
    ) {
        val delay = Duration.between(LocalDateTime.now(), at).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<W>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        work.enqueueUniqueWork(name, policy, request)
    }

    companion object {
        private const val NAME_MIDNIGHT = "midnight"
        private const val NAME_FASTING = "fasting"
        private const val NAME_CLOUD = "cloud_backup"
        private const val NAME_CLOUD_NOW = "cloud_backup_now"
        private const val NAME_MEAL = "meal_reminder"
        private const val NAME_WEIGH = "weight_reminder"
        private const val NAME_WEEKLY = "weekly_report"
        private const val NAME_WEEKLY_CATCH_UP = "weekly_report_catch_up"
    }
}

private val Context.container: AppContainer get() = (applicationContext as KalorienApplication).container

class MidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.calorieNotifier.refresh()
        c.workScheduler.scheduleMidnight()
        return Result.success()
    }
}

/** Erinnert an eine fehlende Hauptmahlzeit (nicht in der Fastenzeit) und plant die nächste. */
class MealReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission") // canPost prüft die Erlaubnis
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val now = LocalDateTime.now()
        val reminders = c.workScheduler.mealReminderTimes()
        // Die Mahlzeit, deren Erinnerungszeit gerade erreicht ist (WorkManager startet manchmal etwas später).
        val meal = reminders.entries
            .filter { (_, time) -> !time.isAfter(now.toLocalTime()) && time.isAfter(now.toLocalTime().minusMinutes(90)) }
            .maxByOrNull { it.value }?.key
        val fasting = c.settings.fastingConfig.value.takeIf { c.settings.fastingEnabled.value && it.isDaily }
        val inFastingTime = fasting != null && !Fasting.isInEatingWindow(fasting, now.toLocalTime())
        if (meal != null && c.settings.mealReminders.value && !inFastingTime && Notifications.canPost(applicationContext) &&
            c.profileRepository.get() != null
        ) {
            val logged = c.foodEntryDao.mealsForDay(LocalDate.now().toEpochDay()).toSet()
            if (MealReminderPlanner.isDue(meal, logged)) {
                val mealName = applicationContext.getString(
                    when (meal) {
                        MealType.BREAKFAST -> R.string.meal_breakfast
                        MealType.LUNCH -> R.string.meal_lunch
                        MealType.DINNER -> R.string.meal_dinner
                        MealType.SNACK -> R.string.meal_snack
                    },
                )
                val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_REMINDERS)
                .setGroup(Notifications.GROUP_OTHER)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(applicationContext.getString(R.string.notif_meal_title, mealName))
                    .setContentText(applicationContext.getString(R.string.notif_meal_text))
                    .setAutoCancel(true)
                    .setContentIntent(
                        Notifications.openAppIntent(applicationContext, Notifications.ID_MEAL, open = Notifications.OPEN_CAPTURE, meal = meal.name),
                    )
                    .build()
                NotificationManagerCompat.from(applicationContext).notify(Notifications.ID_MEAL, notification)
            }
        }
        c.workScheduler.scheduleMealReminder()
        return Result.success()
    }
}

/** Meldet Beginn bzw. Ende des Essensfensters und plant den nächsten Wechsel. */
class FastingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission") // canPost prüft die Erlaubnis
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        // Kalorien-Benachrichtigung: Fastenzeile ein-/ausblenden
        c.calorieNotifier.refresh()
        val config = c.settings.fastingConfig.value
        if (c.settings.fastingEnabled.value && config.isDaily && Notifications.canPost(applicationContext)) {
            // Kurz nach dem geplanten Zeitpunkt: Fenster jetzt offen → es hat gerade begonnen.
            val opened = Fasting.isInEatingWindow(config, LocalTime.now())
            val (title, text) = if (opened) {
                R.string.notif_fasting_eat_title to applicationContext.getString(R.string.notif_fasting_eat_text, config.windowEnd.toString())
            } else {
                R.string.notif_fasting_fast_title to applicationContext.getString(R.string.notif_fasting_fast_text, config.windowStart.toString())
            }
            val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_REMINDERS)
                .setGroup(Notifications.GROUP_OTHER)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(applicationContext.getString(title))
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(Notifications.openAppIntent(applicationContext, Notifications.ID_FASTING))
                .build()
            NotificationManagerCompat.from(applicationContext).notify(Notifications.ID_FASTING, notification)
        }
        c.workScheduler.scheduleFasting()
        return Result.success()
    }
}

class WeightReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission") // canPost prüft die Erlaubnis
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val days = c.settings.weightReminderDays.value
        val today = LocalDate.now()
        // „Gewicht tracken“ aus → nie erinnern (Planung läuft weiter, damit Einschalten sofort wirkt).
        if (days != null && c.settings.weightEnabled.value && c.profileRepository.get() != null && Notifications.canPost(applicationContext)) {
            val zone = ZoneId.systemDefault()
            val last = c.weightRepository.latestDate(zone)
            // Nur vor dem Essen sinnvoll: hat man heute schon etwas eingetragen, ist es zu spät.
            val ateAlready = c.foodEntryDao.countForDay(today.toEpochDay()) > 0
            if (ReminderPlanner.isWeighInDue(last, today, days) && !ateAlready) {
                val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_REMINDERS)
                .setGroup(Notifications.GROUP_OTHER)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(applicationContext.getString(R.string.notif_weigh_title))
                    .setContentText(applicationContext.getString(R.string.notif_weigh_text))
                    .setAutoCancel(true)
                    .setContentIntent(
                        Notifications.openAppIntent(applicationContext, Notifications.ID_WEIGH, open = Notifications.OPEN_WEIGH),
                    )
                    .build()
                NotificationManagerCompat.from(applicationContext).notify(Notifications.ID_WEIGH, notification)
            }
        }
        c.workScheduler.scheduleWeightReminder(fromTomorrow = true)
        return Result.success()
    }
}

class WeeklyReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission") // canPost prüft die Erlaubnis
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        if (!c.settings.weeklyReport.value) return Result.success()
        // Regulärer Termin und Nachholen können gleichzeitig laufen → nacheinander, sonst doppelte Auswertung.
        return generationLock.withLock { runLocked() }
    }

    @SuppressLint("MissingPermission") // canPost prüft die Erlaubnis
    private suspend fun runLocked(): Result {
        val c = applicationContext.container
        val from = WeeklySchedule.dueWeekStart(LocalDateTime.now())
        val to = from.plusDays(6)
        // Regulärer Termin und Nachholen können zusammenfallen → jede Woche nur einmal.
        val done = c.settings.weeklyDoneFor
        if (done != null && !done.isBefore(from)) {
            c.workScheduler.scheduleWeekly(afterRun = true)
            return Result.success()
        }
        try {
            if (!c.weeklyReportRepository.exists(from, to)) {
                val report = c.weeklyReportRepository.generate(from, to, automatic = true)
                if (Notifications.canPost(applicationContext)) {
                    val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_REPORTS)
                .setGroup(Notifications.GROUP_OTHER)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(applicationContext.getString(R.string.notif_report_title))
                        .setContentText(report.headline.ifBlank { report.summary })
                        .setStyle(NotificationCompat.BigTextStyle().bigText(report.summary))
                        .setAutoCancel(true)
                        .setContentIntent(
                            Notifications.openAppIntent(
                                applicationContext, Notifications.ID_REPORT,
                                open = Notifications.OPEN_REPORT, reportId = report.id,
                            ),
                        )
                        .build()
                    NotificationManagerCompat.from(applicationContext).notify(Notifications.ID_REPORT, notification)
                }
            }
        } catch (e: NotEnoughDataException) {
            // Zu wenig eingetragen – diese Woche keine Auswertung.
        } catch (e: GeminiException) {
            val temporary = e.error in setOf(GeminiError.NETWORK, GeminiError.OVERLOADED, GeminiError.QUOTA)
            if (temporary && runAttemptCount < MAX_RETRIES) return Result.retry()
        } catch (e: java.io.IOException) {
            if (runAttemptCount < MAX_RETRIES) return Result.retry()
        }
        c.settings.weeklyDoneFor = from
        c.workScheduler.scheduleWeekly(afterRun = true)
        return Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 4
        val generationLock = kotlinx.coroutines.sync.Mutex()
    }
}

/** Nach Neustart/Update: Benachrichtigung wieder anzeigen und Aufgaben neu planen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                c.calorieNotifier.refresh()
                c.workScheduler.scheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}

/** Automatische Cloud-Sicherung: nur angemeldet, nur bei Änderungen, nie über den Stand eines anderen Geräts. */
class CloudBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        if (c.account.user.value == null || c.settings.cloudConflict.value) return Result.success()
        return runCatching { c.cloudBackup.upload(auto = true) }
            .fold(onSuccess = { Result.success() }, onFailure = { if (runAttemptCount < 3) Result.retry() else Result.success() })
    }
}
