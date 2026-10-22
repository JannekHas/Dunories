package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.WeightEntryDao
import de.kalorienapp.data.db.entity.WeightEntryEntity
import de.kalorienapp.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** Gewichtsverlauf. Der jüngste Eintrag wird ins Profil übernommen (Grundumsatz/Ziele). */
class WeightRepository(
    private val dao: WeightEntryDao,
    private val profileRepository: ProfileRepository,
) {
    val entries: Flow<List<WeightEntry>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Datum der letzten Messung (für die Wiege-Erinnerung). */
    suspend fun latestDate(zone: ZoneId): LocalDate? =
        dao.latest()?.toDomain()?.measuredAt?.atZone(zone)?.toLocalDate()

    suspend fun save(entry: WeightEntry) {
        dao.upsert(WeightEntryEntity.fromDomain(entry))
        syncProfileWeight()
    }

    suspend fun delete(entry: WeightEntry) {
        dao.delete(entry.id)
        syncProfileWeight()
    }

    /** Profilgewicht = zuletzt gemessener Wert (falls vorhanden). */
    private suspend fun syncProfileWeight() {
        val latest = dao.latest() ?: return
        val profile = profileRepository.get() ?: return
        if (profile.weightKg != latest.weightKg) {
            profileRepository.save(profile.copy(weightKg = latest.weightKg))
        }
    }
}
