package com.blobet.recipebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blobet.recipebook.R
import com.blobet.recipebook.domain.model.Tag

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagChips(
    tags: List<Tag>,
    modifier: Modifier = Modifier,
    onTagClick: ((Tag) -> Unit)? = null,
    onRemoveTag: ((Tag) -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            if (onRemoveTag != null) {
                InputChip(
                    selected = false,
                    onClick = { onTagClick?.invoke(tag) },
                    label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onRemoveTag(tag) },
                            modifier = Modifier.size(16.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.remove_tag, tag.name),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    },
                    colors = InputChipDefaults.inputChipColors(),
                )
            } else {
                AssistChip(
                    onClick = { onTagClick?.invoke(tag) },
                    label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}
