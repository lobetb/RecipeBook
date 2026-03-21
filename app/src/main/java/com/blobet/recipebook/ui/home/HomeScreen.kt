package com.blobet.recipebook.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.blobet.recipebook.R
import com.blobet.recipebook.ui.components.RecipeCard
import com.blobet.recipebook.ui.components.SearchBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onRecipeClick: (Long) -> Unit,
    onAddRecipeClick: () -> Unit,
    onImportRecipeClick: () -> Unit,
    onShoppingListClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showFilters by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showLayoutMenu by remember { mutableStateOf(false) }

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
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = stringResource(R.string.home_sort_recipes),
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(mode.labelRes)) },
                                    onClick = {
                                        viewModel.onSortModeChange(mode)
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showLayoutMenu = true }) {
                            LayoutIndicatorIcon(
                                layoutMode = uiState.layoutMode,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showLayoutMenu,
                            onDismissRequest = { showLayoutMenu = false },
                        ) {
                            LayoutMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(mode.labelRes)) },
                                    onClick = {
                                        viewModel.onLayoutModeChange(mode)
                                        showLayoutMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onShoppingListClick) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = stringResource(R.string.home_shopping_list),
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.home_settings),
                        )
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
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_add_recipe))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
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

            // Tag filter chips
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
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
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
                            ) {
                                stringResource(R.string.home_no_recipes_found)
                            } else {
                                stringResource(R.string.home_no_recipes_yet)
                            },
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
                            ) {
                                stringResource(R.string.home_add_first_recipe)
                            } else {
                                stringResource(R.string.home_adjust_filters)
                            },
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
                            lazyItems(
                                items = uiState.recipes,
                                key = { it.id }
                            ) { recipe ->
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
                            gridItems(
                                items = uiState.recipes,
                                key = { it.id }
                            ) { recipe ->
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
                            lazyItems(
                                items = uiState.recipes,
                                key = { it.id }
                            ) { recipe ->
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
                                                tint = if (recipe.isFavorite) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
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
