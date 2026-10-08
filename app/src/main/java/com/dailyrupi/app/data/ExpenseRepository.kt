package com.dailyrupi.app.data

import com.dailyrupi.app.di.AppScope
import com.dailyrupi.app.sync.SyncScheduler
import com.dailyrupi.core.expense.pushRecent
import com.dailyrupi.core.masterdata.ItemChoice
import com.dailyrupi.core.model.ExpenseRequest
import com.dailyrupi.core.model.PaymentMethod
import com.dailyrupi.core.net.ApiException
import com.dailyrupi.core.sync.LocalExpense
import com.dailyrupi.core.sync.LocalExpenseStore
import com.dailyrupi.core.sync.SyncState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Expenses are saved on the phone first and sent to the server in the background, so adding,
 * editing and deleting work with no network. Also keeps the delete-with-undo and quick add memory.
 */
@Singleton
class ExpenseRepository @Inject constructor(
    private val store: LocalExpenseStore,
    private val prefs: AppPreferences,
    private val scheduler: SyncScheduler,
    @AppScope private val scope: CoroutineScope,
) {
    /** Epoch milliseconds, replaceable in tests; orders the changes sent to the server. */
    var clock: () -> Long = System::currentTimeMillis

    private val _pendingDelete = MutableStateFlow<LocalExpense?>(null)

    /** The expense waiting out its Undo period; hidden from the list meanwhile. */
    val pendingDelete: StateFlow<LocalExpense?> = _pendingDelete.asStateFlow()

    /** Every expense on the phone, synced or not, newest first, without the one waiting to be deleted. */
    val expenses: Flow<List<LocalExpense>> = combine(store.observeVisible(), _pendingDelete) { list, pending ->
        if (pending == null) list else list.filter { it.key != pending.key }
    }

    /** Changes the server does not have yet, oldest first. */
    val unsynced: Flow<List<LocalExpense>> = store.observeUnsynced()

    private var deleteJob: Job? = null

    suspend fun get(key: String): LocalExpense? = store.byKey(key)

    suspend fun recentItemIds(): List<Long> = prefs.recentItemIds()

    suspend fun lastPaymentMethodId(): Long? = prefs.lastPaymentMethodId()

    /** Creates when [key] is null, otherwise updates. Saved on the phone at once and synced later. */
    suspend fun save(key: String?, request: ExpenseRequest, item: ItemChoice, method: PaymentMethod): LocalExpense {
        val existing = key?.let { store.byKey(it) }
        if (key != null && (existing == null || existing.state == SyncState.DELETE)) {
            throw ApiException(404, "NOT_FOUND", "This expense was deleted")
        }
        val newKey = existing?.key ?: UUID.randomUUID().toString()
        val saved = LocalExpense(
            key = newKey,
            serverId = existing?.serverId,
            clientId = if (existing == null) newKey else existing.clientId,
            amount = request.amount,
            spentAt = request.spentAt,
            note = request.note,
            categoryId = item.categoryId,
            categoryName = item.categoryName,
            subCategoryId = item.subCategoryId,
            subCategoryName = item.subCategoryName,
            itemId = item.itemId,
            itemName = item.itemName,
            paymentMethodId = method.id,
            paymentMethodName = method.name,
            // Not on the server yet (or refused there): still a create.
            state = if (existing?.serverId == null) SyncState.CREATE else SyncState.UPDATE,
            error = null,
            changedAt = clock(),
        )
        store.upsert(saved)
        prefs.setRecentItemIds(pushRecent(prefs.recentItemIds(), request.itemId))
        prefs.setLastPaymentMethodId(request.paymentMethodId)
        scheduler.requestSync()
        return saved
    }

    /** Deletes after [UNDO_MILLIS] unless [undoDelete] is called first. */
    fun scheduleDelete(expense: LocalExpense) {
        _pendingDelete.value?.let { previous ->
            deleteJob?.cancel()
            scope.launch { commitDelete(previous) }
        }
        _pendingDelete.value = expense
        deleteJob = scope.launch {
            delay(UNDO_MILLIS)
            withContext(NonCancellable) { commitDelete(expense) }
        }
    }

    fun undoDelete() {
        deleteJob?.cancel()
        deleteJob = null
        _pendingDelete.value = null
    }

    fun requestSync() = scheduler.requestSync()

    /** Log out or a new server: everything on the phone goes, synced or not. */
    suspend fun clearAll() {
        deleteJob?.cancel()
        _pendingDelete.value = null
        scheduler.cancel()
        store.clear()
    }

    private suspend fun commitDelete(expense: LocalExpense) {
        try {
            val latest = store.byKey(expense.key) ?: return
            if (latest.serverId == null) {
                // Never reached the server: nothing to tell it.
                store.delete(latest.key)
            } else {
                store.upsert(latest.copy(state = SyncState.DELETE, error = null, changedAt = clock()))
                scheduler.requestSync()
            }
        } finally {
            if (_pendingDelete.value?.key == expense.key) _pendingDelete.value = null
        }
    }

    companion object {
        const val UNDO_MILLIS = 5_000L
    }
}
