package com.dailyrupi.core.sync

import com.dailyrupi.core.model.Expense
import com.dailyrupi.core.model.ExpenseRequest
import java.math.BigDecimal
import java.time.LocalDateTime

/** What still has to reach the server for an expense kept on the phone. */
enum class SyncState {
    /** Same as the server. */
    SYNCED,

    /** Made on the phone; the server has not saved it yet. */
    CREATE,

    /** Changed on the phone since it was last saved on the server. */
    UPDATE,

    /** Deleted on the phone; hidden, and removed for good once the server has deleted it too. */
    DELETE,
}

/**
 * An expense as the phone keeps it, synced or not. [key] never changes: the client id for
 * an expense made on the phone, or `server-<id>` for one made on the web.
 */
data class LocalExpense(
    val key: String,
    val serverId: Long?,
    val clientId: String?,
    val amount: BigDecimal,
    val spentAt: LocalDateTime,
    val note: String?,
    val categoryId: Long,
    val categoryName: String,
    val subCategoryId: Long,
    val subCategoryName: String,
    val itemId: Long,
    val itemName: String,
    val paymentMethodId: Long,
    val paymentMethodName: String,
    val state: SyncState,
    /** Why the server refused the last attempt; the change waits for the user to fix or delete it. */
    val error: String? = null,
    /** When it last changed on the phone, in epoch milliseconds; changes are sent oldest first. */
    val changedAt: Long = 0,
) {
    val needsAttention: Boolean get() = error != null

    fun toRequest(): ExpenseRequest = ExpenseRequest(
        itemId = itemId,
        paymentMethodId = paymentMethodId,
        amount = amount,
        spentAt = spentAt,
        note = note,
        clientId = clientId.takeIf { state == SyncState.CREATE },
    )

    companion object {
        fun keyForServerId(id: Long) = "server-$id"

        /** The server's copy, keeping [key] when the phone already has the expense under another one. */
        fun fromServer(expense: Expense, key: String? = null) = LocalExpense(
            key = key ?: expense.clientId ?: keyForServerId(expense.id),
            serverId = expense.id,
            clientId = expense.clientId,
            amount = expense.amount,
            spentAt = expense.spentAt,
            note = expense.note,
            categoryId = expense.categoryId,
            categoryName = expense.categoryName,
            subCategoryId = expense.subCategoryId,
            subCategoryName = expense.subCategoryName,
            itemId = expense.itemId,
            itemName = expense.itemName,
            paymentMethodId = expense.paymentMethodId,
            paymentMethodName = expense.paymentMethodName,
            state = SyncState.SYNCED,
        )
    }
}
