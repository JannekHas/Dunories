package de.kalorienapp.ui.profile

import de.kalorienapp.domain.model.Profile
import de.kalorienapp.domain.model.Sex
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.components.toInputString

/** Eingabezustand des Profil-Formulars (Onboarding und Profil-Screen). Leere optionale Felder = automatisch. */
data class ProfileFormState(
    val sex: Sex? = null,
    val age: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val proteinPerKg: String = "",
    val kcalOverride: String = "",
    val proteinOverride: String = "",
    val fatOverride: String = "",
    val carbsOverride: String = "",
) {
    val ageValue: Int? get() = age.toIntOrNull()?.takeIf { it in 14..100 }
    val heightValue: Double? get() = heightCm.parseDecimal()?.takeIf { it in 100.0..250.0 }
    val weightValue: Double? get() = weightKg.parseDecimal()?.takeIf { it in 30.0..300.0 }

    val ageError get() = age.isNotBlank() && ageValue == null
    val heightError get() = heightCm.isNotBlank() && heightValue == null
    val weightError get() = weightKg.isNotBlank() && weightValue == null

    val proteinPerKgError get() = optionalError(proteinPerKg) { it.parseDecimal()?.takeIf { v -> v in 0.5..4.0 } }
    val kcalOverrideError get() = optionalError(kcalOverride) { it.toIntOrNull()?.takeIf { v -> v in 800..6000 } }
    val proteinOverrideError get() = optionalError(proteinOverride, ::gramValue)
    val fatOverrideError get() = optionalError(fatOverride, ::gramValue)
    val carbsOverrideError get() = optionalError(carbsOverride, ::gramValue)

    val bodyDataValid: Boolean
        get() = sex != null && ageValue != null && heightValue != null && weightValue != null

    val targetsValid: Boolean
        get() = !proteinPerKgError && !kcalOverrideError && !proteinOverrideError &&
            !fatOverrideError && !carbsOverrideError

    val isValid: Boolean get() = bodyDataValid && targetsValid

    fun toProfile(currentYear: Int): Profile? {
        if (!isValid) return null
        return Profile(
            sex = sex!!,
            birthYear = currentYear - ageValue!!,
            heightCm = heightValue!!,
            weightKg = weightValue!!,
            proteinPerKg = proteinPerKg.parseDecimal(),
            kcalTargetOverride = kcalOverride.toIntOrNull(),
            proteinOverrideG = proteinOverride.toIntOrNull(),
            fatOverrideG = fatOverride.toIntOrNull(),
            carbsOverrideG = carbsOverride.toIntOrNull(),
        )
    }

    companion object {
        fun from(profile: Profile, currentYear: Int) = ProfileFormState(
            sex = profile.sex,
            age = profile.ageIn(currentYear).toString(),
            heightCm = profile.heightCm.toInputString(),
            weightKg = profile.weightKg.toInputString(),
            proteinPerKg = profile.proteinPerKg?.toInputString() ?: "",
            kcalOverride = profile.kcalTargetOverride?.toString() ?: "",
            proteinOverride = profile.proteinOverrideG?.toString() ?: "",
            fatOverride = profile.fatOverrideG?.toString() ?: "",
            carbsOverride = profile.carbsOverrideG?.toString() ?: "",
        )

        private fun gramValue(text: String): Int? = text.toIntOrNull()?.takeIf { it in 0..1000 }

        private fun optionalError(text: String, parse: (String) -> Any?): Boolean =
            text.isNotBlank() && parse(text) == null
    }
}
