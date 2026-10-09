package com.dailyrupi.app

import androidx.lifecycle.SavedStateHandle
import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.app.data.ReferenceDataRepository
import com.dailyrupi.app.ui.expenses.ExpenseEditViewModel
import com.dailyrupi.core.sync.InMemoryLocalExpenseStore
import com.dailyrupi.core.sync.LocalExpense
import com.dailyrupi.core.sync.SyncState
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
    private val store = InMemoryLocalExpenseStore()
    private val repository = ExpenseRepository(store, prefs, FakeScheduler(), TestScope(UnconfinedTestDispatcher()))
    private val now = LocalDateTime.of(2026, 10, 5, 18, 30)
    private val clock = Clock.fixed(now.toInstant(ZoneOffset.ofHoursMinutes(5, 30)), ZoneId.of("Asia/Kolkata"))

    private fun viewModel(key: String? = null) = ExpenseEditViewModel(
        SavedStateHandle(if (key == null) emptyMap() else mapOf("key" to key)),
        repository, ReferenceDataRepository(api, FakeReferenceCache()), clock,
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
        assertTrue(store.all.isEmpty())
    }

    @Test
    fun saveKeepsTheExpenseOnThePhoneAndRemembersItForQuickAdd() {
        val vm = viewModel()
        vm.selectItem(vm.state.value.items.first { it.itemId == 101L })
        vm.onAmountChange("1,250.5")
        vm.selectPaymentMethod(2)
        vm.onNoteChange("  monthly stock  ")

        vm.save()

        assertTrue(vm.state.value.done)
        val saved = store.all.single()
        assertEquals(SyncState.CREATE, saved.state)
        assertEquals(101L, saved.itemId)
        assertEquals("Rice", saved.itemName)
        assertEquals(2L, saved.paymentMethodId)
        assertEquals("Cash", saved.paymentMethodName)
        assertEquals(BigDecimal("1250.5"), saved.amount)
        assertEquals(now, saved.spentAt)
        assertEquals("monthly stock", saved.note)
        assertEquals(listOf(101L), prefs.recent)
        assertEquals(2L, prefs.lastMethod)
    }

    @Test
    fun editLoadsTheExpenseFromThePhoneAndDeleteWaitsForUndo() = runTest {
        api.addExpense(7, "500.00", now.minusDays(1))
        val expense = LocalExpense.fromServer(api.expenses.single())
        store.upsert(expense.copy(error = "This item is inactive"))

        val vm = viewModel(expense.key)
        val state = vm.state.value
        assertTrue(state.isEdit)
        assertEquals("500", state.amount)
        assertEquals(100L, state.item?.itemId)
        assertEquals("This item is inactive", state.syncError)

        vm.delete()

        assertTrue(vm.state.value.done)
        assertEquals(expense.key, repository.pendingDelete.value?.key)
        assertEquals(SyncState.SYNCED, store.byKey(expense.key)?.state)
    }

    @Test
    fun addExpenseWorksOfflineFromTheSavedItemList() {
        viewModel()
        api.unreachable = true
        val offline = ExpenseEditViewModel(
            SavedStateHandle(), repository, ReferenceDataRepository(api, FakeReferenceCache()), clock,
        )
        assertEquals("Could not reach the server. Check your connection and the server address.", offline.state.value.loadError)

        val cache = FakeReferenceCache()
        api.unreachable = false
        ExpenseEditViewModel(SavedStateHandle(), repository, ReferenceDataRepository(api, cache), clock)
        api.unreachable = true
        val fromCache = ExpenseEditViewModel(SavedStateHandle(), repository, ReferenceDataRepository(api, cache), clock)
        assertNull(fromCache.state.value.loadError)
        assertEquals(3, fromCache.state.value.items.size)
        assertEquals(2, fromCache.state.value.paymentMethods.size)
    }
}
