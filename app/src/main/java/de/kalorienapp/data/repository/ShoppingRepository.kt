package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.ShoppingDao
import de.kalorienapp.data.db.entity.ShoppingItemEntity
import de.kalorienapp.domain.ShoppingList
import de.kalorienapp.domain.model.ShoppingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ShoppingRepository(private val dao: ShoppingDao) {
    val items: Flow<List<ShoppingItem>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Artikel hinzufügen; gleiche offene Artikel werden zusammengerechnet. */
    suspend fun add(items: List<ShoppingItem>) {
        val merge = ShoppingList.merge(dao.all().map { it.toDomain() }, items)
        dao.apply(merge.updated.map(ShoppingItemEntity::fromDomain), merge.inserted.map(ShoppingItemEntity::fromDomain))
    }

    suspend fun setChecked(item: ShoppingItem, checked: Boolean) = dao.setChecked(item.id, checked)

    suspend fun delete(item: ShoppingItem) = dao.delete(item.id)

    suspend fun restore(item: ShoppingItem) = dao.upsert(listOf(ShoppingItemEntity.fromDomain(item)))

    suspend fun deleteChecked() = dao.deleteChecked()

    suspend fun deleteAll() = dao.deleteAll()
}
