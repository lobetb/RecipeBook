package com.blobet.recipebook.ui.detail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.blobet.recipebook.R
import com.blobet.recipebook.domain.model.IngredientConversion
import com.blobet.recipebook.ui.components.IngredientList
import com.blobet.recipebook.ui.components.ServingScaler
import com.blobet.recipebook.ui.components.TagChips
import com.blobet.recipebook.ui.components.UnitSystemToggle
import com.blobet.recipebook.util.ShareUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    onNavigateBack: () -> Unit,
    onEditClick: (Long) -> Unit,
    isKeepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showNewTagDialog by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var showActionsMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.recipe?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val recipe = uiState.recipe
                    if (recipe != null) {
                        IconButton(
                            onClick = { viewModel.onCycleTextScale() }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FormatSize,
                                contentDescription = stringResource(R.string.detail_text_size),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = when {
                                uiState.textScale < 0.95f -> "S"
                                uiState.textScale < 1.15f -> "M"
                                else -> "L"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        IconButton(onClick = { viewModel.onToggleFavorite() }) {
                            Icon(
                                imageVector = if (recipe.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.detail_toggle_favorite),
                                tint = if (recipe.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        val keepScreenModifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (isKeepScreenOn) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                shape = CircleShape,
                            )
                        IconButton(onClick = onToggleKeepScreenOn, modifier = keepScreenModifier) {
                            Icon(
                                imageVector = Icons.Default.ScreenLockPortrait,
                                contentDescription = if (isKeepScreenOn) {
                                    stringResource(R.string.keep_screen_on_disable)
                                } else {
                                    stringResource(R.string.keep_screen_on_enable)
                                },
                                tint = if (isKeepScreenOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { showActionsMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
                            }
                            DropdownMenu(
                                expanded = showActionsMenu,
                                onDismissRequest = { showActionsMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_recipe)) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showActionsMenu = false
                                        showShareDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_recipe_title)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showActionsMenu = false
                                        onEditClick(recipe.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showActionsMenu = false
                                        showDeleteDialog = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val recipe = uiState.recipe

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (recipe == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.recipe_not_found))
            }
        } else {
            val bodyLargeScaled: TextStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * uiState.textScale,
            )
            val bodyMediumScaled: TextStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize * uiState.textScale,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Hero image
                val imageModel = recipe.displayImage
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = recipe.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestaurantMenu,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Title
                    Text(
                        text = recipe.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * uiState.textScale,
                        ),
                    )

                    // Description
                    if (recipe.description.isNotBlank()) {
                        Text(
                            text = recipe.description,
                            style = bodyLargeScaled,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Time info
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        recipe.prepTimeMinutes?.let { prep ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.label_prep, formatTime(prep)),
                                    style = bodyMediumScaled,
                                )
                            }
                        }
                        recipe.cookTimeMinutes?.let { cook ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    text = stringResource(R.string.label_cook, formatTime(cook)),
                                    style = bodyMediumScaled,
                                )
                            }
                        }
                    }

                    // Source URL
                    if (!recipe.sourceUrl.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            TextButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(recipe.sourceUrl))
                                    context.startActivity(intent)
                                }
                            ) {
                                Text(
                                    stringResource(R.string.view_original_recipe),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.tags),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    if (recipe.tags.isNotEmpty()) {
                        TagChips(
                            tags = recipe.tags,
                            onRemoveTag = viewModel::onRemoveTag,
                        )
                    }

                    OutlinedButton(
                        onClick = { showNewTagDialog = true },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.new_tag))
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Ingredients
                    if (recipe.ingredients.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.ingredients),
                            style = MaterialTheme.typography.titleLarge,
                        )

                        ServingScaler(
                            originalServings = recipe.servings,
                            currentServings = uiState.currentServings,
                            onServingsChange = viewModel::onServingsChange,
                        )

                        UnitSystemToggle(
                            selected = uiState.unitSystem,
                            onSelected = viewModel::onUnitSystemChange,
                        )

                        IngredientList(
                            ingredients = recipe.ingredients,
                            originalServings = recipe.servings,
                            currentServings = uiState.currentServings,
                            unitSystem = uiState.unitSystem,
                            textScale = uiState.textScale,
                            addedIngredientKeys = uiState.addedIngredientKeys,
                            onAddIngredient = viewModel::onAddIngredientToShoppingList,
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    // Instructions
                    if (recipe.instructions.isNotEmpty()) {
                        val sourceSystem = IngredientConversion.inferSourceUnitSystem(recipe.ingredients)
                        val instructionTempSourceSystem = IngredientConversion.inferInstructionTemperatureSource(
                            instructions = recipe.instructions,
                            ingredientSourceSystem = sourceSystem,
                        )

                        Text(
                            text = stringResource(R.string.instructions),
                            style = MaterialTheme.typography.titleLarge,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        recipe.instructions.forEachIndexed { index, step ->
                            if (step.startsWith("---") && step.endsWith("---")) {
                                // Section header
                                Text(
                                    text = step.removePrefix("---").removeSuffix("---").trim(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = bodyLargeScaled,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                    Text(
                                        text = stripLeadingStepNumber(
                                            IngredientConversion.convertInstructionText(
                                                text = step,
                                                targetSystem = uiState.unitSystem,
                                                sourceSystem = instructionTempSourceSystem,
                                                originalServings = recipe.servings,
                                                desiredServings = uiState.currentServings,
                                            )
                                        ),
                                        style = bodyLargeScaled,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_recipe_title)) },
            text = { Text(stringResource(R.string.delete_recipe_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.onDeleteRecipe()
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showShareDialog) {
        val recipe = uiState.recipe
        if (recipe != null) {
            AlertDialog(
                onDismissRequest = { showShareDialog = false },
                title = { Text(stringResource(R.string.share_recipe)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                showShareDialog = false
                                ShareUtils.shareRecipe(context, recipe)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.share_option_full_text),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                            )
                        }

                        TextButton(
                            onClick = {
                                showShareDialog = false
                                val ok = ShareUtils.shareRecipeUrl(context, recipe)
                                if (!ok) {
                                    Toast.makeText(context, context.getString(R.string.share_url_missing), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.share_option_url_only),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                            )
                        }

                        TextButton(
                            onClick = {
                                showShareDialog = false
                                val ok = ShareUtils.shareRecipeAsFile(context, recipe)
                                if (!ok) {
                                    Toast.makeText(context, context.getString(R.string.share_file_failed), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.share_option_recipe_file),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showShareDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    if (showNewTagDialog) {
        val recipe = uiState.recipe
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

        val suggestions = remember(newTagName, uiState.availableTags, recipe?.tags) {
            val query = newTagName.trim()
            if (query.isBlank() || recipe == null) {
                emptyList()
            } else {
                val existingTagKeys = recipe.tags.map { it.name.trim().lowercase() }.toSet()
                (uiState.availableTags.map { it.name } + builtInTagNames)
                    .distinctBy { it.trim().lowercase() }
                    .filter { name ->
                        name.contains(query, ignoreCase = true) &&
                            name.trim().lowercase() !in existingTagKeys
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
                                        viewModel.createAndAddTag(suggestion)
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
                        viewModel.createAndAddTag(newTagName)
                        newTagName = ""
                        showNewTagDialog = false
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

private fun formatTime(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes} min"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}min"
    }
}

private fun stripLeadingStepNumber(text: String): String {
    return text
        .replace(Regex("""^\s*(?:step|étape|etape|paso)\s*\d+\s*[:.)-]?\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^\s*\d+\s*[:.)-]\s*"""), "")
        .trim()
}
