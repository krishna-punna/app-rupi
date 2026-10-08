package com.dailyrupi.app

import com.dailyrupi.core.model.ExpenseRequest
import com.dailyrupi.core.sync.InMemoryLocalExpenseStore
import com.dailyrupi.core.sync.LocalExpense
import com.dailyrupi.core.sync.SyncCursor
import com.dailyrupi.core.sync.SyncEngine
import com.dailyrupi.core.sync.SyncState
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {

    private val api = FakeApi()
    private val store = InMemoryLocalExpenseStore()
    private val cursor = object : SyncCursor {
        var value: LocalDateTime? = null
        override suspend fun since() = value
        override suspend fun setSince(value: LocalDateTime?) {
            this.value = value
        }
    }
    private val engine = SyncEngine(api, store, cursor)
    private val at = LocalDateTime.of(2026, 10, 5, 9, 0)

    private fun phoneExpense(key: String, itemId: Long = 100, amount: String = "10", changedAt: Long = 1) = LocalExpense(
        key = key, serverId = null, clientId = key, amount = BigDecimal(amount), spentAt = at, note = null,
        categoryId = 1, categoryName = "Food", subCategoryId = 10, subCategoryName = "Groceries",
        itemId = itemId, itemName = "Milk", paymentMethodId = 1, paymentMethodName = "UPI",
        state = SyncState.CREATE, changedAt = changedAt,
    )

    @Test
    fun createsAreSentOldestFirstWithTheirClientIdAndMarkedSynced() = runTest {
        store.upsert(phoneExpense("b", amount = "2", changedAt = 2))
        store.upsert(phoneExpense("a", amount = "1", changedAt = 1))

        val result = engine.sync()

        assertEquals(2, result.sent)
        assertEquals(listOf("a", "b"), api.created.map { it.clientId })
        val a = store.byKey("a")!!
        assertEquals(SyncState.SYNCED, a.state)
        assertNotNull(a.serverId)
        assertEquals(2, store.all.size)
    }

    @Test
    fun aRetriedCreateIsNotSavedTwice() = runTest {
        // The first attempt reached the server but its answer was lost.
        api.createExpense(ExpenseRequest(100, 1, BigDecimal("10"), at, clientId = "a"))
        store.upsert(phoneExpense("a"))

        engine.sync()

        assertEquals(1, api.expenses.size)
        assertEquals(SyncState.SYNCED, store.byKey("a")?.state)
        assertEquals(1, store.all.size)
    }

    @Test
    fun aRefusedChangeWaitsWithTheServersReasonAndLaterChangesStillGo() = runTest {
        api.inactiveItemId = 101
        store.upsert(phoneExpense("refused", itemId = 101, changedAt = 1))
        store.upsert(phoneExpense("fine", changedAt = 2))

        val result = engine.sync()

        assertEquals(1, result.refused)
        val refused = store.byKey("refused")!!
        assertEquals(SyncState.CREATE, refused.state)
        assertEquals("This item is inactive and cannot be used for new expenses", refused.error)
        assertEquals(SyncState.SYNCED, store.byKey("fine")?.state)

        // Not sent again until the user changes it.
        engine.sync()
        assertEquals(1, api.created.size)
    }

    @Test
    fun withNoServerNothingIsLostAndTheErrorIsThrown() = runTest {
        api.unreachable = true
        store.upsert(phoneExpense("a"))

        val thrown = runCatching { engine.sync() }.exceptionOrNull()

        assertTrue(thrown is IOException)
        assertEquals(SyncState.CREATE, store.byKey("a")?.state)
        assertNull(store.byKey("a")?.error)
    }

    @Test
    fun updatesAndDeletesReachTheServer() = runTest {
        api.addExpense(5, "10", at)
        api.addExpense(6, "20", at)
        engine.sync()
        val five = store.byServerId(5)!!
        val six = store.byServerId(6)!!

        store.upsert(five.copy(amount = BigDecimal("15"), state = SyncState.UPDATE, changedAt = 10))
        store.upsert(six.copy(state = SyncState.DELETE, changedAt = 11))
        engine.sync()

        assertEquals(BigDecimal("15"), api.expenses.single { it.id == 5L }.amount)
        assertEquals(listOf(6L), api.deleted)
        assertNull(store.byServerId(6))
        assertEquals(SyncState.SYNCED, store.byServerId(5)?.state)
    }

    @Test
    fun pullBringsWebChangesAndRemovesWebDeletesButKeepsUnsentPhoneChanges() = runTest {
        api.addExpense(5, "10", at)
        api.addExpense(6, "20", at)
        api.addExpense(7, "30", at)
        engine.sync()
        assertEquals(LocalDateTime.of(2026, 10, 5, 12, 0), cursor.value)

        // Edited on the web; edited on the phone too (not sent yet); deleted on the web.
        api.expenses.replaceAll { if (it.id == 5L || it.id == 6L) it.copy(amount = BigDecimal("99")) else it }
        val six = store.byServerId(6)!!
        api.inactiveItemId = six.itemId
        store.upsert(six.copy(amount = BigDecimal("21"), state = SyncState.UPDATE, changedAt = 20))
        api.deleteExpense(7)
        engine.sync()

        assertEquals(BigDecimal("99"), store.byServerId(5)?.amount)
        assertEquals(BigDecimal("21"), store.byServerId(6)?.amount)
        assertNull(store.byServerId(7))
        assertEquals("2026-10-05T12:00:00", api.sinceAsked.last())
    }

    @Test
    fun anUpdateToAnExpenseDeletedOnTheWebIsDropped() = runTest {
        api.addExpense(5, "10", at)
        engine.sync()
        store.upsert(store.byServerId(5)!!.copy(state = SyncState.UPDATE, changedAt = 5))
        api.expenses.clear()

        engine.sync()

        assertNull(store.byServerId(5))
    }
}
