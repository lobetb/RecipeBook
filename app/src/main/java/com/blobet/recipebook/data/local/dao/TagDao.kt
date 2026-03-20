package com.blobet.recipebook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.blobet.recipebook.data.local.entity.RecipeTagCrossRef
import com.blobet.recipebook.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAllTagsOnce(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTagById(id: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity): Long

    @Delete
    suspend fun deleteTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTagById(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeTagCrossRef(crossRef: RecipeTagCrossRef)

    @Delete
    suspend fun deleteRecipeTagCrossRef(crossRef: RecipeTagCrossRef)

    @Query("DELETE FROM recipe_tag_cross_ref WHERE recipeId = :recipeId")
    suspend fun deleteAllTagsForRecipe(recipeId: Long)

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN recipe_tag_cross_ref rtc ON t.id = rtc.tagId
        WHERE rtc.recipeId = :recipeId
        ORDER BY t.name ASC
    """)
    suspend fun getTagsForRecipe(recipeId: Long): List<TagEntity>
}
