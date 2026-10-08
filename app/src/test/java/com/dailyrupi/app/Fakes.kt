package com.dailyrupi.app

import com.dailyrupi.app.data.AppPreferences
import com.dailyrupi.app.data.ReferenceCache
import com.dailyrupi.app.sync.SyncScheduler
import com.dailyrupi.core.model.CategoryNode
import com.dailyrupi.core.model.ChangePasswordRequest
import com.dailyrupi.core.model.CurrentUser
import com.dailyrupi.core.model.DeletedExpense
import com.dailyrupi.core.model.Expense
import com.dailyrupi.core.model.ExpenseChanges
import com.dailyrupi.core.model.ExpensePage
import com.dailyrupi.core.model.ExpenseRequest
import com.dailyrupi.core.model.ExpenseSummary
import com.dailyrupi.core.model.ItemNode
import com.dailyrupi.core.model.MonthBudget
import com.dailyrupi.core.model.PaymentMethod
import com.dailyrupi.core.model.SubCategoryNode
import com.dailyrupi.core.net.ApiException
import com.dailyrupi.core.net.DailyRupiApi
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class FakePreferences : AppPreferences {
    var server: String? = null
    var user: String? = null
    var recent: List<Long> = emptyList()
    var lastMethod: Long? = null
    var since: String? = null
    var lastSync: Long? = null

    override suspend fun serverUrl() = server
    override suspend fun setServerUrl(url: String) { server = url }
    override suspend fun username() = user
    override suspend fun setUsername(username: String) { user = username }
    override suspend fun recentItemIds() = recent
    override suspend fun setRecentItemIds(ids: List<Long>) { recent = ids }
    override suspend fun lastPaymentMethodId() = lastMethod
    override suspend fun setLastPaymentMethodId(id: Long) { lastMethod = id }
    override suspend fun syncSince() = since
    override suspend fun setSyncSince(value: String?) { since = value }
    override suspend fun lastSyncAt() = lastSync
    override suspend fun setLastSyncAt(value: Long) { lastSync = value }
    override suspend fun clearUserData() {
        user = null
        recent = emptyList()
        lastMethod = null
        since = null
        lastSync = null
    }
}

class FakeScheduler : SyncScheduler {
    var requests = 0
    var cancelled = 0
    override fun requestSync() { requests++ }
    override fun cancel() { cancelled++ }
}

class FakeReferenceCache : ReferenceCache {
    val saved = mutableMapOf<String, String>()
    override suspend fun get(name: String) = saved[name]
    override suspend fun put(name: String, json: String) { saved[name] = json }
}

/** An in-memory backend with the same rules the tests rely on. */
class FakeApi : DailyRupiApi {
    var user = CurrentUser("krishna", listOf("USER"), passwordChangeRequired = false)
    var loginFails = false
    val expenses = mutableListOf<Expense>()
    val created = mutableListOf<ExpenseRequest>()
    val deleted = mutableListOf<Long>()
    val deletedClientIds = mutableSetOf<String?>()
    var loggedOut = false
    /** Thrown by every call, to act as a server that cannot be reached. */
    var unreachable = false
    /** Refuses creates and updates for this item, as the server does for an inactive one. */
    var inactiveItemId: Long? = null
    private var nextId = 1000L

    val tree = listOf(
        CategoryNode(
            1, "Food", subCategories = listOf(
                SubCategoryNode(10, "Groceries", items = listOf(ItemNode(100, "Milk"), ItemNode(101, "Rice"))),
            ),
        ),
        CategoryNode(2, "Transport", subCategories = listOf(SubCategoryNode(20, "Fuel", items = listOf(ItemNode(200, "Petrol"))))),
    )
    val methods = listOf(PaymentMethod(1, "UPI"), PaymentMethod(2, "Cash"))

    override suspend fun csrf() = Unit

    override suspend fun login(username: String, password: String): CurrentUser {
        reachable()
        if (loginFails) {
            throw ApiException(401, "LOGIN_FAILED", "Invalid username or password, or the account is temporarily locked")
        }
        return user
    }

    override suspend fun logout() {
        loggedOut = true
    }

