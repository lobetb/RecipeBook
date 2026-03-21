package com.blobet.recipebook.ui.shopping_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blobet.recipebook.data.repository.ShoppingListRepository
import com.blobet.recipebook.domain.model.ShoppingListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingListUiState(
    val items: List<ShoppingListItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val shoppingListRepository: ShoppingListRepository,
) : ViewModel() {

    val uiState: StateFlow<ShoppingListUiState> = shoppingListRepository.observeItems()
        .map { items ->
            ShoppingListUiState(
                items = items,
                isLoading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShoppingListUiState())

    fun setChecked(itemId: Long, checked: Boolean) {
        viewModelScope.launch {
            shoppingListRepository.setChecked(itemId, checked)
        }
    }

    fun updateItem(
        item: ShoppingListItem,
        newName: String,
        newQuantity: String,
        newUnit: String,
    ) {
        val parsedQuantity = newQuantity.trim().replace(',', '.').toDoubleOrNull()
        viewModelScope.launch {
            shoppingListRepository.updateItem(
                item.copy(
                    name = newName.trim(),
                    quantity = parsedQuantity,
                    unit = newUnit.trim().ifBlank { null },
                )
            )
        }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            shoppingListRepository.deleteItem(itemId)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            shoppingListRepository.clearAll()
        }
    }
}
