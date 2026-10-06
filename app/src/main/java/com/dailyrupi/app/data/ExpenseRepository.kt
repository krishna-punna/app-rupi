package com.dailyrupi.app.data

import com.dailyrupi.app.di.AppScope
import com.dailyrupi.core.expense.pushRecent
import com.dailyrupi.core.model.Expense
import com.dailyrupi.core.model.ExpensePage
import com.dailyrupi.core.model.ExpenseRequest
import com.dailyrupi.core.model.ExpenseSummary
import com.dailyrupi.core.net.DailyRupiApi
import com.dailyrupi.core.net.apiCall
import com.dailyrupi.core.net.userMessage
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Expenses on the server, plus the delete-with-undo and quick add memory. */
@Singleton
class ExpenseRepository @Inject constructor(
    private val api: DailyRupiApi,
    private val prefs: AppPreferences,
    @AppScope private val scope: CoroutineScope,
) {
    // There is no "get one expense" endpoint, so edit works from what the list loaded.
    private val known = ConcurrentHashMap<Long, Expense>()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** Emits after every add, edit or delete, so the list and summary refresh. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val _pendingDelete = MutableStateFlow<Expense?>(null)

    /** The expense waiting out its Undo period; hidden from the list meanwhile. */
    val pendingDelete: StateFlow<Expense?> = _pendingDelete.asStateFlow()

    private val _failures = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val failures: SharedFlow<String> = _failures.asSharedFlow()

    private var deleteJob: Job? = null

    suspend fun page(page: Int, size: Int = PAGE_SIZE): ExpensePage =
        apiCall { api.expenses(page, size) }.also { result -> result.content.forEach { known[it.id] = it } }

    suspend fun summary(): ExpenseSummary = apiCall { api.summary() }

    fun cached(id: Long): Expense? = known[id]

    suspend fun recentItemIds(): List<Long> = prefs.recentItemIds()

    suspend fun lastPaymentMethodId(): Long? = prefs.lastPaymentMethodId()

    /** Creates when [id] is null, otherwise updates. */
    suspend fun save(id: Long?, request: ExpenseRequest): Expense {
        val saved = apiCall { if (id == null) api.createExpense(request) else api.updateExpense(id, request) }
        known[saved.id] = saved
        prefs.setRecentItemIds(pushRecent(prefs.recentItemIds(), request.itemId))
        prefs.setLastPaymentMethodId(request.paymentMethodId)
        _changes.tryEmit(Unit)
        return saved
    }

    /** Deletes after [UNDO_MILLIS] unless [undoDelete] is called first. */
    fun scheduleDelete(expense: Expense) {
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

    fun clear() {
        known.clear()
        deleteJob?.cancel()
        _pendingDelete.value = null
    }

    private suspend fun commitDelete(expense: Expense) {
        try {
            apiCall { api.deleteExpense(expense.id) }
            known.remove(expense.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _failures.tryEmit("Could not delete the expense: ${e.userMessage()}")
        } finally {
            if (_pendingDelete.value?.id == expense.id) _pendingDelete.value = null
            _changes.tryEmit(Unit)
        }
    }

    companion object {
        const val PAGE_SIZE = 20
        const val UNDO_MILLIS = 5_000L
    }
}
