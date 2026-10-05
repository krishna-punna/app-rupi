@file:UseSerializers(BigDecimalSerializer::class, LocalDateTimeSerializer::class, LocalDateSerializer::class)

package com.dailyrupi.core.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Mirrors of the Daily Rupi backend's request and response records.

@Serializable
data class ApiErrorBody(val code: String = "", val message: String = "")

@Serializable
data class CurrentUser(
    val username: String,
    val roles: List<String> = emptyList(),
    val passwordChangeRequired: Boolean = false,
)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class PaymentMethod(val id: Long, val name: String)

@Serializable
data class ExpenseRequest(
    val itemId: Long,
    val paymentMethodId: Long,
    val amount: BigDecimal,
    val spentAt: LocalDateTime,
    val note: String? = null,
)

@Serializable
data class Expense(
    val id: Long,
    val amount: BigDecimal,
    val spentAt: LocalDateTime,
    val note: String? = null,
    val categoryId: Long,
    val categoryName: String,
    val subCategoryId: Long,
    val subCategoryName: String,
    val itemId: Long,
    val itemName: String,
    val paymentMethodId: Long,
    val paymentMethodName: String,
)

@Serializable
data class ExpensePage(
    val content: List<Expense>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
)

@Serializable
data class ExpenseSummary(
    val date: LocalDate,
    val weekStart: LocalDate,
    val monthStart: LocalDate,
    val today: BigDecimal,
    val week: BigDecimal,
    val month: BigDecimal,
)

@Serializable
data class BudgetLine(
    val categoryId: Long,
    val categoryName: String,
    val categoryActive: Boolean = true,
    /** Null when the category has no budget for the month. */
    val budget: BigDecimal? = null,
    val spent: BigDecimal,
    /** Null without a budget; negative when over budget. */
    val remaining: BigDecimal? = null,
)

@Serializable
data class MonthBudget(
    val month: String,
    val totalBudget: BigDecimal,
    val totalSpent: BigDecimal,
    val unbudgetedSpent: BigDecimal,
    val lines: List<BudgetLine>,
)

@Serializable
data class ItemNode(val id: Long, val name: String, val isDefault: Boolean = false, val active: Boolean = true)

@Serializable
data class SubCategoryNode(
    val id: Long,
    val name: String,
    val isDefault: Boolean = false,
    val active: Boolean = true,
    val items: List<ItemNode> = emptyList(),
)

@Serializable
data class CategoryNode(
    val id: Long,
    val name: String,
    val isDefault: Boolean = false,
    val active: Boolean = true,
    val subCategories: List<SubCategoryNode> = emptyList(),
)
