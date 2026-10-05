package com.dailyrupi.app

import androidx.lifecycle.SavedStateHandle
import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.app.data.ReferenceDataRepository
import com.dailyrupi.app.ui.expenses.ExpenseEditViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpenseEditViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeApi()
    private val prefs = FakePreferences()
    private val repository = ExpenseRepository(api, prefs, TestScope(UnconfinedTestDispatcher()))
    private val now = LocalDateTime.of(2026, 10, 5, 18, 30)
    private val clock = Clock.fixed(now.toInstant(ZoneOffset.ofHoursMinutes(5, 30)), ZoneId.of("Asia/Kolkata"))

    private fun viewModel(id: Long? = null) = ExpenseEditViewModel(
        SavedStateHandle(if (id == null) emptyMap() else mapOf("id" to id)),
        repository, ReferenceDataRepository(api), clock,
    )

    @Test
    fun newExpenseStartsNowWithTheLastPaymentMethodAndRecentItems() {
        prefs.lastMethod = 2
        prefs.recent = listOf(200L, 999L, 100L)

        val state = viewModel().state.value

        assertFalse(state.loading)
        assertEquals(now, state.spentAt)
        assertEquals(2L, state.paymentMethodId)
        // Items no longer in the tree are dropped from the quick add chips.
        assertEquals(listOf(200L, 100L), state.recent.map { it.itemId })
        assertNull(state.item)
    }

    @Test
    fun saveShowsEveryProblemNextToItsField() {
        val vm = viewModel()
        vm.onAmountChange("12.345")
        vm.onDateChange(LocalDate.of(2026, 10, 6))
        vm.onNoteChange("x".repeat(256))

        vm.save()

        val state = vm.state.value
        assertEquals("Pick an item", state.itemError)
        assertEquals("Use numbers with up to 2 decimals", state.amountError)
        assertEquals("Date and time cannot be in the future", state.dateError)
        assertEquals("Note can be at most 255 characters", state.noteError)
        assertTrue(api.created.isEmpty())
    }

    @Test
    fun saveCreatesTheExpenseAndRemembersItForQuickAdd() {
        val vm = viewModel()
        vm.selectItem(vm.state.value.items.first { it.itemId == 101L })
        vm.onAmountChange("1,250.5")
        vm.selectPaymentMethod(2)
        vm.onNoteChange("  monthly stock  ")

        vm.save()

        assertTrue(vm.state.value.done)
        val request = api.created.single()
        assertEquals(101L, request.itemId)
        assertEquals(2L, request.paymentMethodId)
        assertEquals(BigDecimal("1250.5"), request.amount)
        assertEquals(now, request.spentAt)
        assertEquals("monthly stock", request.note)
        assertEquals(listOf(101L), prefs.recent)
        assertEquals(2L, prefs.lastMethod)
    }

    @Test
    fun editLoadsTheExpenseFromTheListAndDeleteWaitsForUndo() = runTest {
        api.addExpense(7, "500.00", now.minusDays(1))
        repository.page(0)

        val vm = viewModel(7)
        val state = vm.state.value
        assertTrue(state.isEdit)
        assertEquals("500", state.amount)
        assertEquals(100L, state.item?.itemId)

        vm.delete()

        assertTrue(vm.state.value.done)
        assertEquals(7L, repository.pendingDelete.value?.id)
        assertTrue(api.deleted.isEmpty())
    }
}
