package com.dailyrupi.app

import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.app.ui.expenses.ExpensesViewModel
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeApi()
    private val repository = ExpenseRepository(api, FakePreferences(), TestScope(UnconfinedTestDispatcher()))

    @Test
    fun groupsByDayAndLoadsMorePages() = runTest {
        val start = LocalDateTime.of(2026, 10, 5, 20, 0)
        (1L..25L).forEach { api.addExpense(it, "10", start.minusHours(it * 3)) }

        val vm = ExpensesViewModel(repository)
        advanceUntilIdle()
        var state = vm.state.value
        assertFalse(state.loading)
        assertEquals(20, state.days.sumOf { it.expenses.size })
        assertTrue(state.canLoadMore)
        assertEquals(BigDecimal("250"), state.summary?.month)

        vm.loadMore()
        advanceUntilIdle()
        state = vm.state.value
        assertEquals(25, state.days.sumOf { it.expenses.size })
        assertFalse(state.canLoadMore)
        assertEquals(state.days.map { it.date }.sortedDescending(), state.days.map { it.date })
    }

    @Test
    fun pendingDeleteIsHiddenAndListRefreshesAfterAdd() = runTest {
        api.addExpense(1, "10", LocalDateTime.of(2026, 10, 5, 9, 0))
        api.addExpense(2, "20", LocalDateTime.of(2026, 10, 5, 10, 0))
        val vm = ExpensesViewModel(repository)
        advanceUntilIdle()

        repository.scheduleDelete(repository.cached(2)!!)
        advanceUntilIdle()
        assertEquals(listOf(1L), vm.state.value.days.flatMap { it.expenses }.map { it.id })
        assertEquals(2L, vm.state.value.pendingDelete?.id)

        repository.undoDelete()
        advanceUntilIdle()
        assertEquals(listOf(2L, 1L), vm.state.value.days.flatMap { it.expenses }.map { it.id })

        repository.save(null, com.dailyrupi.core.model.ExpenseRequest(200, 1, BigDecimal("5"), LocalDateTime.of(2026, 10, 5, 11, 0)))
        advanceUntilIdle()
        assertEquals(3, vm.state.value.days.sumOf { it.expenses.size })
    }
}
