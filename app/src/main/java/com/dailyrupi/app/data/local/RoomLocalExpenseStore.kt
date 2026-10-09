package com.dailyrupi.app.data.local

import com.dailyrupi.core.sync.LocalExpense
import com.dailyrupi.core.sync.LocalExpenseStore
import java.time.LocalDateTime
import kotlinx.coroutines.flow.map

class RoomLocalExpenseStore(private val dao: ExpenseDao) : LocalExpenseStore {

    override fun observeVisible() = dao.observeVisible().map { rows -> rows.map(ExpenseEntity::toModel) }

    override fun observeUnsynced() = dao.observeUnsynced().map { rows -> rows.map(ExpenseEntity::toModel) }

    override suspend fun byKey(key: String) = dao.byKey(key)?.toModel()

    override suspend fun byServerId(id: Long) = dao.byServerId(id)?.toModel()

    override suspend fun pending() = dao.pending().map(ExpenseEntity::toModel)

    override suspend fun syncedBetween(from: LocalDateTime, to: LocalDateTime) =
        dao.syncedBetween(ExpenseEntity.format(from), ExpenseEntity.format(to)).map(ExpenseEntity::toModel)

    override suspend fun deleteSyncedBefore(before: LocalDateTime, keepFrom: LocalDateTime?, keepTo: LocalDateTime?) {
        val from = keepFrom?.let(ExpenseEntity::format) ?: ""
        val to = keepTo?.let(ExpenseEntity::format) ?: ""
        dao.deleteSyncedBefore(ExpenseEntity.format(before), from, to)
    }

    override suspend fun upsert(expense: LocalExpense) = dao.upsert(ExpenseEntity.from(expense))

    override suspend fun upsertAll(expenses: List<LocalExpense>) = dao.upsertAll(expenses.map(ExpenseEntity::from))

    override suspend fun delete(key: String) = dao.delete(key)

    override suspend fun clear() {
        dao.clear()
        dao.clearCache()
    }
}

class RoomReferenceCache(private val dao: ExpenseDao) : com.dailyrupi.app.data.ReferenceCache {
    override suspend fun get(name: String) = dao.cached(name)
    override suspend fun put(name: String, json: String) = dao.putCache(CacheEntry(name, json))
}
