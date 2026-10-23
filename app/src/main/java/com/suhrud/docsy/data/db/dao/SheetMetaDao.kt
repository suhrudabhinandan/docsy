package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.SheetMetaEntity

@Dao
interface SheetMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSheetMeta(meta: SheetMetaEntity): Long

    @Query("SELECT * FROM sheet_meta WHERE doc_id = :docId")
    suspend fun getSheetMetaForDocument(docId: Long): List<SheetMetaEntity>

    @Query("DELETE FROM sheet_meta WHERE doc_id = :docId")
    suspend fun deleteSheetMetaForDocument(docId: Long)
}
