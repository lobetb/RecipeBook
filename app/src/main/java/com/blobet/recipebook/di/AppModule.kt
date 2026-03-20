package com.blobet.recipebook.di

import android.content.Context
import androidx.room.Room
import com.blobet.recipebook.data.local.RecipeDatabase
import com.blobet.recipebook.data.local.dao.IngredientDao
import com.blobet.recipebook.data.local.dao.RecipeDao
import com.blobet.recipebook.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RecipeDatabase {
        return Room.databaseBuilder(
            context,
            RecipeDatabase::class.java,
            "recipe_database"
        ).build()
    }

    @Provides
    fun provideRecipeDao(database: RecipeDatabase): RecipeDao {
        return database.recipeDao()
    }

    @Provides
    fun provideIngredientDao(database: RecipeDatabase): IngredientDao {
        return database.ingredientDao()
    }

    @Provides
    fun provideTagDao(database: RecipeDatabase): TagDao {
        return database.tagDao()
    }
}
