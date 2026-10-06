package com.dailyrupi.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyrupi.core.model.MonthBudget
import com.dailyrupi.core.net.DailyRupiApi
import com.dailyrupi.core.net.apiCall
import com.dailyrupi.core.net.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BudgetsUiState(
    val month: YearMonth,
    val data: MonthBudget? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val api: DailyRupiApi,
    clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(BudgetsUiState(month = YearMonth.now(clock)))
    val state: StateFlow<BudgetsUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun previousMonth() = show(_state.value.month.minusMonths(1))

    fun nextMonth() = show(_state.value.month.plusMonths(1))

    fun load() {
        val month = _state.value.month
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val data = apiCall { api.budget(month.toString()) }
                _state.update { it.copy(data = data, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    private fun show(month: YearMonth) {
        _state.update { it.copy(month = month, data = null) }
        load()
    }
}
