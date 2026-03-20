package com.blobet.recipebook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.blobet.recipebook.data.local.entity.RecipeEntity
import com.blobet.recipebook.data.local.entity.RecipeWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY updatedAt DESC")
    fun getAllRecipes(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteRecipes(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("""
        SELECT * FROM recipes 
        WHERE title LIKE '%' || :query || '%' 
        OR description LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
    """)
    fun searchRecipes(query: String): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("""
        SELECT DISTINCT r.* FROM recipes r
        INNER JOIN recipe_tag_cross_ref rtc ON r.id = rtc.recipeId
        WHERE rtc.tagId IN (:tagIds)
        ORDER BY r.updatedAt DESC
    """)
    fun getRecipesByTags(tagIds: List<Long>): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun getRecipeById(id: Long): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipeByIdOnce(id: Long): RecipeWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Delete
    suspend fun deleteRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipeById(id: Long)

    @Query("UPDATE recipes SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT DISTINCT r.* FROM recipes r
        INNER JOIN ingredients i ON r.id = i.recipeId
        WHERE i.name LIKE '%' || :ingredientQuery || '%'
        ORDER BY r.updatedAt DESC
    """)
    fun searchByIngredient(ingredientQuery: String): Flow<List<RecipeWithDetails>>
}
