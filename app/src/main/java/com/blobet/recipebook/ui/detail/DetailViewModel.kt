package com.blobet.recipebook.ui.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blobet.recipebook.data.repository.RecipeRepository
import com.blobet.recipebook.data.repository.ShoppingListRepository
import com.blobet.recipebook.data.repository.TagRepository
import com.blobet.recipebook.domain.model.Ingredient
import com.blobet.recipebook.domain.model.IngredientConversion
import com.blobet.recipebook.domain.model.Recipe
import com.blobet.recipebook.domain.model.Tag
import com.blobet.recipebook.domain.model.UnitSystem
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val unitSystem: UnitSystem = UnitSystem.ORIGINAL,
    val availableTags: List<Tag> = emptyList(),
    val addedIngredientKeys: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recipeRepository: RecipeRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val tagRepository: TagRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recipeId: Long = savedStateHandle.get<Long>("recipeId") ?: 0L
    private val unitPrefKey = "detail_units_$recipeId"

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadPersistedUnitSystem()
        observeTags()
        loadRecipe()
    }

    private fun observeTags() {
        viewModelScope.launch {
            tagRepository.getAllTags().collect { tags ->
                _uiState.update { state -> state.copy(availableTags = tags) }
            }
        }
    }

    private fun loadPersistedUnitSystem() {
        val saved = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(unitPrefKey, UnitSystem.ORIGINAL.name)
        val system = UnitSystem.entries.firstOrNull { it.name == saved } ?: UnitSystem.ORIGINAL
        _uiState.update { it.copy(unitSystem = system) }
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

    fun onUnitSystemChange(system: UnitSystem) {
        _uiState.update { it.copy(unitSystem = system) }
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(unitPrefKey, system.name)
            .apply()
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

    fun onAddTag(tag: Tag) {
        val recipe = _uiState.value.recipe ?: return
        if (recipe.tags.any { it.id == tag.id }) return

        viewModelScope.launch {
            recipeRepository.saveRecipe(recipe.copy(tags = recipe.tags + tag))
        }
    }

    fun createAndAddTag(name: String) {
        val recipe = _uiState.value.recipe ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            val normalizedInput = normalizeTagKey(trimmed)
            val existing = tagRepository.getAllTagsOnce().firstOrNull {
                normalizeTagKey(it.name) == normalizedInput
            }
            val tag = existing ?: tagRepository.createTag(trimmed)
            if (recipe.tags.any { it.id == tag.id }) return@launch
            recipeRepository.saveRecipe(recipe.copy(tags = recipe.tags + tag))
        }
    }

    fun onRemoveTag(tag: Tag) {
        val recipe = _uiState.value.recipe ?: return
        if (recipe.tags.none { it.id == tag.id }) return

        viewModelScope.launch {
            recipeRepository.saveRecipe(recipe.copy(tags = recipe.tags.filterNot { it.id == tag.id }))
        }
    }

    fun onAddIngredientToShoppingList(ingredient: Ingredient) {
        val recipe = _uiState.value.recipe ?: return
        val selectedSystem = _uiState.value.unitSystem
        val ingredientToSave = IngredientConversion.convertIngredient(ingredient, selectedSystem)
        val key = ingredientKey(ingredient)

        if (key in _uiState.value.addedIngredientKeys) {
            viewModelScope.launch {
                shoppingListRepository.deleteByNameAndUnit(
                    name = ingredientToSave.name,
                    unit = ingredientToSave.unit,
                )
                _uiState.update { state ->
                    state.copy(addedIngredientKeys = state.addedIngredientKeys - key)
                }
            }
            return
        }

        viewModelScope.launch {
            shoppingListRepository.addItem(
                name = ingredientToSave.name,
                quantity = ingredientToSave.quantity,
                unit = ingredientToSave.unit,
                sourceRecipeId = recipe.id,
                sourceRecipeTitle = recipe.title,
            )

            _uiState.update { state ->
                state.copy(addedIngredientKeys = state.addedIngredientKeys + key)
            }
        }
    }

    private fun ingredientKey(ingredient: Ingredient): String {
        return "${ingredient.orderIndex}:${ingredient.name.trim().lowercase()}"
    }

    private fun normalizeTagKey(value: String): String {
        return value.trim().replace(Regex("\\s+"), " ").lowercase()
    }

    companion object {
        private const val PREFS_NAME = "detail_ui_prefs"
    }
}
