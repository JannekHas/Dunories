package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.kalorienapp.data.db.entity.OwnBarcodeEntity

/** Eigene Barcodes (Produkte, die Open Food Facts nicht kennt). */
@Dao
interface OwnBarcodeDao {
    @Query("SELECT * FROM own_barcode WHERE barcode = :barcode")
    suspend fun find(barcode: String): OwnBarcodeEntity?

    @Upsert
    suspend fun upsert(entity: OwnBarcodeEntity)
}
