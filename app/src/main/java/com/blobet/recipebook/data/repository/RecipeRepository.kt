package com.blobet.recipebook.data.repository

import com.blobet.recipebook.data.local.dao.IngredientDao
import com.blobet.recipebook.data.local.dao.RecipeDao
import com.blobet.recipebook.data.local.dao.TagDao
import com.blobet.recipebook.data.local.entity.RecipeTagCrossRef
import com.blobet.recipebook.data.local.entity.toDomainModel
import com.blobet.recipebook.data.local.entity.toEntity
import com.blobet.recipebook.domain.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val ingredientDao: IngredientDao,
    private val tagDao: TagDao,
) {
    fun getAllRecipes(): Flow<List<Recipe>> {
        return recipeDao.getAllRecipes().map { list ->
            list.map { it.toDomainModel() }
        }
    }

    fun getFavoriteRecipes(): Flow<List<Recipe>> {
        return recipeDao.getFavoriteRecipes().map { list ->
            list.map { it.toDomainModel() }
        }
    }

    fun searchRecipes(query: String): Flow<List<Recipe>> {
        return recipeDao.searchRecipes(query).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    fun searchByIngredient(query: String): Flow<List<Recipe>> {
        return recipeDao.searchByIngredient(query).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    fun getRecipesByTags(tagIds: List<Long>): Flow<List<Recipe>> {
        return recipeDao.getRecipesByTags(tagIds).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    fun getRecipeById(id: Long): Flow<Recipe?> {
        return recipeDao.getRecipeById(id).map { it?.toDomainModel() }
    }

    suspend fun getRecipeByIdOnce(id: Long): Recipe? {
        return recipeDao.getRecipeByIdOnce(id)?.toDomainModel()
    }

    suspend fun getAllRecipesOnce(): List<Recipe> {
        return recipeDao.getAllRecipesOnce().map { it.toDomainModel() }
    }

    suspend fun findDuplicateForImport(sourceUrl: String?, title: String): Recipe? {
        val normalizedUrl = sourceUrl?.trim().orEmpty()
        if (normalizedUrl.isNotBlank()) {
            recipeDao.getRecipeBySourceUrlOnce(normalizedUrl)?.let { return it.toDomainModel() }
        }

        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) return null
        return recipeDao.getRecipeByTitleOnce(normalizedTitle)?.toDomainModel()
    }

    suspend fun saveRecipe(recipe: Recipe): Long {
        val now = System.currentTimeMillis()
        val recipeEntity = recipe.toEntity().copy(updatedAt = now)

        val recipeId = if (recipe.id == 0L) {
            recipeDao.insertRecipe(recipeEntity.copy(createdAt = now))
        } else {
            recipeDao.updateRecipe(recipeEntity)
            recipe.id
        }

        // Replace all ingredients
        ingredientDao.deleteIngredientsForRecipe(recipeId)
        val ingredientEntities = recipe.ingredients.mapIndexed { index, ingredient ->
            ingredient.toEntity(recipeId).copy(id = 0, orderIndex = index)
        }
        ingredientDao.insertIngredients(ingredientEntities)

        // Replace all tag associations
        tagDao.deleteAllTagsForRecipe(recipeId)
        recipe.tags.forEach { tag ->
            val tagId = if (tag.id == 0L) {
                val existing = tagDao.getTagByName(tag.name)
                existing?.id ?: tagDao.insertTag(tag.toEntity())
            } else {
                tag.id
            }
            tagDao.insertRecipeTagCrossRef(RecipeTagCrossRef(recipeId, tagId))
        }

        return recipeId
    }

    suspend fun deleteRecipe(id: Long) {
        recipeDao.deleteRecipeById(id)
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        recipeDao.updateFavoriteStatus(id, isFavorite)
    }
}
