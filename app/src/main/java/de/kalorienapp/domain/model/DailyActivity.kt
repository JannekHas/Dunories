package de.kalorienapp.domain.model

import de.kalorienapp.domain.ActivitySource
import java.time.LocalDate

/** Aktive Kalorien eines Tages mit Herkunft; [steps] nur wenn aus Health Connect bekannt. */
data class DailyActivity(
    val date: LocalDate,
    val activeKcal: Int,
    val steps: Long?,
    val source: ActivitySource?,
)
