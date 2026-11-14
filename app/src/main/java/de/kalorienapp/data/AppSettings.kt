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

    /** Profil → „Tipps wieder anzeigen“. */
    fun resetTips() {
        prefs.edit().remove(KEY_SEEN_TIPS).apply()
        _seenTips.value = emptySet()
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
        private const val KEY_PLAN_ENABLED = "meal_plan_enabled"
        private const val KEY_PLAN_START = "meal_plan_start"
        private const val KEY_PLAN_DAYS = "meal_plan_days"
        private const val KEY_PLAN_END = "meal_plan_end"
        private const val KEY_PLAN_STOP = "meal_plan_stop_at_end"
    }
}
