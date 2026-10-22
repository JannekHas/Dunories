package de.kalorienapp.domain.model

import java.time.LocalDate

/** Selbst eingetragene verbrannte Kalorien (z. B. „Joggen“, 300 kcal) – erhöhen das Tagesbudget. */
data class ActivityEntry(
    val id: Long = 0,
    val date: LocalDate,
    /** Optionale Bezeichnung; leer = „Aktivität“. */
    val name: String,
    val kcal: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
