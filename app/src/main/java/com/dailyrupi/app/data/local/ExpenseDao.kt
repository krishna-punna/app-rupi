package com.dailyrupi.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses WHERE state != 'DELETE' ORDER BY spentAt DESC, changedAt DESC")
    fun observeVisible(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE state != 'SYNCED' ORDER BY changedAt")
    fun observeUnsynced(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE state != 'SYNCED' AND error IS NULL ORDER BY changedAt")
    suspend fun pending(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE `key` = :key")
    suspend fun byKey(key: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE serverId = :id LIMIT 1")
    suspend fun byServerId(id: Long): ExpenseEntity?

    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    @Upsert
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM expenses")
    suspend fun clear()

    @Query("SELECT json FROM cache WHERE name = :name")
    suspend fun cached(name: String): String?

    @Upsert
    suspend fun putCache(entry: CacheEntry)

    @Query("DELETE FROM cache")
    suspend fun clearCache()
}
