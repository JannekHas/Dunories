package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.BodyMeasurementDao
import de.kalorienapp.data.db.dao.ProfileDao
import de.kalorienapp.data.db.entity.ProfileEntity
import de.kalorienapp.domain.model.MeasureType
import de.kalorienapp.domain.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

class ProfileRepository(private val dao: ProfileDao, private val measurementDao: BodyMeasurementDao) {

    /** Letzte Körperfett-Messung, wenn höchstens ein Jahr alt (für das Protein-Bezugsgewicht). */
    val bodyFatPercent: Flow<Double?> = measurementDao.observeLatest(MeasureType.BODY_FAT.name).map { latest ->
        latest?.takeIf { Duration.between(Instant.ofEpochMilli(it.measuredAt), Instant.now()) <= MAX_BODY_FAT_AGE }?.value
    }

    val profile: Flow<Profile?> = combine(dao.observe(), bodyFatPercent) { entity, bodyFat ->
        entity?.toDomain()?.copy(bodyFatPercent = bodyFat)
    }

    suspend fun get(): Profile? = dao.get()?.toDomain()?.copy(bodyFatPercent = bodyFatPercent.first())

    suspend fun save(profile: Profile) = dao.upsert(ProfileEntity.fromDomain(profile))

    private companion object {
        val MAX_BODY_FAT_AGE: Duration = Duration.ofDays(365)
    }
}
