package com.dailyrupi.app.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.app.sync.SyncOutcome
import com.dailyrupi.app.sync.SyncRunner
import com.dailyrupi.core.expense.ExpenseDay
import com.dailyrupi.core.expense.groupByDay
import com.dailyrupi.core.model.ExpenseSummary
import com.dailyrupi.core.net.DailyRupiApi
import com.dailyrupi.core.net.apiCall
import com.dailyrupi.core.sync.LocalExpense
import com.dailyrupi.core.sync.OfflineWindow
import com.dailyrupi.core.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpensesUiState(
    /** From the server only; null while it cannot be reached. */
    val summary: ExpenseSummary? = null,
    val days: List<ExpenseDay> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    /** The day picked to view, or null for the last 7 days. */
    val day: LocalDate? = null,
    /** The last call to the server worked, so another day can be picked. */
    val online: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Changes not on the server yet, and how many of them the server refused. */
    val unsynced: Int = 0,
    val needsAttention: Int = 0,
    val pendingDelete: LocalExpense? = null,
    /** A one-off message for the snackbar, cleared with [ExpensesViewModel.messageShown]. */
    val message: String? = null,
)

/**
 * Online, the list is loaded from the server (the last 7 days by sync, a picked day on demand) and
 * the totals come from the server. Offline, the last 7 days kept on the phone are shown, without totals.
 * Changes not synced yet are always shown.
 */
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val sync: SyncRunner,
    private val api: DailyRupiApi,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ExpensesUiState(today = LocalDate.now(clock)))
    val state: StateFlow<ExpensesUiState> = _state.asStateFlow()

    private val selectedDay = MutableStateFlow<LocalDate?>(null)

    init {
        viewModelScope.launch {
            combine(repository.expenses, selectedDay) { expenses, day -> expenses to day }.collect { (expenses, day) ->
                val today = LocalDate.now(clock)
                val shown = expenses.filter { it.isShown(day, today) }
                _state.update { it.copy(days = groupByDay(shown), today = today, loading = false) }
            }
        }
        viewModelScope.launch {
            repository.unsynced.collect { unsynced ->
                _state.update { it.copy(unsynced = unsynced.size, needsAttention = unsynced.count { e -> e.needsAttention }) }
            }
        }
        viewModelScope.launch { repository.pendingDelete.collect { pending -> _state.update { it.copy(pendingDelete = pending) } } }
        // A background sync that worked (after a save, say) means the server's totals have changed.
        viewModelScope.launch {
            var seen = sync.status.value.lastSyncAt
            sync.status.collect { status ->
                if (status.lastSyncAt != seen && status.problem == null && !status.running) {
                    seen = status.lastSyncAt
                    loadSummary()
                }
            }
        }
        load(quiet = true)
    }

    /** Pull to refresh: send this phone's changes and load the list and totals from the server. */
    fun refresh() = load(quiet = false)

    /** Shows [day] from the server, or the last 7 days for null. Picking a day needs the server. */
    fun showDay(day: LocalDate?) {
        val today = LocalDate.now(clock)
        val picked = day?.let { if (it.isAfter(today)) today else it }
        selectedDay.value = picked
        _state.update { it.copy(day = picked, refreshing = picked != null) }
        viewModelScope.launch {
            val problem = sync.showDay(picked)
            _state.update {
                it.copy(refreshing = false, message = problem, online = if (problem != null) false else it.online)
            }
        }
    }

    fun undoDelete() = repository.undoDelete()

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun load(quiet: Boolean) {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = !quiet) }
        viewModelScope.launch {
            val outcome = sync.run()
            val online = outcome == SyncOutcome.DONE
            var problem = if (online) null else sync.status.value.problem?.message
            // The totals are loaded when the status shows the sync worked.
            if (online) selectedDay.value?.let { day -> problem = sync.showDay(day) }
            _state.update {
                it.copy(
                    refreshing = false,
                    online = online && problem == null,
                    summary = if (online) it.summary else null,
                    message = if (quiet) null else problem,
                )
            }
        }
    }

    private suspend fun loadSummary() {
        try {
            val summary = apiCall { api.summary() }
            _state.update { it.copy(summary = summary, online = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(summary = null) }
        }
    }

    private companion object {
        /** A picked day, or the last 7 days plus anything still to sync. */
        fun LocalExpense.isShown(day: LocalDate?, today: LocalDate): Boolean {
            val spent = spentAt.toLocalDate()
            return if (day != null) spent == day else OfflineWindow.contains(spent, today) || state != SyncState.SYNCED
        }
    }
}
