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

    /** spentAt is stored as yyyy-MM-dd'T'HH:mm:ss, so text order is time order. */
    @Query("SELECT * FROM expenses WHERE state = 'SYNCED' AND spentAt >= :from AND spentAt < :to")
    suspend fun syncedBetween(from: String, to: String): List<ExpenseEntity>

    /** Pass the same value for [keepFrom] and [keepTo] to keep nothing. */
    @Query(
        "DELETE FROM expenses WHERE state = 'SYNCED' AND spentAt < :before " +
            "AND NOT (spentAt >= :keepFrom AND spentAt < :keepTo)",
    )
    suspend fun deleteSyncedBefore(before: String, keepFrom: String, keepTo: String)

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
