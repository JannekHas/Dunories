package de.kalorienapp.ui.components

import androidx.annotation.StringRes
import de.kalorienapp.R
import de.kalorienapp.domain.model.ActivityLevel
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Pace
import de.kalorienapp.domain.model.Sex

@get:StringRes
val Sex.labelRes: Int
    get() = when (this) {
        Sex.MALE -> R.string.sex_male
        Sex.FEMALE -> R.string.sex_female
    }

@get:StringRes
val Goal.labelRes: Int
    get() = when (this) {
        Goal.LOSE -> R.string.goal_lose
        Goal.MAINTAIN -> R.string.goal_maintain
        Goal.GAIN -> R.string.goal_gain
    }

@StringRes
fun Pace.labelRes(goal: Goal): Int = when (goal) {
    Goal.GAIN -> if (this == Pace.SLOW) R.string.pace_gain_slow else R.string.pace_gain_normal
    else -> if (this == Pace.SLOW) R.string.pace_lose_slow else R.string.pace_lose_normal
}

@get:StringRes
val ActivityLevel.labelRes: Int
    get() = when (this) {
        ActivityLevel.SEDENTARY -> R.string.activity_sedentary
        ActivityLevel.LIGHT -> R.string.activity_light
        ActivityLevel.MODERATE -> R.string.activity_moderate
        ActivityLevel.ACTIVE -> R.string.activity_active
        ActivityLevel.VERY_ACTIVE -> R.string.activity_very_active
    }

@get:StringRes
val ActivityLevel.descriptionRes: Int
    get() = when (this) {
        ActivityLevel.SEDENTARY -> R.string.activity_sedentary_desc
        ActivityLevel.LIGHT -> R.string.activity_light_desc
        ActivityLevel.MODERATE -> R.string.activity_moderate_desc
        ActivityLevel.ACTIVE -> R.string.activity_active_desc
        ActivityLevel.VERY_ACTIVE -> R.string.activity_very_active_desc
    }

@get:StringRes
val MealType.labelRes: Int
    get() = when (this) {
        MealType.BREAKFAST -> R.string.meal_breakfast
        MealType.LUNCH -> R.string.meal_lunch
        MealType.DINNER -> R.string.meal_dinner
        MealType.SNACK -> R.string.meal_snack
    }
