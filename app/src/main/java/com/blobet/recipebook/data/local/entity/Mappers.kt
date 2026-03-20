package com.blobet.recipebook.data.local.entity

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.blobet.recipebook.domain.model.Ingredient
import com.blobet.recipebook.domain.model.Recipe
import com.blobet.recipebook.domain.model.Tag

private val gson = Gson()

fun RecipeWithDetails.toDomainModel(): Recipe {
    val instructionsList: List<String> = if (recipe.instructions.isBlank()) {
        emptyList()
    } else {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(recipe.instructions, type)
        } catch (e: Exception) {
            listOf(recipe.instructions)
        }
    }

    return Recipe(
        id = recipe.id,
        title = recipe.title,
        description = recipe.description,
        sourceUrl = recipe.sourceUrl,
        imageUrl = recipe.imageUrl,
        localImagePath = recipe.localImagePath,
        servings = recipe.servings,
        prepTimeMinutes = recipe.prepTimeMinutes,
        cookTimeMinutes = recipe.cookTimeMinutes,
        instructions = instructionsList,
        ingredients = ingredients.sortedBy { it.orderIndex }.map { it.toDomainModel() },
        tags = tags.map { it.toDomainModel() },
        isFavorite = recipe.isFavorite,
        createdAt = recipe.createdAt,
        updatedAt = recipe.updatedAt,
    )
}

fun Recipe.toEntity(): RecipeEntity {
    return RecipeEntity(
        id = id,
        title = title,
        description = description,
        sourceUrl = sourceUrl,
        imageUrl = imageUrl,
        localImagePath = localImagePath,
        servings = servings,
        prepTimeMinutes = prepTimeMinutes,
        cookTimeMinutes = cookTimeMinutes,
        instructions = gson.toJson(instructions),
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun IngredientEntity.toDomainModel(): Ingredient {
    return Ingredient(
        id = id,
        name = name,
        quantity = quantity,
        unit = unit,
        orderIndex = orderIndex,
    )
}

fun Ingredient.toEntity(recipeId: Long): IngredientEntity {
    return IngredientEntity(
        id = id,
        recipeId = recipeId,
        name = name,
        quantity = quantity,
        unit = unit,
        orderIndex = orderIndex,
    )
}

fun TagEntity.toDomainModel(): Tag {
    return Tag(
        id = id,
        name = name,
    )
}

fun Tag.toEntity(): TagEntity {
    return TagEntity(
        id = id,
        name = name,
    )
}
