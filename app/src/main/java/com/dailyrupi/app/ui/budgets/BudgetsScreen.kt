package com.dailyrupi.app.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyrupi.app.ui.components.CenteredMessage
import com.dailyrupi.app.ui.components.FullScreenLoading
import com.dailyrupi.core.format.Formats
import com.dailyrupi.core.model.BudgetLine
import com.dailyrupi.core.model.MonthBudget
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Budgets") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::previousMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    Formats.month(state.month),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::nextMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            val data = state.data
            when {
                state.loading -> FullScreenLoading()
                state.error != null -> CenteredMessage(state.error!!, onRetry = viewModel::load)
                data != null -> BudgetContent(data)
            }
        }
    }
}

@Composable
private fun BudgetContent(data: MonthBudget) {
    val budgeted = data.lines.filter { it.budget != null }
    val unbudgeted = data.lines.filter { it.budget == null && it.spent.signum() > 0 }
    // What is left counts only spending in categories that have a budget.
    val left = data.totalBudget - (data.totalSpent - data.unbudgetedSpent)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TotalRow("Budget", Formats.rupees(data.totalBudget))
                    TotalRow("Spent", Formats.rupees(data.totalSpent))
                    TotalRow(
                        if (left.signum() < 0) "Over budget" else "Left",
                        Formats.rupees(left.abs()),
                        if (left.signum() < 0) MaterialTheme.colorScheme.error else null,
                    )
                    if (data.unbudgetedSpent.signum() > 0) {
                        TotalRow("Spent without a budget", Formats.rupees(data.unbudgetedSpent))
                    }
                }
            }
        }
        if (budgeted.isEmpty() && unbudgeted.isEmpty()) {
            item { CenteredMessage("No budgets or spending this month. Budgets are set in the web app for now.") }
        }
        items(budgeted, key = { "b${it.categoryId}" }) { BudgetLineCard(it) }
        if (unbudgeted.isNotEmpty()) {
            item {
                Text(
                    "Without a budget",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(unbudgeted, key = { "u${it.categoryId}" }) { line ->
                TotalRow(categoryLabel(line), Formats.rupees(line.spent))
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String, color: Color? = null) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color ?: MaterialTheme.colorScheme.onSurface)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BudgetLineCard(line: BudgetLine) {
    val budget = line.budget ?: return
    val remaining = line.remaining ?: (budget - line.spent)
    val over = remaining.signum() < 0
    val usage = if (budget.signum() > 0) {
        line.spent.divide(budget, 4, RoundingMode.HALF_UP).toFloat()
    } else {
        1f
    }
    val barColor = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(categoryLabel(line), style = MaterialTheme.typography.titleSmall)
                Text("${percent(usage)}%", style = MaterialTheme.typography.labelLarge, color = barColor)
            }
            LinearProgressIndicator(
                progress = { usage.coerceIn(0f, 1f) },
                color = barColor,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${Formats.rupees(line.spent)} of ${Formats.rupees(budget)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (over) "${Formats.rupees(remaining.abs())} over" else "${Formats.rupees(remaining)} left",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun categoryLabel(line: BudgetLine) =
    if (line.categoryActive) line.categoryName else "${line.categoryName} (inactive)"

private fun percent(usage: Float): Int = BigDecimal(usage.toDouble() * 100).setScale(0, RoundingMode.HALF_UP).toInt()
