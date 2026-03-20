package com.blobet.recipebook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val sourceUrl: String? = null,
    val imageUrl: String? = null,
    val localImagePath: String? = null,
    val servings: Int = 4,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val instructions: String = "", // stored as JSON array
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
