package de.kalorienapp.domain.model

import java.time.Instant

/** Körpermaß; Umfänge in cm, Körperfett in %. */
enum class MeasureType(val isPercent: Boolean = false) {
    WAIST, HIP, CHEST, UPPER_ARM, THIGH, BODY_FAT(isPercent = true)
}

/** Eine Messung – beliebig viele, nichts wird überschrieben. */
data class BodyMeasurement(
    val id: Long = 0,
    val type: MeasureType,
    val value: Double,
    val measuredAt: Instant,
)
