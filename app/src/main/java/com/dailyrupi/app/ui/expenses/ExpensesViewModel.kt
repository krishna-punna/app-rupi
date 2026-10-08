package com.dailyrupi.app.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.app.sync.SyncOutcome
import com.dailyrupi.app.sync.SyncRunner
import com.dailyrupi.core.expense.ExpenseDay
import com.dailyrupi.core.expense.groupByDay
import com.dailyrupi.core.expense.summarize
import com.dailyrupi.core.model.ExpenseSummary
import com.dailyrupi.core.sync.LocalExpense
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpensesUiState(
    val summary: ExpenseSummary? = null,
    val days: List<ExpenseDay> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Changes not on the server yet, and how many of them the server refused. */
    val unsynced: Int = 0,
    val needsAttention: Int = 0,
    val pendingDelete: LocalExpense? = null,
    /** A one-off message for the snackbar, cleared with [ExpensesViewModel.messageShown]. */
    val message: String? = null,
)

/** The list and summary come from the phone, so they show unsynced expenses and work offline. */
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val sync: SyncRunner,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ExpensesUiState(today = LocalDate.now(clock)))
    val state: StateFlow<ExpensesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.expenses.collect { expenses ->
                val today = LocalDate.now(clock)
                _state.update {
                    it.copy(days = groupByDay(expenses), summary = summarize(expenses, today), today = today, loading = false)
                }
            }
        }
        viewModelScope.launch {
            repository.unsynced.collect { unsynced ->
                _state.update { it.copy(unsynced = unsynced.size, needsAttention = unsynced.count { e -> e.needsAttention }) }
            }
        }
        viewModelScope.launch { repository.pendingDelete.collect { pending -> _state.update { it.copy(pendingDelete = pending) } } }
    }

    /** Pull to refresh: send this phone's changes and fetch the server's. */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val outcome = sync.run()
            val problem = sync.status.value.problem
            _state.update {
                it.copy(refreshing = false, message = if (outcome == SyncOutcome.DONE) null else problem?.message)
            }
        }
    }

    fun undoDelete() = repository.undoDelete()

    fun messageShown() = _state.update { it.copy(message = null) }
}
