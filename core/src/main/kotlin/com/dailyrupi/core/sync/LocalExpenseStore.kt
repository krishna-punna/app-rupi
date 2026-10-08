package com.dailyrupi.core.sync

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Where the phone keeps expenses between app runs. The app uses a Room database. */
interface LocalExpenseStore {
    /** Every expense not deleted on the phone, newest spend first. */
    fun observeVisible(): Flow<List<LocalExpense>>

    /** Every expense with a change the server does not have yet, oldest change first. */
    fun observeUnsynced(): Flow<List<LocalExpense>>

    suspend fun byKey(key: String): LocalExpense?
    suspend fun byServerId(id: Long): LocalExpense?

    /** Changes to send, oldest first: unsynced and not waiting for the user to fix them. */
    suspend fun pending(): List<LocalExpense>

    suspend fun upsert(expense: LocalExpense)
    suspend fun upsertAll(expenses: List<LocalExpense>)
    suspend fun delete(key: String)
    suspend fun clear()
}

private val newestFirst = compareByDescending<LocalExpense> { it.spentAt }.thenByDescending { it.changedAt }

/** For tests, and anywhere a database is not needed. */
class InMemoryLocalExpenseStore : LocalExpenseStore {
    private val rows = ConcurrentHashMap<String, LocalExpense>()
    private val version = MutableStateFlow(0)

    val all: List<LocalExpense> get() = rows.values.toList()

    override fun observeVisible() = version.map {
        rows.values.filter { it.state != SyncState.DELETE }.sortedWith(newestFirst)
    }

    override fun observeUnsynced() = version.map {
        rows.values.filter { it.state != SyncState.SYNCED }.sortedBy { it.changedAt }
    }

    override suspend fun byKey(key: String) = rows[key]

    override suspend fun byServerId(id: Long) = rows.values.firstOrNull { it.serverId == id }

    override suspend fun pending() =
        rows.values.filter { it.state != SyncState.SYNCED && it.error == null }.sortedBy { it.changedAt }

    override suspend fun upsert(expense: LocalExpense) {
        rows[expense.key] = expense
        version.value++
    }

    override suspend fun upsertAll(expenses: List<LocalExpense>) {
        expenses.forEach { rows[it.key] = it }
        version.value++
    }

    override suspend fun delete(key: String) {
        rows.remove(key)
        version.value++
    }

    override suspend fun clear() {
        rows.clear()
        version.value++
    }
}
