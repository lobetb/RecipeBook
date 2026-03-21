package com.blobet.recipebook.domain.model

data class ShoppingListItem(
    val id: Long = 0,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val isChecked: Boolean = false,
    val sourceRecipeId: Long? = null,
    val sourceRecipeTitle: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
