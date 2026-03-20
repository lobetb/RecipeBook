package com.blobet.recipebook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blobet.recipebook.domain.model.Ingredient

@Composable
fun IngredientList(
    ingredients: List<Ingredient>,
    originalServings: Int,
    currentServings: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ingredients.forEach { ingredient ->
            val scaled = ingredient.scaled(originalServings, currentServings)
            IngredientRow(scaled)
            Divider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun IngredientRow(ingredient: Ingredient) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        val qtyText = ingredient.displayQuantity()
        val unitText = ingredient.unit ?: ""

        if (qtyText.isNotBlank()) {
            Text(
                text = "$qtyText ",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (unitText.isNotBlank()) {
            Text(
                text = "$unitText ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = ingredient.name,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
