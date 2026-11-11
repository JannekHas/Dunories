package de.kalorienapp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.kalorienapp.domain.model.Lifestyle
import de.kalorienapp.domain.model.Profile
import de.kalorienapp.domain.model.Sex

/** Es gibt genau ein Profil (id = 1). */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = SINGLE_ID,
    val sex: Sex,
    val birthYear: Int,
    val heightCm: Double,
    val weightKg: Double,
    val proteinPerKg: Double?,
    val kcalTargetOverride: Int?,
    val proteinOverrideG: Int?,
    val fatOverrideG: Int?,
    val carbsOverrideG: Int?,
    @ColumnInfo(defaultValue = "SEDENTARY") val lifestyle: Lifestyle = Lifestyle.SEDENTARY,
) {
    fun toDomain() = Profile(
        sex = sex,
        birthYear = birthYear,
        heightCm = heightCm,
        weightKg = weightKg,
        proteinPerKg = proteinPerKg,
        kcalTargetOverride = kcalTargetOverride,
        proteinOverrideG = proteinOverrideG,
        fatOverrideG = fatOverrideG,
        carbsOverrideG = carbsOverrideG,
        lifestyle = lifestyle,
    )

    companion object {
        const val SINGLE_ID = 1

        fun fromDomain(p: Profile) = ProfileEntity(
            sex = p.sex,
            birthYear = p.birthYear,
            heightCm = p.heightCm,
            weightKg = p.weightKg,
            proteinPerKg = p.proteinPerKg,
            kcalTargetOverride = p.kcalTargetOverride,
            proteinOverrideG = p.proteinOverrideG,
            fatOverrideG = p.fatOverrideG,
            carbsOverrideG = p.carbsOverrideG,
            lifestyle = p.lifestyle,
        )
    }
}
