package com.dailyrupi.app.ui.expenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dailyrupi.app.ui.components.CenteredMessage
import com.dailyrupi.core.masterdata.ItemChoice
import com.dailyrupi.core.masterdata.searchItems

/**
 * Full-screen item picker: type to search across category, sub category and item,
 * or drill down category → sub category → item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemPickerDialog(items: List<ItemChoice>, onPick: (ItemChoice) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var subCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }

    val back: () -> Unit = {
        when {
            subCategoryId != null -> subCategoryId = null
            categoryId != null -> categoryId = null
            else -> onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = back)
        val title = when {
            subCategoryId != null -> items.first { it.subCategoryId == subCategoryId }.subCategoryName
            categoryId != null -> items.first { it.categoryId == categoryId }.categoryName
            else -> "Choose item"
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = back) {
                            if (categoryId == null) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            } else {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search items") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                when {
                    items.isEmpty() -> CenteredMessage("No active items. Add some in the web app first.")
                    query.isNotBlank() -> SearchResults(items, query, onPick)
                    subCategoryId != null -> ChoiceList(
                        rows = items.filter { it.subCategoryId == subCategoryId }.map { it.itemId to it.itemName },
                        drillDown = false,
                        onClick = { id -> onPick(items.first { it.itemId == id }) },
                    )
                    categoryId != null -> ChoiceList(
                        rows = items.filter { it.categoryId == categoryId }
                            .distinctBy { it.subCategoryId }
                            .map { it.subCategoryId to it.subCategoryName },
                        drillDown = true,
                        onClick = { subCategoryId = it },
                    )
                    else -> ChoiceList(
                        rows = items.distinctBy { it.categoryId }.map { it.categoryId to it.categoryName },
                        drillDown = true,
                        onClick = { categoryId = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResults(items: List<ItemChoice>, query: String, onPick: (ItemChoice) -> Unit) {
    val results = remember(items, query) { searchItems(items, query) }
    if (results.isEmpty()) {
        CenteredMessage("No items match \"$query\"")
        return
    }
    LazyColumn {
        items(results, key = { it.itemId }) { choice ->
            ListItem(
                headlineContent = { Text(choice.itemName) },
                supportingContent = { Text(choice.path) },
                modifier = Modifier.clickable { onPick(choice) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ChoiceList(rows: List<Pair<Long, String>>, drillDown: Boolean, onClick: (Long) -> Unit) {
    LazyColumn {
        items(rows, key = { it.first }) { (id, name) ->
            ListItem(
                headlineContent = { Text(name) },
                trailingContent = if (drillDown) {
                    { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
                } else {
                    null
                },
                modifier = Modifier.clickable { onClick(id) },
            )
            HorizontalDivider()
        }
    }
}
