package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.BodyMeasurementDao
import de.kalorienapp.data.db.entity.BodyMeasurementEntity
import de.kalorienapp.domain.model.BodyMeasurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BodyMeasurementRepository(private val dao: BodyMeasurementDao) {
    val all: Flow<List<BodyMeasurement>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun add(items: List<BodyMeasurement>) = dao.insert(items.map(BodyMeasurementEntity::fromDomain))

    suspend fun delete(item: BodyMeasurement) = dao.delete(item.id)
}
