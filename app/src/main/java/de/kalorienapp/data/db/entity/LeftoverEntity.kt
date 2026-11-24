package de.kalorienapp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.kalorienapp.data.db.DishComponentCodec
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.Leftover
import de.kalorienapp.domain.model.MealType
import java.time.LocalDate

/** Offener Rest (DB v23, siehe [Leftover]); Zutaten als JSON wie bei Gerichten. */
@Entity(tableName = "leftover")
data class LeftoverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val components: String,
    val createdEpochDay: Long,
    val mealType: MealType,
    val source: EntrySource,
    val portionAmount: Double?,
    @ColumnInfo(defaultValue = "0") val frozen: Boolean,
    val dismissedEpochDay: Long?,
    val checkedEpochDay: Long?,
    val createdAt: Long,
) {
    fun toDomain() = Leftover(
        id = id,
        name = name,
        components = DishComponentCodec.decode(components),
        createdDay = LocalDate.ofEpochDay(createdEpochDay),
        mealType = mealType,
        source = source,
        portionAmount = portionAmount,
        frozen = frozen,
        dismissedDay = dismissedEpochDay?.let(LocalDate::ofEpochDay),
        checkedDay = checkedEpochDay?.let(LocalDate::ofEpochDay),
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(l: Leftover) = LeftoverEntity(
            id = l.id,
            name = l.name,
            components = DishComponentCodec.encode(l.components).orEmpty(),
            createdEpochDay = l.createdDay.toEpochDay(),
            mealType = l.mealType,
            source = l.source,
            portionAmount = l.portionAmount,
            frozen = l.frozen,
            dismissedEpochDay = l.dismissedDay?.toEpochDay(),
            checkedEpochDay = l.checkedDay?.toEpochDay(),
            createdAt = l.createdAt,
        )
    }
}
