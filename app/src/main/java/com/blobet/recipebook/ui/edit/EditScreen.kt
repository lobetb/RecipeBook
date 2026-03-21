package com.blobet.recipebook.ui.edit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.blobet.recipebook.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun EditScreen(
    onNavigateBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: EditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    var showNewTagDialog by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onLocalImagePicked(it) }
    }

    LaunchedEffect(uiState.savedRecipeId) {
        uiState.savedRecipeId?.let { id ->
            onSaved(id)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isEditing) {
                            stringResource(R.string.edit_recipe_title)
                        } else {
                            stringResource(R.string.new_recipe_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveRecipe() },
                        enabled = uiState.title.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save_recipe_cd))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Photo
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { imagePickerLauncher.launch("image/*") },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val imageUri = uiState.localImagePath ?: uiState.imageUrl
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = stringResource(R.string.recipe_photo),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.tap_to_add_photo),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            // Title
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.field_title_required)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            // Description
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.field_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            // Time and Servings row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = uiState.prepTimeStr,
                    onValueChange = viewModel::onPrepTimeChange,
                    label = { Text(stringResource(R.string.field_prep_minutes)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = uiState.cookTimeStr,
                    onValueChange = viewModel::onCookTimeChange,
                    label = { Text(stringResource(R.string.field_cook_minutes)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = uiState.servingsStr,
                    onValueChange = viewModel::onServingsChange,
                    label = { Text(stringResource(R.string.field_servings)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            // Source URL
            OutlinedTextField(
                value = uiState.sourceUrl,
                onValueChange = viewModel::onSourceUrlChange,
                label = { Text(stringResource(R.string.field_source_url_optional)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Divider()

            // Ingredients section
            Text(
                text = stringResource(R.string.ingredients),
                style = MaterialTheme.typography.titleMedium,
            )

            uiState.ingredientLines.forEachIndexed { index, line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = line,
                        onValueChange = { viewModel.onIngredientLineChange(index, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.ingredient_example)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    IconButton(onClick = { viewModel.removeIngredientLine(index) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.remove_ingredient),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { viewModel.addIngredientLine() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_ingredient))
            }

            Divider()

            // Instructions section
            Text(
                text = stringResource(R.string.instructions),
                style = MaterialTheme.typography.titleMedium,
            )

            uiState.instructionLines.forEachIndexed { index, line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp, end = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = line,
                        onValueChange = { viewModel.onInstructionLineChange(index, it) },
                        modifier = Modifier.weight(1f),
                        minLines = 2,
                        placeholder = { Text(stringResource(R.string.instruction_step, index + 1)) },
                    )
                    IconButton(onClick = { viewModel.removeInstructionLine(index) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.remove_step),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { viewModel.addInstructionLine() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_step))
            }

            Divider()

            // Tags section
            Text(
                text = stringResource(R.string.tags),
                style = MaterialTheme.typography.titleMedium,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                uiState.availableTags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in uiState.selectedTagIds,
                        onClick = { viewModel.onToggleTag(tag) },
                        label = { Text(tag.name) },
                    )
                }
            }

            OutlinedButton(
                onClick = { showNewTagDialog = true },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.new_tag))
            }

            // Bottom spacer
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // New tag dialog
    if (showNewTagDialog) {
        val builtInTagNames = listOf(
            stringResource(R.string.type_breakfast),
            stringResource(R.string.type_lunch),
            stringResource(R.string.type_dinner),
            stringResource(R.string.type_dessert),
            stringResource(R.string.type_snack),
            stringResource(R.string.type_soup),
            stringResource(R.string.type_salad),
            stringResource(R.string.type_side),
            stringResource(R.string.type_drink),
        )

        val suggestions = remember(newTagName, uiState.availableTags, uiState.selectedTagIds) {
            val query = newTagName.trim()
            if (query.isBlank()) {
                emptyList()
            } else {
                (uiState.availableTags.map { it.name } + builtInTagNames)
                    .distinctBy { it.trim().lowercase() }
                    .filter { name ->
                        name.contains(query, ignoreCase = true)
                    }
                    .sortedBy { name ->
                        val lower = name.lowercase()
                        val q = query.lowercase()
                        when {
                            lower == q -> 0
                            lower.startsWith(q) -> 1
                            else -> 2
                        }
                    }
                    .take(6)
            }
        }

        AlertDialog(
            onDismissRequest = {
                showNewTagDialog = false
                newTagName = ""
            },
            title = { Text(stringResource(R.string.new_tag)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        label = { Text(stringResource(R.string.tag_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (suggestions.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            suggestions.forEach { suggestion ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        viewModel.createAndSelectTag(suggestion)
                                        newTagName = ""
                                        showNewTagDialog = false
                                    },
                                    label = { Text(suggestion) },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            viewModel.createAndSelectTag(newTagName.trim())
                            newTagName = ""
                            showNewTagDialog = false
                        }
                    },
                    enabled = newTagName.isNotBlank(),
                ) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewTagDialog = false
                    newTagName = ""
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
