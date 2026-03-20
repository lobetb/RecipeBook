package com.blobet.recipebook.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blobet.recipebook.data.repository.RecipeRepository
import com.blobet.recipebook.domain.model.Recipe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val recipe: Recipe? = null,
    val currentServings: Int = 4,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recipeId: Long = savedStateHandle.get<Long>("recipeId") ?: 0L

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadRecipe()
    }

    private fun loadRecipe() {
        viewModelScope.launch {
            recipeRepository.getRecipeById(recipeId).collect { recipe ->
                _uiState.update { state ->
                    state.copy(
                        recipe = recipe,
                        currentServings = if (state.currentServings == state.recipe?.servings || state.recipe == null) {
                            recipe?.servings ?: 4
                        } else {
                            state.currentServings
                        },
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onServingsChange(servings: Int) {
        _uiState.update { it.copy(currentServings = servings) }
    }

    fun onToggleFavorite() {
        val recipe = _uiState.value.recipe ?: return
        viewModelScope.launch {
            recipeRepository.toggleFavorite(recipe.id, !recipe.isFavorite)
        }
    }

    fun onDeleteRecipe() {
        viewModelScope.launch {
            recipeRepository.deleteRecipe(recipeId)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }
}
