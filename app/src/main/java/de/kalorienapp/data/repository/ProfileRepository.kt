package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.ProfileDao
import de.kalorienapp.data.db.entity.ProfileEntity
import de.kalorienapp.domain.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepository(private val dao: ProfileDao) {
    val profile: Flow<Profile?> = dao.observe().map { it?.toDomain() }

    suspend fun get(): Profile? = dao.get()?.toDomain()

    suspend fun save(profile: Profile) = dao.upsert(ProfileEntity.fromDomain(profile))
}
