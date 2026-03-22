package com.blobet.recipebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blobet.recipebook.data.local.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {

    @Query("SELECT * FROM shopping_list_items ORDER BY isChecked ASC, updatedAt DESC")
    fun observeAllItems(): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT * FROM shopping_list_items WHERE normalizedName = :normalizedName AND normalizedUnit = :normalizedUnit LIMIT 1")
    suspend fun findByNormalizedNameAndUnit(
        normalizedName: String,
        normalizedUnit: String,
    ): ShoppingListItemEntity?

    @Query("DELETE FROM shopping_list_items WHERE normalizedName = :normalizedName AND normalizedUnit = :normalizedUnit")
    suspend fun deleteByNormalizedNameAndUnit(
        normalizedName: String,
        normalizedUnit: String,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ShoppingListItemEntity): Long

    @Update
    suspend fun update(item: ShoppingListItemEntity)

    @Query("UPDATE shopping_list_items SET isChecked = :checked, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM shopping_list_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM shopping_list_items")
    suspend fun clearAll()
}
