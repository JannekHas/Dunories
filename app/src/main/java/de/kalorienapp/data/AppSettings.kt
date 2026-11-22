package de.kalorienapp.data

import android.content.Context
import de.kalorienapp.domain.FastingConfig
import de.kalorienapp.domain.FastingPlan
import de.kalorienapp.domain.model.Budget
import de.kalorienapp.domain.model.CookingTime
import de.kalorienapp.domain.model.DietStyle
import de.kalorienapp.domain.model.ExcludedFood
import de.kalorienapp.domain.model.Intolerance
import de.kalorienapp.domain.model.FoodPreferences
import de.kalorienapp.domain.model.MealPlanSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalTime

/** Einstellungen für Benachrichtigungen (klein, kein Teil des Profils → SharedPreferences statt Room). */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _caloriesNotification = MutableStateFlow(prefs.getBoolean(KEY_CALORIES, false))
    /** Dauerhafte Benachrichtigung mit den verbleibenden kcal. */
    val caloriesNotification: StateFlow<Boolean> = _caloriesNotification.asStateFlow()

    private val _weightReminderDays = MutableStateFlow(
        prefs.getInt(KEY_WEIGHT_DAYS, DEFAULT_WEIGHT_DAYS).takeIf { it > 0 },
    )
    /** Erinnerung, wenn die letzte Messung so viele Tage alt ist; null = aus. */
    val weightReminderDays: StateFlow<Int?> = _weightReminderDays.asStateFlow()

    private val _weeklyReport = MutableStateFlow(prefs.getBoolean(KEY_WEEKLY, true))
    /** Montags automatisch eine KI-Wochenauswertung erstellen. */
    val weeklyReport: StateFlow<Boolean> = _weeklyReport.asStateFlow()

    private val _waterEnabled = MutableStateFlow(prefs.getBoolean(KEY_WATER_ENABLED, false))

    /** Wasser-Karte auf „Heute“ (und Wasser in der KI-Auswertung); standardmäßig aus. */
    val waterEnabled: StateFlow<Boolean> = _waterEnabled.asStateFlow()

    fun setWaterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WATER_ENABLED, enabled).apply()
        _waterEnabled.value = enabled
    }

    private val _proteinOnly = MutableStateFlow(prefs.getBoolean(KEY_PROTEIN_ONLY, false))

    /** „Nur Eiweiß“: Kalorien, KH und Fett überall ausblenden, nur Eiweiß zählt (standardmäßig aus). */
    val proteinOnly: StateFlow<Boolean> = _proteinOnly.asStateFlow()

    fun setProteinOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROTEIN_ONLY, enabled).apply()
        _proteinOnly.value = enabled
    }

    private val _weightEnabled = MutableStateFlow(prefs.getBoolean(KEY_WEIGHT_ENABLED, true))

    /**
     * Gewicht tracken (standardmäßig an). Aus: Reiter „Körper“, Wiege-Erinnerung und Gewicht in der
     * KI-Auswertung verschwinden; ein laufendes Gewichtsziel rechnet unsichtbar mit dem letzten Gewicht weiter.
     */
    val weightEnabled: StateFlow<Boolean> = _weightEnabled.asStateFlow()

    fun setWeightEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEIGHT_ENABLED, enabled).apply()
        _weightEnabled.value = enabled
    }

    private val _bodyAnalysis = MutableStateFlow(prefs.getBoolean(KEY_BODY_ANALYSIS, false))

    /** Körperanalyse (Bioimpedanz-Werte) unter Statistik → Körper; standardmäßig aus, nur mit „Gewicht tracken“. */
    val bodyAnalysisEnabled: StateFlow<Boolean> = _bodyAnalysis.asStateFlow()

    fun setBodyAnalysisEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BODY_ANALYSIS, enabled).apply()
        _bodyAnalysis.value = enabled
    }

    private val _measures = MutableStateFlow(prefs.getBoolean(KEY_MEASURES, false))

    /** Körpermaße (Taille, Hüfte, Brust, Oberarm, Oberschenkel) unter Statistik → Körper; standardmäßig aus. */
    val measuresEnabled: StateFlow<Boolean> = _measures.asStateFlow()

    fun setMeasuresEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEASURES, enabled).apply()
        _measures.value = enabled
    }

    private val _goalSkipped = MutableStateFlow(prefs.getBoolean(KEY_GOAL_SKIPPED, false))

    /** In der Einrichtung „Erstmal ohne Ziel“ gewählt → kein Ziel-Banner auf „Heute“. */
    val goalSkipped: StateFlow<Boolean> = _goalSkipped.asStateFlow()

    fun setGoalSkipped(skipped: Boolean) {
        prefs.edit().putBoolean(KEY_GOAL_SKIPPED, skipped).apply()
        _goalSkipped.value = skipped
    }

    private val _waterGoalMl = MutableStateFlow(prefs.getInt(KEY_WATER_GOAL, 0).takeIf { it > 0 })
    /** Eigenes Wasser-Tagesziel in ml; null = automatisch aus dem Gewicht. */
    val waterGoalMl: StateFlow<Int?> = _waterGoalMl.asStateFlow()

    fun setWaterGoalMl(ml: Int?) {
        prefs.edit().putInt(KEY_WATER_GOAL, ml ?: 0).apply()
        _waterGoalMl.value = ml
    }

    private val _mealReminders = MutableStateFlow(prefs.getBoolean(KEY_MEAL_REMINDERS, true))
    /** Erinnerung, wenn eine Hauptmahlzeit zur üblichen Zeit noch fehlt. */
    val mealReminders: StateFlow<Boolean> = _mealReminders.asStateFlow()

    fun setMealReminders(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEAL_REMINDERS, enabled).apply()
        _mealReminders.value = enabled
    }

    private val _fastingEnabled = MutableStateFlow(prefs.getBoolean(KEY_FASTING, false))
    /** Intervallfasten: Timer auf „Heute“, Benachrichtigungen, Auswertung. */
    val fastingEnabled: StateFlow<Boolean> = _fastingEnabled.asStateFlow()

    private val _fastingConfig = MutableStateFlow(readFastingConfig())
    val fastingConfig: StateFlow<FastingConfig> = _fastingConfig.asStateFlow()

    fun setFastingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FASTING, enabled).apply()
        _fastingEnabled.value = enabled
    }

    fun setFastingConfig(config: FastingConfig) {
        prefs.edit()
            .putString(KEY_FASTING_PLAN, config.plan.name)
            .putInt(KEY_FASTING_START, config.windowStart.toSecondOfDay() / 60)
            .putInt(KEY_FASTING_HOURS, config.customFastHours)
            .putString(KEY_FASTING_DAYS, config.fastDays.joinToString(",") { it.name })
            .apply()
        _fastingConfig.value = config
    }

    private fun readFastingConfig(): FastingConfig {
        val defaults = FastingConfig()
        return FastingConfig(
            plan = FastingPlan.entries.find { it.name == prefs.getString(KEY_FASTING_PLAN, null) } ?: defaults.plan,
            windowStart = prefs.getInt(KEY_FASTING_START, -1).takeIf { it in 0 until 24 * 60 }
                ?.let { LocalTime.of(it / 60, it % 60) } ?: defaults.windowStart,
            customFastHours = prefs.getInt(KEY_FASTING_HOURS, defaults.customFastHours),
            fastDays = prefs.getString(KEY_FASTING_DAYS, null)?.split(',')
                ?.mapNotNull { name -> DayOfWeek.entries.find { it.name == name } }?.toSet() ?: defaults.fastDays,
        )
    }

    fun setCaloriesNotification(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALORIES, enabled).apply()
        _caloriesNotification.value = enabled
    }

    fun setWeightReminderDays(days: Int?) {
        prefs.edit().putInt(KEY_WEIGHT_DAYS, days ?: 0).apply()
        _weightReminderDays.value = days
    }

    fun setWeeklyReport(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEEKLY, enabled).apply()
        _weeklyReport.value = enabled
    }

    private val _seenTips = MutableStateFlow(prefs.getStringSet(KEY_SEEN_TIPS, emptySet()).orEmpty().toSet())
    /** Namen der weggeklickten Tipps ([de.kalorienapp.ui.components.Tip]). */
    val seenTips: StateFlow<Set<String>> = _seenTips.asStateFlow()

    fun markTipSeen(name: String) {
        val updated = _seenTips.value + name
        prefs.edit().putStringSet(KEY_SEEN_TIPS, updated).apply()
        _seenTips.value = updated
    }

    /**
     * Alle bisher mit apply() gemerkten Änderungen sicher auf die Festplatte schreiben – vor dem harten Neustart
     * nach „Sicherung einspielen“ (sonst gehen die eingespielten Einstellungen verloren). commit() wartet auf
     * die ausstehenden Schreibvorgänge.
     */
    fun flushToDisk() {
        prefs.edit().commit()
    }

    // ------------------------------------------------------------ Konto / Cloud-Sicherung (gerätegebunden, nicht in der Sicherung)

    val cloudUid: String? get() = prefs.getString(KEY_CLOUD_UID, null)
    val cloudLastUploadAt: Long? get() = prefs.getLong(KEY_CLOUD_AT, 0L).takeIf { it > 0 }
    val cloudLastHash: String get() = prefs.getString(KEY_CLOUD_HASH, null).orEmpty()

    private val _cloudBackupAt = MutableStateFlow(cloudLastUploadAt)

    /** Zeitpunkt des letzten Stands, den dieses Handy in der Cloud hat (für „Zuletzt gesichert …“). */
    val cloudBackupAt: StateFlow<Long?> = _cloudBackupAt.asStateFlow()

    fun setCloudUploaded(uid: String, at: Long, hash: String) {
        prefs.edit().putString(KEY_CLOUD_UID, uid).putLong(KEY_CLOUD_AT, at).putString(KEY_CLOUD_HASH, hash).apply()
        _cloudBackupAt.value = at
    }

    private val _cloudConflict = MutableStateFlow(prefs.getBoolean(KEY_CLOUD_CONFLICT, false))

    /** Ein anderes Gerät hat gesichert → automatisches Sichern pausiert, bis der Nutzer vergleicht. */
    val cloudConflict: StateFlow<Boolean> = _cloudConflict.asStateFlow()

    fun setCloudConflict(conflict: Boolean) {
        prefs.edit().putBoolean(KEY_CLOUD_CONFLICT, conflict).apply()
        _cloudConflict.value = conflict
    }

    private val _cloudBackupOn = MutableStateFlow(prefs.getInt(KEY_CLOUD_CONSENT, 0) > 0)

    /**
     * Cloud-Sicherung eingeschaltet = Einwilligung auf diesem Handy gegeben. Seit der Trennung von Konto und
     * Cloud-Sicherung (Nov. 2026) ist das ein eigener Schalter in der Konto-Karte; Konto allein = nur Premium/Codes.
     */
    val cloudBackupOn: StateFlow<Boolean> = _cloudBackupOn.asStateFlow()

    /** Einwilligung zur Cloud-Speicherung auf diesem Handy gegeben (Textversion, 0 = aus). */
    var cloudConsentVersion: Int
        get() = prefs.getInt(KEY_CLOUD_CONSENT, 0)
        set(value) {
            prefs.edit().putInt(KEY_CLOUD_CONSENT, value).apply()
            _cloudBackupOn.value = value > 0
        }

    fun clearCloud() {
        prefs.edit().remove(KEY_CLOUD_UID).remove(KEY_CLOUD_AT).remove(KEY_CLOUD_HASH).remove(KEY_CLOUD_CONFLICT).remove(KEY_CLOUD_CONSENT).apply()
        _cloudBackupAt.value = null
        _cloudConflict.value = false
        _cloudBackupOn.value = false
    }

    /** Freiwilliger Name beim Feedback (wird beim nächsten Mal vorausgefüllt). */
    val feedbackName: String get() = prefs.getString(KEY_FEEDBACK_NAME, null).orEmpty()

    fun setFeedbackName(name: String) = prefs.edit().putString(KEY_FEEDBACK_NAME, name).apply()

    private val _feedbackOnToday = MutableStateFlow(prefs.getBoolean(KEY_FEEDBACK_TODAY, true))

    /** Feedback-Symbol auf „Heute“ (Testphase) – Fernschalter `config/app.feedbackOnToday` in Firestore. */
    val feedbackOnToday: StateFlow<Boolean> = _feedbackOnToday.asStateFlow()

    fun setFeedbackOnToday(show: Boolean) {
        prefs.edit().putBoolean(KEY_FEEDBACK_TODAY, show).apply()
        _feedbackOnToday.value = show
    }

    /** Sicherung einspielen: gesehene/ausstehende Tipps und Abtipp-Zähler übernehmen. */
    fun restoreTips(seen: Set<String>, pending: Set<String>, manualTyped: Int) {
        prefs.edit().putStringSet(KEY_SEEN_TIPS, seen).putStringSet(KEY_PENDING_TIPS, pending).putInt(KEY_MANUAL_TYPED, manualTyped).apply()
        _seenTips.value = seen
        _pendingTips.value = pending
    }

    val manualTyped: Int get() = prefs.getInt(KEY_MANUAL_TYPED, 0)

    /** Profil → „Tipps wieder anzeigen“ (auch die Tipps nach Verhalten). */
    fun resetTips() {
        prefs.edit().remove(KEY_SEEN_TIPS).remove(KEY_PENDING_TIPS).remove(KEY_TIP_OF_DAY).remove(KEY_MANUAL_TYPED).apply()
        _seenTips.value = emptySet()
        _pendingTips.value = emptySet()
        _tipOfDay.value = null
    }

    private val _pendingTips = MutableStateFlow(prefs.getStringSet(KEY_PENDING_TIPS, emptySet()).orEmpty().toSet())

    /** Tipps nach Verhalten, deren Auslöser schon eingetreten ist und die beim nächsten Erfassen erscheinen. */
    val pendingTips: StateFlow<Set<String>> = _pendingTips.asStateFlow()

    fun addPendingTip(name: String) {
        if (name in _pendingTips.value || name in _seenTips.value) return
        val updated = _pendingTips.value + name
        prefs.edit().putStringSet(KEY_PENDING_TIPS, updated).apply()
        _pendingTips.value = updated
    }

    private val _tipOfDay = MutableStateFlow(prefs.getString(KEY_TIP_OF_DAY, null))

    /** „Tag:Name“ des heute gezeigten Tipps nach Verhalten – höchstens einer pro Tag. */
    val tipOfDay: StateFlow<String?> = _tipOfDay.asStateFlow()

    /** Darf [name] heute erscheinen? Ja, wenn heute noch keiner kam oder es genau dieser ist; dann gilt er als heutiger. */
    fun claimTipOfDay(name: String, epochDay: Long): Boolean {
        val current = _tipOfDay.value
        val value = "$epochDay:$name"
        if (current == value) return true
        if (current?.substringBefore(':') == epochDay.toString()) return false
        prefs.edit().putString(KEY_TIP_OF_DAY, value).apply()
        _tipOfDay.value = value
        return true
    }

    /** Wie oft ein Lebensmittel von Hand abgetippt wurde (für den Tipp „Barcode/Nährwerttabelle“). */
    fun countManualTyped(count: Int = 1): Int {
        val total = prefs.getInt(KEY_MANUAL_TYPED, 0) + count
        prefs.edit().putInt(KEY_MANUAL_TYPED, total).apply()
        return total
    }

    private val _foodPreferences = MutableStateFlow(readFoodPreferences())
    /** Ernährungsweise, Allergien, Alltag und „mag ich nicht“ für die KI-Wochenauswertung. */
    val foodPreferences: StateFlow<FoodPreferences> = _foodPreferences.asStateFlow()

    fun setFoodPreferences(value: FoodPreferences) {
        prefs.edit()
            .putString(KEY_DIET_STYLE, value.style.name)
            .putStringSet(KEY_EXCLUDED, value.excluded.map { it.name }.toSet())
            .putStringSet(KEY_INTOLERANCES, value.intolerances.map { it.name }.toSet())
            .putString(KEY_COOKING, value.cooking.name)
            .putString(KEY_BUDGET, value.budget.name)
            .putBoolean(KEY_MEAL_PREP, value.mealPrep)
            .putString(KEY_DISLIKES, value.dislikes.joinToString(DISLIKE_SEPARATOR))
            .apply()
        _foodPreferences.value = value
    }

    fun updateFoodPreferences(transform: (FoodPreferences) -> FoodPreferences) =
        setFoodPreferences(transform(_foodPreferences.value))

    fun addDislike(name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || _foodPreferences.value.dislikes(clean)) return
        updateFoodPreferences { it.copy(dislikes = it.dislikes + clean) }
    }

    fun removeDislike(name: String) =
        updateFoodPreferences { p -> p.copy(dislikes = p.dislikes.filterNot { it.equals(name, ignoreCase = true) }) }

    private fun readFoodPreferences(): FoodPreferences {
        val defaults = FoodPreferences()
        return FoodPreferences(
            style = DietStyle.entries.find { it.name == prefs.getString(KEY_DIET_STYLE, null) } ?: defaults.style,
            excluded = prefs.getStringSet(KEY_EXCLUDED, emptySet()).orEmpty()
                .mapNotNull { name -> ExcludedFood.entries.find { it.name == name } }.toSet(),
            intolerances = prefs.getStringSet(KEY_INTOLERANCES, emptySet()).orEmpty()
                .mapNotNull { name -> Intolerance.entries.find { it.name == name } }.toSet(),
            cooking = CookingTime.entries.find { it.name == prefs.getString(KEY_COOKING, null) } ?: defaults.cooking,
            budget = Budget.entries.find { it.name == prefs.getString(KEY_BUDGET, null) } ?: defaults.budget,
            mealPrep = prefs.getBoolean(KEY_MEAL_PREP, false),
            dislikes = prefs.getString(KEY_DISLIKES, null).orEmpty().split(DISLIKE_SEPARATOR).filter(String::isNotBlank),
        )
    }

    /** Wochenbeginn (Montag) der zuletzt automatisch erledigten Wochenauswertung; null = noch nie. */
    var weeklyDoneFor: java.time.LocalDate?
        get() = prefs.getLong(KEY_WEEKLY_DONE, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(java.time.LocalDate::ofEpochDay)
        set(value) = prefs.edit().putLong(KEY_WEEKLY_DONE, value?.toEpochDay() ?: Long.MIN_VALUE).apply()

    private val _changelogSeen = MutableStateFlow(prefs.getInt(KEY_CHANGELOG_SEEN, 0).takeIf { it > 0 })

    /** versionCode, dessen Patchnotes zuletzt gelesen wurden (null = noch nie). */
    val changelogSeen: StateFlow<Int?> = _changelogSeen.asStateFlow()

    fun markChangelogSeen(versionCode: Int) {
        prefs.edit().putInt(KEY_CHANGELOG_SEEN, versionCode).apply()
        _changelogSeen.value = versionCode
    }

    private val _mealPlan = MutableStateFlow(readMealPlan())

    /** Ernährungsplan: an/aus, Start des Durchlaufs, Anzahl Tage. */
    val mealPlan: StateFlow<MealPlanSettings> = _mealPlan.asStateFlow()

    fun setMealPlan(value: MealPlanSettings) {
        prefs.edit()
            .putBoolean(KEY_PLAN_ENABLED, value.enabled)
            .putLong(KEY_PLAN_START, value.startEpochDay)
            .putInt(KEY_PLAN_DAYS, value.days)
            .putLong(KEY_PLAN_END, value.endEpochDay ?: Long.MIN_VALUE)
            .putBoolean(KEY_PLAN_STOP, value.stopAtEnd)
            .putBoolean(KEY_PLAN_OPEN, value.openEnded)
            .apply()
        _mealPlan.value = value
    }

    fun updateMealPlan(transform: (MealPlanSettings) -> MealPlanSettings) = setMealPlan(transform(_mealPlan.value))

    private fun readMealPlan(): MealPlanSettings {
        val defaults = MealPlanSettings()
        return MealPlanSettings(
            enabled = prefs.getBoolean(KEY_PLAN_ENABLED, false),
            startEpochDay = prefs.getLong(KEY_PLAN_START, defaults.startEpochDay),
            days = prefs.getInt(KEY_PLAN_DAYS, defaults.days).coerceIn(1, MealPlanSettings.MAX_DAYS),
            endEpochDay = prefs.getLong(KEY_PLAN_END, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE },
            stopAtEnd = prefs.getBoolean(KEY_PLAN_STOP, false),
            openEnded = prefs.getBoolean(KEY_PLAN_OPEN, false),
        )
    }

    /** Ob schon einmal nach der Benachrichtigungs-Erlaubnis gefragt wurde (nicht bei jedem Start nerven). */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_ASKED, value).apply()

    companion object {
        const val DEFAULT_WEIGHT_DAYS = 3
        private const val KEY_CALORIES = "calories_notification"
        private const val KEY_WEIGHT_DAYS = "weight_reminder_days"
        private const val KEY_WEEKLY = "weekly_report"
        private const val KEY_WATER_GOAL = "water_goal_ml"
        private const val KEY_WATER_ENABLED = "water_enabled"
        private const val KEY_PROTEIN_ONLY = "protein_only"
        private const val KEY_WEIGHT_ENABLED = "weight_enabled"
        private const val KEY_BODY_ANALYSIS = "body_analysis"
        private const val KEY_MEASURES = "measures"
        private const val KEY_GOAL_SKIPPED = "goal_skipped"
        private const val KEY_FASTING = "fasting_enabled"
        private const val KEY_MEAL_REMINDERS = "meal_reminders"
        private const val KEY_FASTING_PLAN = "fasting_plan"
        private const val KEY_FASTING_START = "fasting_window_start_min"
        private const val KEY_FASTING_HOURS = "fasting_custom_hours"
        private const val KEY_FASTING_DAYS = "fasting_days"
        private const val KEY_PERMISSION_ASKED = "notification_permission_asked"
        private const val KEY_DIET_STYLE = "diet_style"
        private const val KEY_EXCLUDED = "diet_excluded"
        private const val KEY_INTOLERANCES = "diet_intolerances"
        private const val KEY_COOKING = "diet_cooking"
        private const val KEY_BUDGET = "diet_budget"
        private const val KEY_MEAL_PREP = "diet_meal_prep"
        private const val KEY_DISLIKES = "disliked_foods"
        private const val DISLIKE_SEPARATOR = "\n"
        private const val KEY_SEEN_TIPS = "seen_tips"
        private const val KEY_FEEDBACK_NAME = "feedback_name"
        private const val KEY_CLOUD_UID = "cloud_uid"
        private const val KEY_CLOUD_AT = "cloud_last_upload_at"
        private const val KEY_CLOUD_HASH = "cloud_last_hash"
        private const val KEY_CLOUD_CONFLICT = "cloud_conflict"
        private const val KEY_CLOUD_CONSENT = "cloud_consent_version"
        private const val KEY_FEEDBACK_TODAY = "feedback_on_today"
        private const val KEY_PENDING_TIPS = "pending_tips"
        private const val KEY_TIP_OF_DAY = "tip_of_day"
        private const val KEY_MANUAL_TYPED = "manual_typed"
        private const val KEY_PLAN_ENABLED = "meal_plan_enabled"
        private const val KEY_CHANGELOG_SEEN = "changelog_seen_version"
        private const val KEY_WEEKLY_DONE = "weekly_report_done_for"
        private const val KEY_PLAN_START = "meal_plan_start"
        private const val KEY_PLAN_DAYS = "meal_plan_days"
        private const val KEY_PLAN_END = "meal_plan_end"
        private const val KEY_PLAN_STOP = "meal_plan_stop_at_end"
        private const val KEY_PLAN_OPEN = "meal_plan_open_ended"
    }
}
