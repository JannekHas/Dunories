package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.kalorienapp.domain.model.ShoppingItem

/** Artikel der Einkaufsliste; [sources] = Rezeptnamen, getrennt durch [SEPARATOR]. */
@Entity(tableName = "shopping_item")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Double?,
    val unit: String,
    val checked: Boolean,
    val sources: String,
    val createdAt: Long,
) {
    fun toDomain() = ShoppingItem(
        id = id,
        name = name,
        quantity = quantity,
        unit = unit,
        checked = checked,
        sources = sources.split(SEPARATOR).filter { it.isNotBlank() },
        createdAt = createdAt,
    )

    companion object {
        private const val SEPARATOR = "\u001F"

        fun fromDomain(i: ShoppingItem) = ShoppingItemEntity(
            id = i.id,
            name = i.name,
            quantity = i.quantity,
            unit = i.unit,
            checked = i.checked,
            sources = i.sources.joinToString(SEPARATOR),
            createdAt = i.createdAt,
        )
    }
}
