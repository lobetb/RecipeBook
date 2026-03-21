package com.blobet.recipebook.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shopping_list_items",
    indices = [
        Index("normalizedName", "normalizedUnit"),
    ],
)
data class ShoppingListItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val normalizedUnit: String,
    val isChecked: Boolean = false,
    val sourceRecipeId: Long? = null,
    val sourceRecipeTitle: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
