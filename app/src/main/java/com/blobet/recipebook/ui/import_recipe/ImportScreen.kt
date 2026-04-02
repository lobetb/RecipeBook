package com.blobet.recipebook.ui.import_recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.blobet.recipebook.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ImportScreen(
    onNavigateBack: () -> Unit,
    onRecipeImported: (Long) -> Unit,
    sharedUrl: String = "",
    sharedRecipePayloadJson: String = "",
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val humanVerificationMessage = stringResource(R.string.error_site_human_verification)
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.savedRecipeId) {
        uiState.savedRecipeId?.let { id ->
            onRecipeImported(id)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = if (msg == humanVerificationMessage) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            viewModel.clearError()
        }
    }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl.isNotBlank()) {
            viewModel.importSharedUrl(sharedUrl)
        }
    }

    LaunchedEffect(sharedRecipePayloadJson) {
        if (sharedRecipePayloadJson.isNotBlank()) {
            viewModel.importSharedRecipePayload(sharedRecipePayloadJson)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val duplicateTitle = uiState.duplicateRecipeTitle
            if (uiState.pendingSaveRecipe != null && duplicateTitle != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.cancelSaveDuplicate() },
                    title = { Text(stringResource(R.string.duplicate_recipe_title)) },
                    text = {
                        Text(
                            text = if (uiState.duplicateMatchedByUrl) {
                                stringResource(
                                    R.string.duplicate_recipe_message_url,
                                    duplicateTitle,
                                )
                            } else {
                                stringResource(
                                    R.string.duplicate_recipe_message_title,
                                    duplicateTitle,
                                )
                            }
                        )
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.confirmSaveDuplicate() }) {
                            Text(stringResource(R.string.duplicate_recipe_import_anyway))
                        }
                    },
                    dismissButton = {
                        Button(onClick = { viewModel.cancelSaveDuplicate() }) {
                            Text(stringResource(R.string.duplicate_recipe_cancel))
                        }
                    },
                )
            }

            // URL input
            Text(
                text = stringResource(R.string.import_paste_url),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = uiState.url,
                onValueChange = viewModel::onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.import_url_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Default.Link, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val text = clipboardManager.getText()?.text
                            if (!text.isNullOrBlank()) {
                                viewModel.onUrlChange(text)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = stringResource(R.string.import_paste_clipboard),
                        )
                    }
                },
                singleLine = true,
            )

            OutlinedTextField(
                value = uiState.optionalDescriptionText,
                onValueChange = viewModel::onOptionalDescriptionTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.import_optional_description_label)) },
                placeholder = { Text(stringResource(R.string.import_optional_description_placeholder)) },
                minLines = 4,
                maxLines = 10,
            )

            Button(
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    viewModel.fetchAndParse()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && uiState.url.isNotBlank(),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.import_fetching))
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.import_extract_recipe))
                }
            }

            // Preview section
            val recipe = uiState.parsedRecipe
            if (recipe != null) {
                Divider()

                Text(
                    text = stringResource(R.string.import_preview),
                    style = MaterialTheme.typography.titleLarge,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Image
                        if (recipe.imageUrl != null) {
                            AsyncImage(
                                model = recipe.imageUrl,
                                contentDescription = recipe.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(MaterialTheme.shapes.medium),
                                contentScale = ContentScale.Crop,
                            )
                        }

                        // Title
                        Text(
                            text = recipe.title,
                            style = MaterialTheme.typography.headlineSmall,
                        )

                        // Description
                        if (recipe.description.isNotBlank()) {
                            Text(
                                text = recipe.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                            )
                        }

                        val missingIngredients = recipe.ingredients.isEmpty() && recipe.instructions.isNotEmpty()
                        val missingInstructions = recipe.instructions.isEmpty() && recipe.ingredients.isNotEmpty()
                        if (missingIngredients || missingInstructions) {
                            AssistChip(
                                onClick = { },
                                enabled = false,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (missingIngredients) {
                                            stringResource(R.string.import_partial_recipe_missing_ingredients)
                                        } else {
                                            stringResource(R.string.import_partial_recipe_missing_instructions)
                                        },
                                    )
                                },
                            )
                        }

                        // Time
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            recipe.prepTimeMinutes?.let { prep ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(text = stringResource(R.string.label_prep, "${prep}min"), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            recipe.cookTimeMinutes?.let { cook ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(text = stringResource(R.string.label_cook, "${cook}min"), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Text(
                                stringResource(R.string.label_servings, recipe.servings),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }

                        // Ingredients count
                        if (recipe.ingredients.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.label_ingredients_count, recipe.ingredients.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            recipe.ingredients.take(5).forEach { ingredient ->
                                Text(
                                    text = "  - ${ingredient.displayText()}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (recipe.ingredients.size > 5) {
                                Text(
                                    text = "  ${stringResource(R.string.label_and_more, recipe.ingredients.size - 5)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Instructions count
                        if (recipe.instructions.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.label_instructions_count, recipe.instructions.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { viewModel.saveRecipe(recipe) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.save_recipe))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
