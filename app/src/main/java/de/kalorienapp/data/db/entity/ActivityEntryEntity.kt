package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.kalorienapp.domain.model.ActivityEntry
import java.time.LocalDate

@Entity(tableName = "activity_entry", indices = [Index("dateEpochDay")])
data class ActivityEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val name: String,
    val kcal: Int,
    val createdAt: Long,
) {
    fun toDomain() = ActivityEntry(
        id = id,
        date = LocalDate.ofEpochDay(dateEpochDay),
        name = name,
        kcal = kcal,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(a: ActivityEntry) = ActivityEntryEntity(
            id = a.id,
            dateEpochDay = a.date.toEpochDay(),
            name = a.name,
            kcal = a.kcal,
            createdAt = a.createdAt,
        )
    }
}
