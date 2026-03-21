package com.blobet.recipebook.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.blobet.recipebook.data.local.RecipeDatabase
import com.blobet.recipebook.data.local.dao.IngredientDao
import com.blobet.recipebook.data.local.dao.RecipeDao
import com.blobet.recipebook.data.local.dao.ShoppingListDao
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
        ).addMigrations(MIGRATION_1_2).build()
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

    @Provides
    fun provideShoppingListDao(database: RecipeDatabase): ShoppingListDao {
        return database.shoppingListDao()
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS shopping_list_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        normalizedName TEXT NOT NULL,
                        quantity REAL,
                        unit TEXT,
                        normalizedUnit TEXT NOT NULL,
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        sourceRecipeId INTEGER,
                        sourceRecipeTitle TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent()
            )
            database.execSQL(
                """
                    CREATE INDEX IF NOT EXISTS index_shopping_list_items_normalizedName_normalizedUnit
                    ON shopping_list_items (normalizedName, normalizedUnit)
                """.trimIndent()
            )
        }
    }
}
