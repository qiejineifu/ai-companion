package com.aicompanion.core.database.dao

import androidx.room.*
import com.aicompanion.core.database.entity.ApiProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiProviderDao {
    @Query("SELECT * FROM api_providers ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ApiProviderEntity>>

    @Query("SELECT * FROM api_providers WHERE id = :id")
    suspend fun getById(id: String): ApiProviderEntity?

    @Query("SELECT * FROM api_providers WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): ApiProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: ApiProviderEntity)

    @Update
    suspend fun update(provider: ApiProviderEntity)

    @Query("DELETE FROM api_providers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE api_providers SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE api_providers SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)
}
