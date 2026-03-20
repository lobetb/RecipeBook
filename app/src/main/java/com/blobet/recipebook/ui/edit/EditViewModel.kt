package com.blobet.recipebook.ui.edit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blobet.recipebook.R
import com.blobet.recipebook.data.parser.IngredientParser
import com.blobet.recipebook.data.repository.RecipeRepository
import com.blobet.recipebook.data.repository.TagRepository
import com.blobet.recipebook.domain.model.Recipe
import com.blobet.recipebook.domain.model.Tag
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

data class EditUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val description: String = "",
    val sourceUrl: String = "",
    val imageUrl: String? = null,
    val localImagePath: String? = null,
    val servingsStr: String = "4",
    val prepTimeStr: String = "",
    val cookTimeStr: String = "",
    val ingredientLines: List<String> = listOf(""),
    val instructionLines: List<String> = listOf(""),
    val availableTags: List<Tag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val savedRecipeId: Long? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class EditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recipeRepository: RecipeRepository,
    private val tagRepository: TagRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recipeId: Long = savedStateHandle.get<Long>("recipeId") ?: 0L

    private val _uiState = MutableStateFlow(EditUiState(isEditing = recipeId > 0))
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    init {
        loadTags()
        if (recipeId > 0) {
            loadRecipe(recipeId)
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            tagRepository.getAllTags().collect { tags ->
                _uiState.update { it.copy(availableTags = tags) }
            }
        }
    }

    private fun loadRecipe(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val recipe = recipeRepository.getRecipeByIdOnce(id)
            if (recipe != null) {
                _uiState.update { state ->
                    state.copy(
                        title = recipe.title,
                        description = recipe.description,
                        sourceUrl = recipe.sourceUrl ?: "",
                        imageUrl = recipe.imageUrl,
                        localImagePath = recipe.localImagePath,
                        servingsStr = recipe.servings.toString(),
                        prepTimeStr = recipe.prepTimeMinutes?.toString() ?: "",
                        cookTimeStr = recipe.cookTimeMinutes?.toString() ?: "",
                        ingredientLines = recipe.ingredients.map { it.displayText() }.ifEmpty { listOf("") },
                        instructionLines = recipe.instructions.ifEmpty { listOf("") },
                        selectedTagIds = recipe.tags.map { it.id }.toSet(),
                        isLoading = false,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = context.getString(R.string.error_recipe_not_found))
                }
            }
        }
    }

    /**
     * Pre-fill the form with data from a parsed recipe (used by ImportScreen).
     */
    fun prefillFromRecipe(recipe: Recipe) {
        _uiState.update { state ->
            state.copy(
                title = recipe.title,
                description = recipe.description,
                sourceUrl = recipe.sourceUrl ?: "",
                imageUrl = recipe.imageUrl,
                servingsStr = recipe.servings.toString(),
                prepTimeStr = recipe.prepTimeMinutes?.toString() ?: "",
                cookTimeStr = recipe.cookTimeMinutes?.toString() ?: "",
                ingredientLines = recipe.ingredients.map { it.displayText() }.ifEmpty { listOf("") },
                instructionLines = recipe.instructions.ifEmpty { listOf("") },
            )
        }
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun onSourceUrlChange(url: String) {
        _uiState.update { it.copy(sourceUrl = url) }
    }

    fun onServingsChange(servings: String) {
        _uiState.update { it.copy(servingsStr = servings.filter { c -> c.isDigit() }) }
    }

    fun onPrepTimeChange(time: String) {
        _uiState.update { it.copy(prepTimeStr = time.filter { c -> c.isDigit() }) }
    }

    fun onCookTimeChange(time: String) {
        _uiState.update { it.copy(cookTimeStr = time.filter { c -> c.isDigit() }) }
    }

    fun onIngredientLineChange(index: Int, value: String) {
        _uiState.update { state ->
            val lines = state.ingredientLines.toMutableList()
            if (index < lines.size) lines[index] = value
            state.copy(ingredientLines = lines)
        }
    }

    fun addIngredientLine() {
        _uiState.update { state ->
            state.copy(ingredientLines = state.ingredientLines + "")
        }
    }

    fun removeIngredientLine(index: Int) {
        _uiState.update { state ->
            val lines = state.ingredientLines.toMutableList()
            if (lines.size > 1) {
                lines.removeAt(index)
            } else {
                lines[0] = ""
            }
            state.copy(ingredientLines = lines)
        }
    }

    fun onInstructionLineChange(index: Int, value: String) {
        _uiState.update { state ->
            val lines = state.instructionLines.toMutableList()
            if (index < lines.size) lines[index] = value
            state.copy(instructionLines = lines)
        }
    }

    fun addInstructionLine() {
        _uiState.update { state ->
            state.copy(instructionLines = state.instructionLines + "")
        }
    }

    fun removeInstructionLine(index: Int) {
        _uiState.update { state ->
            val lines = state.instructionLines.toMutableList()
            if (lines.size > 1) {
                lines.removeAt(index)
            } else {
                lines[0] = ""
            }
            state.copy(instructionLines = lines)
        }
    }

    fun onToggleTag(tag: Tag) {
        _uiState.update { state ->
            val updated = state.selectedTagIds.toMutableSet()
            if (tag.id in updated) updated.remove(tag.id) else updated.add(tag.id)
            state.copy(selectedTagIds = updated)
        }
    }

    fun createAndSelectTag(name: String) {
        viewModelScope.launch {
            val tag = tagRepository.createTag(name)
            _uiState.update { state ->
                state.copy(selectedTagIds = state.selectedTagIds + tag.id)
            }
        }
    }

    fun onLocalImagePicked(uri: Uri) {
        viewModelScope.launch {
            try {
                val imagesDir = File(this@EditViewModel.context.filesDir, "recipe_images")
                imagesDir.mkdirs()
                val fileName = "recipe_${UUID.randomUUID()}.jpg"
                val destFile = File(imagesDir, fileName)

                this@EditViewModel.context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                _uiState.update { it.copy(localImagePath = destFile.absolutePath) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = context.getString(
                            R.string.error_save_image,
                            e.message ?: e.javaClass.simpleName,
                        )
                    )
                }
            }
        }
    }

    fun saveRecipe() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.error_title_required)) }
            return
        }

        viewModelScope.launch {
            try {
                val ingredients = state.ingredientLines
                    .filter { it.isNotBlank() }
                    .mapIndexed { index, line -> IngredientParser.parse(line, index) }

                val instructions = state.instructionLines.filter { it.isNotBlank() }

                val selectedTags = state.availableTags.filter { it.id in state.selectedTagIds }

                val recipe = Recipe(
                    id = if (state.isEditing) recipeId else 0,
                    title = state.title.trim(),
                    description = state.description.trim(),
                    sourceUrl = state.sourceUrl.trim().ifBlank { null },
                    imageUrl = state.imageUrl,
                    localImagePath = state.localImagePath,
                    servings = state.servingsStr.toIntOrNull() ?: 4,
                    prepTimeMinutes = state.prepTimeStr.toIntOrNull(),
                    cookTimeMinutes = state.cookTimeStr.toIntOrNull(),
                    instructions = instructions,
                    ingredients = ingredients,
                    tags = selectedTags,
                )

                val savedId = recipeRepository.saveRecipe(recipe)
                _uiState.update { it.copy(savedRecipeId = savedId) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = context.getString(
                            R.string.error_save,
                            e.message ?: e.javaClass.simpleName,
                        )
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
