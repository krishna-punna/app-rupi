package com.dailyrupi.app

import com.dailyrupi.app.data.ExpenseRepository
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private val api = FakeApi()
    private val scope = TestScope(StandardTestDispatcher())
    private val repository = ExpenseRepository(api, FakePreferences(), scope)

    private suspend fun loaded(id: Long) = run {
        api.addExpense(id, "10", LocalDateTime.of(2026, 10, 5, 9, 0))
        repository.page(0)
        repository.cached(id)!!
    }

    @Test
    fun deleteReachesTheServerAfterFiveSeconds() = runTest {
        val expense = loaded(1)
        repository.scheduleDelete(expense)

        scope.advanceTimeBy(ExpenseRepository.UNDO_MILLIS - 1)
        scope.runCurrent()
        assertTrue(api.deleted.isEmpty())
        assertEquals(1L, repository.pendingDelete.value?.id)

        scope.advanceTimeBy(2)
        scope.runCurrent()
        assertEquals(listOf(1L), api.deleted)
        assertNull(repository.pendingDelete.value)
        assertNull(repository.cached(1))
    }

    @Test
    fun undoKeepsTheExpense() = runTest {
        val expense = loaded(1)
        repository.scheduleDelete(expense)
        scope.advanceTimeBy(1_000)
        repository.undoDelete()
        scope.advanceTimeBy(10_000)
        scope.runCurrent()

        assertTrue(api.deleted.isEmpty())
        assertNull(repository.pendingDelete.value)
    }

    @Test
    fun aSecondDeleteSendsTheFirstAtOnce() = runTest {
        val first = loaded(1)
        val second = loaded(2)
        repository.scheduleDelete(first)
        repository.scheduleDelete(second)
        scope.runCurrent()

        assertEquals(listOf(1L), api.deleted)
        assertEquals(2L, repository.pendingDelete.value?.id)
    }
}
