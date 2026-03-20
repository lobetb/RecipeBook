package com.blobet.recipebook.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blobet.recipebook.data.repository.RecipeRepository
import com.blobet.recipebook.data.repository.TagRepository
import com.blobet.recipebook.domain.model.Recipe
import com.blobet.recipebook.domain.model.Tag
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recipes: List<Recipe> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val searchQuery: String = "",
    val selectedTagIds: Set<Long> = emptySet(),
    val selectedRecipeTypes: Set<RecipeTypeFilter> = emptySet(),
    val sortMode: SortMode = SortMode.NEWEST_FIRST,
    val layoutMode: LayoutMode = LayoutMode.LARGE,
    val showFavoritesOnly: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recipeRepository: RecipeRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectedRecipeTypes = MutableStateFlow<Set<RecipeTypeFilter>>(emptySet())
    private val _sortMode = MutableStateFlow(SortMode.NEWEST_FIRST)
    private val _layoutMode = MutableStateFlow(loadSavedLayoutMode())
    private val _showFavoritesOnly = MutableStateFlow(false)

    private val selectedFilters = combine(
        _selectedTagIds,
        _selectedRecipeTypes,
    ) { tagIds, recipeTypes ->
        tagIds to recipeTypes
    }

    private val displayOptions = combine(
        _sortMode,
        _layoutMode,
    ) { sortMode, layoutMode ->
        sortMode to layoutMode
    }

    private val displayAndFavorites = combine(
        displayOptions,
        _showFavoritesOnly,
    ) { options, favoritesOnly ->
        Triple(options.first, options.second, favoritesOnly)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val recipes: StateFlow<List<Recipe>> = combine(
        _searchQuery,
        selectedFilters,
        displayOptions,
        _showFavoritesOnly,
    ) { query, selectedFilters, displayOptions, favoritesOnly ->
        val (tagIds, recipeTypes) = selectedFilters
        val (sortMode, layoutMode) = displayOptions
        FilterRequest(query, tagIds, recipeTypes, sortMode, layoutMode, favoritesOnly)
    }.flatMapLatest { request ->
        val query = request.query
        val tagIds = request.tagIds
        val favoritesOnly = request.favoritesOnly
        when {
            favoritesOnly && query.isNotBlank() -> {
                recipeRepository.searchRecipes(query)
            }
            favoritesOnly -> recipeRepository.getFavoriteRecipes()
            query.isNotBlank() -> recipeRepository.searchRecipes(query)
            tagIds.isNotEmpty() -> recipeRepository.getRecipesByTags(tagIds.toList())
            else -> recipeRepository.getAllRecipes()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<HomeUiState> = combine(
        recipes,
        tagRepository.getAllTags(),
        _searchQuery,
        selectedFilters,
        displayAndFavorites,
    ) { recipes, tags, query, selectedFilters, displayAndFavorites ->
        val (selectedTags, selectedRecipeTypes) = selectedFilters
        val (sortMode, layoutMode, favoritesOnly) = displayAndFavorites
        val filteredRecipes = recipes
            .let { list ->
                if (favoritesOnly && query.isNotBlank()) {
                    list.filter { it.isFavorite }
                } else {
                    list
                }
            }
            .let { list ->
                if (selectedRecipeTypes.isEmpty()) {
                    list
                } else {
                    list.filter { recipe ->
                        selectedRecipeTypes.any { type -> type.matches(recipe) }
                    }
                }
            }
            .sortedByMode(sortMode)

        HomeUiState(
            recipes = filteredRecipes,
            allTags = tags,
            searchQuery = query,
            selectedTagIds = selectedTags,
            selectedRecipeTypes = selectedRecipeTypes,
            sortMode = sortMode,
            layoutMode = layoutMode,
            showFavoritesOnly = favoritesOnly,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onToggleTag(tagId: Long) {
        _selectedTagIds.value = _selectedTagIds.value.toMutableSet().apply {
            if (contains(tagId)) remove(tagId) else add(tagId)
        }
    }

    fun onToggleRecipeType(type: RecipeTypeFilter) {
        _selectedRecipeTypes.value = _selectedRecipeTypes.value.toMutableSet().apply {
            if (contains(type)) remove(type) else add(type)
        }
    }

    fun onToggleFavorites() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
    }

    fun onSortModeChange(mode: SortMode) {
        _sortMode.value = mode
    }

    fun onLayoutModeChange(mode: LayoutMode) {
        _layoutMode.value = mode
        saveLayoutMode(mode)
    }

    fun onToggleRecipeFavorite(recipeId: Long, currentFavorite: Boolean) {
        viewModelScope.launch {
            recipeRepository.toggleFavorite(recipeId, !currentFavorite)
        }
    }

    private data class FilterRequest(
        val query: String,
        val tagIds: Set<Long>,
        val recipeTypes: Set<RecipeTypeFilter>,
        val sortMode: SortMode,
        val layoutMode: LayoutMode,
        val favoritesOnly: Boolean,
    )

    private fun loadSavedLayoutMode(): LayoutMode {
        val stored = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAYOUT_MODE, null)
            ?: return LayoutMode.LARGE

        return LayoutMode.entries.firstOrNull { it.name == stored } ?: LayoutMode.LARGE
    }

    private fun saveLayoutMode(mode: LayoutMode) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAYOUT_MODE, mode.name)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "home_ui_prefs"
        private const val KEY_LAYOUT_MODE = "layout_mode"
    }
}