    override suspend fun me() = user

    override suspend fun changePassword(body: ChangePasswordRequest) = Unit

    override suspend fun paymentMethods(): List<PaymentMethod> {
        reachable()
        return methods
    }

    override suspend fun expenses(page: Int, size: Int, from: String?, to: String?): ExpensePage {
        reachable()
        daysAsked += from to to
        val matching = expenses.filter { e ->
            val day = e.spentAt.toLocalDate()
            (from == null || !day.isBefore(LocalDate.parse(from))) && (to == null || !day.isAfter(LocalDate.parse(to)))
        }
        val sorted = matching.sortedWith(compareByDescending<Expense> { it.spentAt }.thenByDescending { it.id })
        return ExpensePage(sorted.drop(page * size).take(size), page, size, matching.size.toLong())
    }

    val daysAsked = mutableListOf<Pair<String?, String?>>()

    override suspend fun summary() = ExpenseSummary(
        LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1),
        expenses.sumOf { it.amount }, expenses.sumOf { it.amount }, expenses.sumOf { it.amount },
    )

    override suspend fun createExpense(body: ExpenseRequest): Expense {
        reachable()
        refuseInactive(body)
        body.clientId?.let { id -> expenses.firstOrNull { it.clientId == id }?.let { return it } }
        if (body.clientId != null && body.clientId in deletedClientIds) throw ApiException(410, "DELETED", "This expense was deleted")
        created += body
        return toExpense(nextId++, body).also { expenses += it }
    }

    override suspend fun updateExpense(id: Long, body: ExpenseRequest): Expense {
        reachable()
        refuseInactive(body)
        val old = expenses.firstOrNull { it.id == id } ?: throw ApiException(404, "NOT_FOUND", "Expense not found")
        val updated = toExpense(id, body).copy(clientId = old.clientId)
        expenses.replaceAll { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun deleteExpense(id: Long) {
        reachable()
        val old = expenses.firstOrNull { it.id == id } ?: throw ApiException(404, "NOT_FOUND", "Expense not found")
        deleted += id
        deletedClientIds += old.clientId
        expenses.removeAll { it.id == id }
    }

    /** Everything each time, which the app must handle as well as an incremental answer. */
    override suspend fun expenseChanges(since: String?, from: String?): ExpenseChanges {
        reachable()
        sinceAsked += since
        fromAsked += from
        val sent = if (since == null && from != null) {
            expenses.filter { !it.spentAt.toLocalDate().isBefore(LocalDate.parse(from)) }
        } else {
            expenses.toList()
        }
        return ExpenseChanges(LocalDateTime.of(2026, 10, 5, 12, 0), sent, deleted.map { DeletedExpense(it) })
    }

    val fromAsked = mutableListOf<String?>()

    val sinceAsked = mutableListOf<String?>()

    private fun reachable() {
        if (unreachable) throw java.io.IOException("unreachable")
    }

    private fun refuseInactive(body: ExpenseRequest) {
        if (body.itemId == inactiveItemId) {
            throw ApiException(400, "INACTIVE_ITEM", "This item is inactive and cannot be used for new expenses")
        }
    }

    override suspend fun budget(month: String) =
        MonthBudget(month, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, emptyList())

    override suspend fun masterData(includeInactive: Boolean): List<CategoryNode> {
        reachable()
        return tree
    }

    fun addExpense(id: Long, amount: String, spentAt: LocalDateTime) {
        expenses += toExpense(id, ExpenseRequest(100, 1, BigDecimal(amount), spentAt, null))
    }

    private fun toExpense(id: Long, body: ExpenseRequest): Expense {
        val category = tree.first { c -> c.subCategories.any { s -> s.items.any { it.id == body.itemId } } }
        val sub = category.subCategories.first { s -> s.items.any { it.id == body.itemId } }
        val item = sub.items.first { it.id == body.itemId }
        return Expense(
            id, body.amount, body.spentAt, body.note, category.id, category.name, sub.id, sub.name,
            item.id, item.name, body.paymentMethodId, methods.first { it.id == body.paymentMethodId }.name,
            clientId = body.clientId,
        )
    }
}
