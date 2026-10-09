package com.dailyrupi.app

import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.core.masterdata.ItemChoice
import com.dailyrupi.core.model.ExpenseRequest
import com.dailyrupi.core.model.PaymentMethod
import com.dailyrupi.core.sync.InMemoryLocalExpenseStore
import com.dailyrupi.core.sync.LocalExpense
import com.dailyrupi.core.sync.SyncState
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseRepositoryTest {

    private val store = InMemoryLocalExpenseStore()
    private val scheduler = FakeScheduler()
    private val prefs = FakePreferences()
    private val scope = TestScope(StandardTestDispatcher())
    private val repository = ExpenseRepository(store, prefs, scheduler, scope)
    private val milk = ItemChoice(100, "Milk", 10, "Groceries", 1, "Food")
    private val upi = PaymentMethod(1, "UPI")

    private fun request(amount: String) =
        ExpenseRequest(100, 1, BigDecimal(amount), LocalDateTime.of(2026, 10, 5, 9, 0), "note")

    private suspend fun synced(serverId: Long) = LocalExpense(
        key = LocalExpense.keyForServerId(serverId), serverId = serverId, clientId = null, amount = BigDecimal("10"),
        spentAt = LocalDateTime.of(2026, 10, 5, 9, 0), note = null, categoryId = 1, categoryName = "Food",
        subCategoryId = 10, subCategoryName = "Groceries", itemId = 100, itemName = "Milk",
        paymentMethodId = 1, paymentMethodName = "UPI", state = SyncState.SYNCED,
    ).also { store.upsert(it) }

    @Test
    fun aNewExpenseIsSavedOnThePhoneWithItsOwnIdAndASyncIsRequested() = runTest {
        val saved = repository.save(null, request("42.50"), milk, upi)

        assertEquals(SyncState.CREATE, saved.state)
        assertEquals(saved.key, saved.clientId)
        assertEquals("Milk", saved.itemName)
        assertEquals(saved, store.byKey(saved.key))
        assertEquals(1, scheduler.requests)
        assertEquals(listOf(100L), prefs.recent)
        assertEquals(1L, prefs.lastMethod)
    }

    @Test
    fun editingStaysACreateUntilTheServerHasItThenBecomesAnUpdate() = runTest {
        val created = repository.save(null, request("1"), milk, upi)
        assertEquals(SyncState.CREATE, repository.save(created.key, request("2"), milk, upi).state)

        val server = synced(5)
        val edited = repository.save(server.key, request("3"), milk, upi)
        assertEquals(SyncState.UPDATE, edited.state)
        assertEquals(5L, edited.serverId)
        assertNull(edited.clientId)
    }

    @Test
    fun editingClearsTheServersRefusal() = runTest {
        val refused = synced(5).copy(state = SyncState.UPDATE, error = "This item is inactive")
        store.upsert(refused)
        assertNull(repository.save(refused.key, request("3"), milk, upi).error)
    }

    @Test
    fun deleteIsSavedAfterFiveSecondsAndHiddenMeanwhile() = runTest {
        val expense = synced(1)
        repository.scheduleDelete(expense)
        scope.runCurrent()
        assertTrue(repository.expenses.first().isEmpty())

        scope.advanceTimeBy(ExpenseRepository.UNDO_MILLIS - 1)
        scope.runCurrent()
        assertEquals(SyncState.SYNCED, store.byKey(expense.key)?.state)

        scope.advanceTimeBy(2)
        scope.runCurrent()
        assertEquals(SyncState.DELETE, store.byKey(expense.key)?.state)
        assertNull(repository.pendingDelete.value)
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun deletingAnExpenseTheServerNeverHadRemovesItAtOnce() = runTest {
        val created = repository.save(null, request("1"), milk, upi)
        repository.scheduleDelete(created)
        scope.advanceTimeBy(ExpenseRepository.UNDO_MILLIS + 1)
        scope.runCurrent()
        assertNull(store.byKey(created.key))
    }

    @Test
    fun undoKeepsTheExpense() = runTest {
        val expense = synced(1)
        repository.scheduleDelete(expense)
        scope.advanceTimeBy(1_000)
        repository.undoDelete()
        scope.advanceTimeBy(10_000)
        scope.runCurrent()

        assertEquals(SyncState.SYNCED, store.byKey(expense.key)?.state)
        assertNull(repository.pendingDelete.value)
        assertEquals(1, repository.expenses.first().size)
    }

    @Test
    fun aSecondDeleteSavesTheFirstAtOnce() = runTest {
        val first = synced(1)
        val second = synced(2)
        repository.scheduleDelete(first)
        repository.scheduleDelete(second)
        scope.runCurrent()

        assertEquals(SyncState.DELETE, store.byKey(first.key)?.state)
        assertEquals(2L, repository.pendingDelete.value?.serverId)
    }
}
