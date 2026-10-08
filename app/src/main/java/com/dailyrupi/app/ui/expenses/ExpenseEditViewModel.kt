package com.dailyrupi.app.ui.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.app.data.ReferenceDataRepository
import com.dailyrupi.core.expense.ExpenseRules
import com.dailyrupi.core.masterdata.ItemChoice
import com.dailyrupi.core.model.ExpenseRequest
import com.dailyrupi.core.model.PaymentMethod
import com.dailyrupi.core.net.userMessage
import com.dailyrupi.core.sync.LocalExpense
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpenseEditUiState(
    val isEdit: Boolean = false,
    val loading: Boolean = true,
    val loadError: String? = null,
    val items: List<ItemChoice> = emptyList(),
    val recent: List<ItemChoice> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val item: ItemChoice? = null,
    val amount: String = "",
    val spentAt: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES),
    val paymentMethodId: Long? = null,
    val note: String = "",
    val itemError: String? = null,
    val amountError: String? = null,
    val dateError: String? = null,
    val noteError: String? = null,
    val paymentMethodError: String? = null,
    val error: String? = null,
    /** Why the server refused the last change, when it did; saving again sends it again. */
    val syncError: String? = null,
    val saving: Boolean = false,
    val done: Boolean = false,
)

@HiltViewModel
class ExpenseEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val expenses: ExpenseRepository,
    private val referenceData: ReferenceDataRepository,
    private val clock: Clock,
) : ViewModel() {

    private val expenseKey: String? = savedState.get<String>("key")
    private var original: LocalExpense? = null

    private val _state = MutableStateFlow(
        ExpenseEditUiState(isEdit = expenseKey != null, spentAt = now().truncatedTo(ChronoUnit.MINUTES)),
    )
    val state: StateFlow<ExpenseEditUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                if (expenseKey != null) {
                    original = expenses.get(expenseKey)
                    if (original == null) {
                        _state.update { it.copy(loading = false, loadError = "This expense was deleted.") }
                        return@launch
                    }
                }
                val loaded = original
                val items = referenceData.itemChoices()
                val methods = referenceData.paymentMethods()
                val byId = items.associateBy { it.itemId }
                val recent = expenses.recentItemIds().mapNotNull(byId::get)
                _state.update { current ->
                    if (loaded != null) {
                        current.withExpense(loaded, items, methods, recent)
                    } else {
                        val last = expenses.lastPaymentMethodId()?.takeIf { id -> methods.any { it.id == id } }
                        current.copy(
                            items = items,
                            recent = recent,
                            paymentMethods = methods,
                            paymentMethodId = current.paymentMethodId ?: last ?: methods.firstOrNull()?.id,
                        )
                    }.copy(loading = false)
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadError = e.userMessage()) }
            }
        }
    }

    fun selectItem(item: ItemChoice) = _state.update { it.copy(item = item, itemError = null, error = null) }

    fun onAmountChange(value: String) = _state.update { it.copy(amount = value, amountError = null, error = null) }

    fun onDateChange(date: LocalDate) = _state.update {
        it.copy(spentAt = LocalDateTime.of(date, it.spentAt.toLocalTime()), dateError = null, error = null)
    }

    fun onTimeChange(time: LocalTime) = _state.update {
        it.copy(spentAt = LocalDateTime.of(it.spentAt.toLocalDate(), time), dateError = null, error = null)
    }

    fun selectPaymentMethod(id: Long) = _state.update { it.copy(paymentMethodId = id, paymentMethodError = null, error = null) }

    fun onNoteChange(value: String) = _state.update { it.copy(note = value, noteError = null, error = null) }

    fun save() {
        val form = _state.value
        if (form.saving || form.loading) return
        val amount = ExpenseRules.parseAmount(form.amount)
        val checked = form.copy(
            itemError = if (form.item == null) "Pick an item" else null,
            amountError = (amount as? ExpenseRules.Amount.Invalid)?.reason,
            dateError = if (ExpenseRules.isFuture(form.spentAt, now())) "Date and time cannot be in the future" else null,
            paymentMethodError = if (form.paymentMethodId == null) "Pick a payment method" else null,
            noteError = ExpenseRules.noteError(form.note),
        )
        val hasErrors = listOf(
            checked.itemError, checked.amountError, checked.dateError, checked.paymentMethodError, checked.noteError,
        ).any { it != null }
        if (hasErrors || amount !is ExpenseRules.Amount.Valid) {
            _state.value = checked
            return
        }
        val item = form.item!!
        val method = form.paymentMethods.first { it.id == form.paymentMethodId }
        val request = ExpenseRequest(
            itemId = item.itemId,
            paymentMethodId = form.paymentMethodId!!,
            amount = amount.value,
            spentAt = form.spentAt,
            note = form.note.trim().ifEmpty { null },
        )
        _state.value = checked.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                expenses.save(expenseKey, request, item, method)
                _state.update { it.copy(saving = false, done = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.userMessage()) }
            }
        }
    }

    /** Leaves the screen at once; the list offers Undo before the delete is saved. */
    fun delete() {
        val expense = original ?: return
        expenses.scheduleDelete(expense)
        _state.update { it.copy(done = true) }
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    private fun ExpenseEditUiState.withExpense(
        expense: LocalExpense,
        items: List<ItemChoice>,
        methods: List<PaymentMethod>,
        recent: List<ItemChoice>,
    ): ExpenseEditUiState {
        // An old expense may use an item or payment method made inactive since; keep showing it.
        val item = items.firstOrNull { it.itemId == expense.itemId } ?: ItemChoice(
            expense.itemId, expense.itemName, expense.subCategoryId, expense.subCategoryName,
            expense.categoryId, expense.categoryName,
        )
        val allMethods = if (methods.any { it.id == expense.paymentMethodId }) {
            methods
        } else {
            methods + PaymentMethod(expense.paymentMethodId, expense.paymentMethodName)
        }
        return copy(
            items = items,
            recent = recent,
            paymentMethods = allMethods,
            item = item,
            amount = expense.amount.stripTrailingZeros().toPlainString(),
            spentAt = expense.spentAt,
            paymentMethodId = expense.paymentMethodId,
            note = expense.note.orEmpty(),
            syncError = expense.error,
        )
    }
}
