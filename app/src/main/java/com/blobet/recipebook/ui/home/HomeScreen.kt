package com.blobet.recipebook.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.blobet.recipebook.R
import com.blobet.recipebook.ui.components.RecipeCard
import com.blobet.recipebook.ui.components.SearchBar
import com.blobet.recipebook.util.BugReportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onRecipeClick: (Long) -> Unit,
    onAddRecipeClick: () -> Unit,
    onImportRecipeClick: () -> Unit,
    onImportRecipeFileClick: (String) -> Unit,
    onShoppingListClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showHandsOnTutorial: Boolean,
    onHandsOnTutorialDone: () -> Unit,
    isKeepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showFilters by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var showActionsMenu by remember { mutableStateOf(false) }
    var tutorialStep by remember { mutableStateOf(-1) }
    val scope = rememberCoroutineScope()
    val showTutorialOverlay = showHandsOnTutorial && !showActionsMenu && !showFilters
    val tutorialPulse by rememberInfiniteTransition(label = "tutorialPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tutorialPulseValue",
    )

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val payload = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                if (payload.isNullOrBlank()) {
                    Toast.makeText(context, context.getString(R.string.error_invalid_recipe_file), Toast.LENGTH_SHORT).show()
                } else {
                    onImportRecipeFileClick(payload)
                }
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.error_invalid_recipe_file), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { viewModel.onToggleFavorites() }) {
                        Icon(
                            imageVector = if (uiState.showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.home_toggle_favorites),
                            tint = if (uiState.showFavoritesOnly) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.home_filter_recipes),
                        )
                    }
                    val shoppingMod = if (showTutorialOverlay && tutorialStep == 1) {
                        Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f * tutorialPulse), CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + (0.55f * tutorialPulse)), CircleShape)
                    } else Modifier
                    IconButton(
                        onClick = onShoppingListClick,
                        modifier = shoppingMod,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = stringResource(R.string.home_shopping_list),
                        )
                    }
                    val keepScreenModifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isKeepScreenOn) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                            shape = CircleShape,
                        )
                    val lockMod = if (showTutorialOverlay && tutorialStep == 2) {
                        keepScreenModifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f * tutorialPulse), CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + (0.55f * tutorialPulse)), CircleShape)
                    } else keepScreenModifier
                    IconButton(onClick = onToggleKeepScreenOn, modifier = lockMod) {
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
                        val settingsMod = if (showTutorialOverlay && tutorialStep == 3) {
                            Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f * tutorialPulse), CircleShape)
                                .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + (0.55f * tutorialPulse)), CircleShape)
                        } else Modifier
                        IconButton(onClick = { showActionsMenu = true }, modifier = settingsMod) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_actions),
                            )
                        }
                        DropdownMenu(
                            expanded = showActionsMenu,
                            onDismissRequest = { showActionsMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_sort_recipes)) },
                                onClick = {},
                                enabled = false,
                            )
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.sortMode == mode) {
                                                "${stringResource(mode.labelRes)} \u2713"
                                            } else {
                                                stringResource(mode.labelRes)
                                            }
                                        )
                                    },
                                    onClick = {
                                        viewModel.onSortModeChange(mode)
                                        showActionsMenu = false
                                    },
                                )
                            }

                            Divider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_layout_options)) },
                                onClick = {},
                                enabled = false,
                            )
                            LayoutMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.layoutMode == mode) {
                                                "${stringResource(mode.labelRes)} \u2713"
                                            } else {
                                                stringResource(mode.labelRes)
                                            }
                                        )
                                    },
                                    onClick = {
                                        viewModel.onLayoutModeChange(mode)
                                        showActionsMenu = false
                                    },
                                )
                            }

                            Divider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_settings)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                },
                                onClick = {
                                    showActionsMenu = false
                                    onSettingsClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.report_bug)) },
                                onClick = {
                                    showActionsMenu = false
                                    val opened = BugReportUtils.openBugReportEmail(context)
                                    if (!opened) {
                                        Toast.makeText(context, context.getString(R.string.report_bug_no_email_app), Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(visible = fabExpanded) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                fabExpanded = false
                                onImportRecipeClick()
                            },
                            icon = { Icon(Icons.Default.Link, contentDescription = null) },
                            text = { Text(stringResource(R.string.home_import_from_url)) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                        ExtendedFloatingActionButton(
                            onClick = {
                                fabExpanded = false
                                importFileLauncher.launch(
                                    arrayOf(
                                        "application/vnd.blobet.recipebook+json",
                                        "application/json",
                                        "*/*",
                                    )
                                )
                            },
                            icon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                            text = { Text(stringResource(R.string.home_import_recipebook_file)) },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                        ExtendedFloatingActionButton(
                            onClick = {
                                fabExpanded = false
                                onAddRecipeClick()
                            },
                            icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                            text = { Text(stringResource(R.string.home_create_manually)) },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    }
                }
                val addMod = if (showTutorialOverlay && tutorialStep == 0) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f * tutorialPulse), CircleShape)
                        .border(4.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + (0.55f * tutorialPulse)), CircleShape)
                } else Modifier
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    modifier = addMod,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_add_recipe))
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                )

                AnimatedVisibility(visible = showFilters) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RecipeTypeFilter.entries.forEach { type ->
                            FilterChip(
                                selected = type in uiState.selectedRecipeTypes,
                                onClick = { viewModel.onToggleRecipeType(type) },
                                label = { Text(stringResource(type.labelRes)) },
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showFilters && uiState.allTags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        uiState.allTags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in uiState.selectedTagIds,
                                onClick = { viewModel.onToggleTag(tag.id) },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                }

                if (uiState.recipes.isEmpty() && !uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (
                                    uiState.searchQuery.isNotBlank() ||
                                    uiState.selectedTagIds.isNotEmpty() ||
                                    uiState.selectedRecipeTypes.isNotEmpty() ||
                                    uiState.showFavoritesOnly
                                ) stringResource(R.string.home_no_recipes_found)
                                else stringResource(R.string.home_no_recipes_yet),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (
                                    uiState.searchQuery.isBlank() &&
                                    uiState.selectedTagIds.isEmpty() &&
                                    uiState.selectedRecipeTypes.isEmpty() &&
                                    !uiState.showFavoritesOnly
                                ) stringResource(R.string.home_add_first_recipe)
                                else stringResource(R.string.home_adjust_filters),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                } else {
                    when (uiState.layoutMode) {
                        LayoutMode.LARGE -> {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                lazyItems(items = uiState.recipes, key = { it.id }) { recipe ->
                                    RecipeCard(
                                        recipe = recipe,
                                        onClick = { onRecipeClick(recipe.id) },
                                        onFavoriteClick = {
                                            viewModel.onToggleRecipeFavorite(recipe.id, recipe.isFavorite)
                                        },
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(80.dp)) }
                            }
                        }

                        LayoutMode.SMALL -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                gridItems(items = uiState.recipes, key = { it.id }) { recipe ->
                                    RecipeCard(
                                        recipe = recipe,
                                        onClick = { onRecipeClick(recipe.id) },
                                        onFavoriteClick = {
                                            viewModel.onToggleRecipeFavorite(recipe.id, recipe.isFavorite)
                                        },
                                    )
                                }
                                item(span = { GridItemSpan(2) }) {
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }

                        LayoutMode.LIST -> {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                lazyItems(items = uiState.recipes, key = { it.id }) { recipe ->
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                text = recipe.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        supportingContent = {
                                            if (recipe.description.isNotBlank()) {
                                                Text(
                                                    text = recipe.description,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        },
                                        leadingContent = {
                                            Icon(Icons.Default.MenuBook, contentDescription = null)
                                        },
                                        trailingContent = {
                                            IconButton(onClick = {
                                                viewModel.onToggleRecipeFavorite(recipe.id, recipe.isFavorite)
                                            }) {
                                                Icon(
                                                    imageVector = if (recipe.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    contentDescription = null,
                                                    tint = if (recipe.isFavorite) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp)
                                            .clickable { onRecipeClick(recipe.id) },
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(80.dp)) }
                            }
                        }
                    }
                }
            }

            if (showTutorialOverlay) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                    val tutorialText = when (tutorialStep) {
                        -1 -> stringResource(R.string.tutorial_overlay_welcome)
                        0 -> stringResource(R.string.tutorial_overlay_add)
                        1 -> stringResource(R.string.tutorial_overlay_shopping)
                        2 -> stringResource(R.string.tutorial_overlay_lock)
                        else -> stringResource(R.string.tutorial_overlay_settings)
                    }

                    val bubbleAlignment = Alignment.Center
                    val bubbleModifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(0.88f)

                    Column(
                        modifier = Modifier
                            .align(bubbleAlignment)
                            .then(bubbleModifier)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = tutorialText,
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            androidx.compose.material3.TextButton(onClick = onHandsOnTutorialDone) {
                                Text(stringResource(R.string.tutorial_skip))
                            }
                            Button(
                                onClick = {
                                    if (tutorialStep >= 3) onHandsOnTutorialDone()
                                    else tutorialStep += 1
                                }
                            ) {
                                Text(
                                    if (tutorialStep >= 3) stringResource(R.string.tutorial_done)
                                    else stringResource(R.string.tutorial_next)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutIndicatorIcon(
    layoutMode: LayoutMode,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurface

    when (layoutMode) {
        LayoutMode.LARGE -> {
            Box(
                modifier = modifier
                    .background(color),
            )
        }

        LayoutMode.SMALL -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(color))
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(color))
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(color))
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(color))
                }
            }
        }

        LayoutMode.LIST -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(color),
                    )
                }
            }
        }
    }
}
