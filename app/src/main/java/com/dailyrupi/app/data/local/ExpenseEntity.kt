package com.dailyrupi.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dailyrupi.core.sync.LocalExpense
import com.dailyrupi.core.sync.SyncState
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** One expense on the phone. Amounts and dates are kept as text so nothing is rounded. */
@Entity(tableName = "expenses", indices = [Index("serverId"), Index("spentAt")])
data class ExpenseEntity(
    @PrimaryKey val key: String,
    val serverId: Long?,
    val clientId: String?,
    val amount: String,
    val spentAt: String,
    val note: String?,
    val categoryId: Long,
    val categoryName: String,
    val subCategoryId: Long,
    val subCategoryName: String,
    val itemId: Long,
    val itemName: String,
    val paymentMethodId: Long,
    val paymentMethodName: String,
    val state: String,
    val error: String?,
    val changedAt: Long,
) {
    fun toModel() = LocalExpense(
        key = key,
        serverId = serverId,
        clientId = clientId,
        amount = BigDecimal(amount),
        spentAt = LocalDateTime.parse(spentAt),
        note = note,
        categoryId = categoryId,
        categoryName = categoryName,
        subCategoryId = subCategoryId,
        subCategoryName = subCategoryName,
        itemId = itemId,
        itemName = itemName,
        paymentMethodId = paymentMethodId,
        paymentMethodName = paymentMethodName,
        state = SyncState.valueOf(state),
        error = error,
        changedAt = changedAt,
    )

    companion object {
        /** Always the same length, so text order is time order, which the list query relies on. */
        private val SPENT_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        fun format(time: LocalDateTime): String = time.truncatedTo(ChronoUnit.SECONDS).format(SPENT_AT)

        fun from(e: LocalExpense) = ExpenseEntity(
            key = e.key,
            serverId = e.serverId,
            clientId = e.clientId,
            amount = e.amount.toPlainString(),
            spentAt = format(e.spentAt),
            note = e.note,
            categoryId = e.categoryId,
            categoryName = e.categoryName,
            subCategoryId = e.subCategoryId,
            subCategoryName = e.subCategoryName,
            itemId = e.itemId,
            itemName = e.itemName,
            paymentMethodId = e.paymentMethodId,
            paymentMethodName = e.paymentMethodName,
            state = e.state.name,
            error = e.error,
            changedAt = e.changedAt,
        )
    }
}

/** Small JSON documents kept for offline use: the item tree and payment methods. */
@Entity(tableName = "cache")
data class CacheEntry(@PrimaryKey val name: String, val json: String)
