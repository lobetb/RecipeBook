package com.blobet.recipebook.util

import com.blobet.recipebook.domain.model.Ingredient
import com.blobet.recipebook.domain.model.Recipe
import com.blobet.recipebook.domain.model.Tag

data class RecipeSharePayload(
    val schemaVersion: Int = 1,
    val title: String,
    val description: String,
    val sourceUrl: String?,
    val servings: Int,
    val prepTimeMinutes: Int?,
    val cookTimeMinutes: Int?,
    val instructions: List<String>,
    val ingredients: List<RecipeShareIngredient>,
    val tags: List<String>,
) {
    data class RecipeShareIngredient(
        val name: String,
        val quantity: Double?,
        val unit: String?,
        val orderIndex: Int,
    )

    fun toRecipe(): Recipe {
        return Recipe(
            title = title,
            description = description,
            sourceUrl = sourceUrl,
            servings = servings,
            prepTimeMinutes = prepTimeMinutes,
            cookTimeMinutes = cookTimeMinutes,
            instructions = instructions,
            ingredients = ingredients.mapIndexed { index, item ->
                Ingredient(
                    name = item.name,
                    quantity = item.quantity,
                    unit = item.unit,
                    orderIndex = item.orderIndex.takeIf { it >= 0 } ?: index,
                )
            },
            tags = tags
                .filter { it.isNotBlank() }
                .distinct()
                .map { Tag(name = it.trim()) },
        )
    }

    companion object {
        fun fromRecipe(recipe: Recipe): RecipeSharePayload {
            return RecipeSharePayload(
                title = recipe.title,
                description = recipe.description,
                sourceUrl = recipe.sourceUrl,
                servings = recipe.servings,
                prepTimeMinutes = recipe.prepTimeMinutes,
                cookTimeMinutes = recipe.cookTimeMinutes,
                instructions = recipe.instructions,
                ingredients = recipe.ingredients.map {
                    RecipeShareIngredient(
                        name = it.name,
                        quantity = it.quantity,
                        unit = it.unit,
                        orderIndex = it.orderIndex,
                    )
                },
                tags = recipe.tags.map { it.name },
            )
        }
    }
}
