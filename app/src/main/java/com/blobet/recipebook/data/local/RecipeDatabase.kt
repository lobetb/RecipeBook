package com.blobet.recipebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.blobet.recipebook.data.local.dao.IngredientDao
import com.blobet.recipebook.data.local.dao.RecipeDao
import com.blobet.recipebook.data.local.dao.TagDao
import com.blobet.recipebook.data.local.entity.IngredientEntity
import com.blobet.recipebook.data.local.entity.RecipeEntity
import com.blobet.recipebook.data.local.entity.RecipeTagCrossRef
import com.blobet.recipebook.data.local.entity.TagEntity

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        TagEntity::class,
        RecipeTagCrossRef::class,
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun tagDao(): TagDao
}
