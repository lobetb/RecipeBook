package com.blobet.recipebook.data.repository

import com.blobet.recipebook.data.local.dao.ShoppingListDao
import com.blobet.recipebook.data.local.entity.toDomainModel
import com.blobet.recipebook.domain.model.ShoppingListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingListRepository @Inject constructor(
    private val shoppingListDao: ShoppingListDao,
) {

    fun observeItems(): Flow<List<ShoppingListItem>> {
        return shoppingListDao.observeAllItems().map { list -> list.map { it.toDomainModel() } }
    }

    suspend fun addItem(
        name: String,
        quantity: Double?,
        unit: String?,
        sourceRecipeId: Long?,
        sourceRecipeTitle: String?,
    ) {
        val normalizedName = normalize(name)
        if (normalizedName.isBlank()) return
        val normalizedUnit = normalize(unit)
        val now = System.currentTimeMillis()

        val existing = shoppingListDao.findByNormalizedNameAndUnit(normalizedName, normalizedUnit)
        if (existing == null) {
            shoppingListDao.insert(
                com.blobet.recipebook.data.local.entity.ShoppingListItemEntity(
                    name = name.trim(),
                    normalizedName = normalizedName,
                    quantity = quantity,
                    unit = unit?.trim().orEmpty().ifBlank { null },
                    normalizedUnit = normalizedUnit,
                    isChecked = false,
                    sourceRecipeId = sourceRecipeId,
                    sourceRecipeTitle = sourceRecipeTitle,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            return
        }

        val mergedQuantity = when {
            existing.quantity != null && quantity != null -> existing.quantity + quantity
            existing.quantity != null -> existing.quantity
            else -> quantity
        }

        shoppingListDao.update(
            existing.copy(
                quantity = mergedQuantity,
                isChecked = false,
                updatedAt = now,
                sourceRecipeId = sourceRecipeId ?: existing.sourceRecipeId,
                sourceRecipeTitle = sourceRecipeTitle ?: existing.sourceRecipeTitle,
            )
        )
    }

    suspend fun updateItem(item: ShoppingListItem) {
        val normalizedName = normalize(item.name)
        if (normalizedName.isBlank()) return
        val normalizedUnit = normalize(item.unit)

        shoppingListDao.update(
            com.blobet.recipebook.data.local.entity.ShoppingListItemEntity(
                id = item.id,
                name = item.name.trim(),
                normalizedName = normalizedName,
                quantity = item.quantity,
                unit = item.unit?.trim().orEmpty().ifBlank { null },
                normalizedUnit = normalizedUnit,
                isChecked = item.isChecked,
                sourceRecipeId = item.sourceRecipeId,
                sourceRecipeTitle = item.sourceRecipeTitle,
                createdAt = item.createdAt,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun setChecked(itemId: Long, checked: Boolean) {
        shoppingListDao.setChecked(itemId, checked)
    }

    suspend fun deleteItem(itemId: Long) {
        shoppingListDao.deleteById(itemId)
    }

    suspend fun clearAll() {
        shoppingListDao.clearAll()
    }

    private fun normalize(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return ""
        val noDiacritics = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noDiacritics.lowercase().replace(Regex("\\s+"), " ")
    }
}
