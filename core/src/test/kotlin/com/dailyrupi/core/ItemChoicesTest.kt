package com.dailyrupi.core

import com.dailyrupi.core.masterdata.activeItemChoices
import com.dailyrupi.core.masterdata.searchItems
import com.dailyrupi.core.model.CategoryNode
import com.dailyrupi.core.model.ItemNode
import com.dailyrupi.core.model.SubCategoryNode
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemChoicesTest {

    private val tree = listOf(
        CategoryNode(
            1, "Food", subCategories = listOf(
                SubCategoryNode(10, "Groceries", items = listOf(ItemNode(100, "Milk"), ItemNode(101, "Rice", active = false))),
                SubCategoryNode(11, "Eating out", items = listOf(ItemNode(110, "Restaurant"), ItemNode(111, "Milk tea"))),
            ),
        ),
        CategoryNode(
            2, "Transport", subCategories = listOf(
                SubCategoryNode(20, "Fuel", items = listOf(ItemNode(200, "Petrol"))),
                SubCategoryNode(21, "Old", active = false, items = listOf(ItemNode(210, "Tonga"))),
            ),
        ),
        CategoryNode(3, "Gone", active = false, subCategories = listOf(SubCategoryNode(30, "X", items = listOf(ItemNode(300, "Y"))))),
    )

    @Test
    fun onlyActiveItemsUnderActiveParents() {
        assertEquals(listOf(100L, 110L, 111L, 200L), activeItemChoices(tree).map { it.itemId })
    }

    @Test
    fun searchMatchesAllLevelsItemNamesFirst() {
        val choices = activeItemChoices(tree)
        assertEquals(listOf(100L, 111L), searchItems(choices, "milk").map { it.itemId })
        assertEquals(listOf(200L), searchItems(choices, "fuel").map { it.itemId })
        assertEquals(listOf(110L, 111L), searchItems(choices, "eating").map { it.itemId })
        assertEquals(listOf(111L), searchItems(choices, "tea eating").map { it.itemId })
        assertEquals(choices, searchItems(choices, "  "))
    }
}
