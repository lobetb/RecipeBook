package com.blobet.recipebook.ui.shopping_list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.blobet.recipebook.R
import com.blobet.recipebook.domain.model.ShoppingListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShoppingListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shopping_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showClearAllDialog = true },
                        enabled = uiState.items.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.shopping_list_clear_all))
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.shopping_list_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        ShoppingItemRow(
                            item = item,
                            onCheckedChange = { checked ->
                                viewModel.setChecked(item.id, checked)
                            },
                            onDelete = { viewModel.deleteItem(item.id) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(stringResource(R.string.shopping_list_clear_all_title)) },
            text = { Text(stringResource(R.string.shopping_list_clear_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllDialog = false
                    viewModel.clearAll()
                }) {
                    Text(stringResource(R.string.shopping_list_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingListItem,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onDelete) {
            Text(
                text = "-",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(
            text = buildItemLabel(item),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
            color = if (item.isChecked) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )

        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun buildItemLabel(item: ShoppingListItem): String {
    val parts = mutableListOf<String>()
    item.quantity?.let { qty ->
        val qtyText = formatQuantity(qty)
        parts.add(qtyText)
    }
    item.unit?.trim()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    parts.add(item.name)
    return parts.joinToString(" ")
}

private fun formatQuantity(value: Double): String {
    val rounded = when {
        value >= 10 -> kotlin.math.round(value * 10.0) / 10.0
        else -> kotlin.math.round(value * 100.0) / 100.0
    }
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}
