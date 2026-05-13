package com.aicompanion.core.database.dao

import androidx.room.*
import com.aicompanion.core.database.entity.Live2DModelInfoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface Live2DModelInfoDao {
    @Query("SELECT * FROM live2d_models ORDER BY isBuiltIn DESC, importedAt DESC")
    fun getAll(): Flow<List<Live2DModelInfoEntity>>

    @Query("SELECT * FROM live2d_models WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): Live2DModelInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: Live2DModelInfoEntity)

    @Query("DELETE FROM live2d_models WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE live2d_models SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE live2d_models SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)
}
