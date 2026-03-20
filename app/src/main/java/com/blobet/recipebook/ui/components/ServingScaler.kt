package com.blobet.recipebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blobet.recipebook.R

@Composable
fun ServingScaler(
    originalServings: Int,
    currentServings: Int,
    onServingsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${stringResource(R.string.servings)}: ",
            style = MaterialTheme.typography.titleSmall,
        )

        OutlinedIconButton(
            onClick = { if (currentServings > 1) onServingsChange(currentServings - 1) },
            enabled = currentServings > 1,
        ) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease_servings))
        }

        Text(
            text = "$currentServings",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        OutlinedIconButton(
            onClick = { onServingsChange(currentServings + 1) },
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase_servings))
        }

        if (currentServings != originalServings) {
            Text(
                text = stringResource(R.string.servings_original, originalServings),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
