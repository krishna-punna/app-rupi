package com.dailyrupi.core.masterdata

import com.dailyrupi.core.model.CategoryNode

/** An item that can be picked for an expense, with where it sits in the tree. */
data class ItemChoice(
    val itemId: Long,
    val itemName: String,
    val subCategoryId: Long,
    val subCategoryName: String,
    val categoryId: Long,
    val categoryName: String,
) {
    val path: String get() = "$categoryName › $subCategoryName"
}

/** Every active item in the tree, in the tree's order. */
fun activeItemChoices(tree: List<CategoryNode>): List<ItemChoice> =
    tree.filter { it.active }.flatMap { category ->
        category.subCategories.filter { it.active }.flatMap { sub ->
            sub.items.filter { it.active }.map { item ->
                ItemChoice(item.id, item.name, sub.id, sub.name, category.id, category.name)
            }
        }
    }

/**
 * Search across all three levels. Every word must match somewhere; items whose own
 * name matches come first, then those matched only by their category or sub category.
 */
fun searchItems(choices: List<ItemChoice>, query: String): List<ItemChoice> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return choices
    val matches = choices.filter { choice ->
        val haystack = "${choice.itemName} ${choice.subCategoryName} ${choice.categoryName}".lowercase()
        words.all { it in haystack }
    }
    val (byName, byParent) = matches.partition { choice ->
        words.any { it in choice.itemName.lowercase() }
    }
    return byName + byParent
}
