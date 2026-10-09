package com.dailyrupi.app.ui.expenses

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyrupi.app.ui.components.CenteredMessage
import com.dailyrupi.app.ui.components.ErrorText
import com.dailyrupi.app.ui.components.FullScreenLoading
import com.dailyrupi.core.expense.ExpenseRules
import com.dailyrupi.core.format.Formats
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditScreen(onDone: () -> Unit, viewModel: ExpenseEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit expense" else "Add expense") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEdit && state.loadError == null) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete expense")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            state.loading -> FullScreenLoading(modifier)
            state.loadError != null -> CenteredMessage(state.loadError!!, modifier, onRetry = viewModel::load)
            else -> ExpenseForm(state, viewModel, onPickItem = { picking = true }, modifier = modifier)
        }
    }

    if (picking) {
        ItemPickerDialog(
            items = state.items,
            onPick = {
                viewModel.selectItem(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this expense?") },
            text = { Text("You can undo for a few seconds afterwards.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ExpenseForm(
    state: ExpenseEditUiState,
    viewModel: ExpenseEditViewModel,
    onPickItem: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.syncError?.let { ErrorText("The server refused this change: $it. Fix it and save, or delete it.") }
        if (state.recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recent", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.recent, key = { it.itemId }) { choice ->
                        FilterChip(
                            selected = state.item?.itemId == choice.itemId,
                            onClick = { viewModel.selectItem(choice) },
                            label = { Text(choice.itemName) },
                        )
                    }
                }
            }
        }

        Column {
            Surface(
                onClick = onPickItem,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(
                    1.dp,
                    if (state.itemError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Item", style = MaterialTheme.typography.labelMedium)
                    val item = state.item
                    if (item == null) {
                        Text("Choose an item", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(item.itemName, style = MaterialTheme.typography.bodyLarge)
                        Text(item.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            ErrorText(state.itemError, Modifier.padding(start = 16.dp, top = 4.dp))
        }

        OutlinedTextField(
            value = state.amount,
            onValueChange = viewModel::onAmountChange,
            label = { Text("Amount") },
            prefix = { Text("₹") },
            singleLine = true,
            isError = state.amountError != null,
            supportingText = { state.amountError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        val current = state.spentAt.toLocalDate()
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> viewModel.onDateChange(LocalDate.of(year, month + 1, day)) },
                            current.year,
                            current.monthValue - 1,
                            current.dayOfMonth,
                        ).apply {
                            datePicker.maxDate = System.currentTimeMillis()
                        }.show()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(Formats.date(state.spentAt.toLocalDate())) }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> viewModel.onTimeChange(LocalTime.of(hour, minute)) },
                            state.spentAt.hour,
                            state.spentAt.minute,
                            DateFormat.is24HourFormat(context),
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(Formats.time(state.spentAt)) }
            }
            ErrorText(state.dateError, Modifier.padding(start = 16.dp, top = 4.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Payment method", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                items(state.paymentMethods, key = { it.id }) { method ->
                    FilterChip(
                        selected = state.paymentMethodId == method.id,
                        onClick = { viewModel.selectPaymentMethod(method.id) },
                        label = { Text(method.name) },
                    )
                }
            }
            ErrorText(state.paymentMethodError)
        }

        OutlinedTextField(
            value = state.note,
            onValueChange = viewModel::onNoteChange,
            label = { Text("Note (optional)") },
            isError = state.noteError != null,
            supportingText = {
                Text(state.noteError ?: "${state.note.trim().length} / ${ExpenseRules.NOTE_MAX}")
            },
            modifier = Modifier.fillMaxWidth(),
        )

        ErrorText(state.error)

        Button(
            onClick = viewModel::save,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.saving) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Save")
            }
        }
    }
}

