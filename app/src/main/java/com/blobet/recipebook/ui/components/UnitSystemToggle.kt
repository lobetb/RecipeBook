package com.blobet.recipebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blobet.recipebook.R
import com.blobet.recipebook.domain.model.UnitSystem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitSystemToggle(
    selected: UnitSystem,
    onSelected: (UnitSystem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == UnitSystem.ORIGINAL,
            onClick = { onSelected(UnitSystem.ORIGINAL) },
            label = { Text(stringResource(R.string.units_original)) },
        )
        FilterChip(
            selected = selected == UnitSystem.METRIC,
            onClick = { onSelected(UnitSystem.METRIC) },
            label = { Text(stringResource(R.string.units_metric)) },
        )
        FilterChip(
            selected = selected == UnitSystem.IMPERIAL,
            onClick = { onSelected(UnitSystem.IMPERIAL) },
            label = { Text(stringResource(R.string.units_imperial)) },
        )
    }
}
