package com.dailyrupi.app.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.core.expense.ExpenseDay
import com.dailyrupi.core.expense.groupByDay
import com.dailyrupi.core.model.Expense
import com.dailyrupi.core.model.ExpenseSummary
import com.dailyrupi.core.net.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
    val pendingDelete: Expense? = null,
    /** A one-off message for the snackbar, cleared with [ExpensesViewModel.messageShown]. */
    val message: String? = null,
)

@HiltViewModel
class ExpensesViewModel @Inject constructor(private val repository: ExpenseRepository) : ViewModel() {

    private val _state = MutableStateFlow(ExpensesUiState())
    val state: StateFlow<ExpensesUiState> = _state.asStateFlow()

    private var loaded: List<Expense> = emptyList()
    private var nextPage = 0
    private var total = 0L
    private var loadJob: Job? = null

    init {
        refresh(initial = true)
        viewModelScope.launch { repository.changes.collect { refresh(initial = false, quiet = true) } }
        viewModelScope.launch { repository.failures.collect { message -> _state.update { it.copy(message = message) } } }
        viewModelScope.launch {
            repository.pendingDelete.collect { pending ->
                _state.update { it.copy(pendingDelete = pending) }
                publish()
            }
        }
    }

    /** Pull to refresh, app open and after every change: the summary and the first page again. */
    fun refresh(initial: Boolean = false, quiet: Boolean = false) {
        loadJob?.cancel()
        _state.update { it.copy(loading = initial && loaded.isEmpty(), refreshing = !initial && !quiet, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val summary = async { repository.summary() }
                val first = repository.page(0)
                loaded = first.content
                nextPage = 1
                total = first.totalElements
                _state.update { it.copy(summary = summary.await(), today = LocalDate.now()) }
                publish()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.userMessage()) }
            } finally {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || !current.canLoadMore || loadJob?.isActive == true) return
        _state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch {
            try {
                val page = repository.page(nextPage)
                val seen = loaded.mapTo(HashSet()) { it.id }
                // New expenses shift the pages, so the next page can repeat a few rows.
                loaded = loaded + page.content.filter { it.id !in seen }
                nextPage++
                total = page.totalElements
                if (page.content.isEmpty()) total = loaded.size.toLong()
                publish()
            } catch (e: Exception) {
                _state.update { it.copy(message = e.userMessage()) }
            } finally {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun undoDelete() = repository.undoDelete()

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun publish() {
        val hidden = repository.pendingDelete.value?.id
        _state.update {
            it.copy(
                days = groupByDay(loaded.filter { expense -> expense.id != hidden }),
                canLoadMore = loaded.size < total,
            )
        }
    }
}
