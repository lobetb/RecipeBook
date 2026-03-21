package com.blobet.recipebook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blobet.recipebook.R
import com.blobet.recipebook.domain.model.IngredientConversion
import com.blobet.recipebook.domain.model.UnitSystem
import com.blobet.recipebook.domain.model.Ingredient

@Composable
fun IngredientList(
    ingredients: List<Ingredient>,
    originalServings: Int,
    currentServings: Int,
    unitSystem: UnitSystem = UnitSystem.ORIGINAL,
    addedIngredientKeys: Set<String> = emptySet(),
    onAddIngredient: ((Ingredient) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ingredients.forEach { ingredient ->
            val scaled = ingredient.scaled(originalServings, currentServings)
            val ingredientKey = "${ingredient.orderIndex}:${ingredient.name.lowercase()}"
            IngredientRow(
                ingredient = scaled,
                unitSystem = unitSystem,
                isAdded = ingredientKey in addedIngredientKeys,
                onAdd = if (onAddIngredient != null) ({ onAddIngredient(scaled) }) else null,
            )
            Divider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun IngredientRow(
    ingredient: Ingredient,
    unitSystem: UnitSystem,
    isAdded: Boolean,
    onAdd: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        val display = IngredientConversion.toDisplay(ingredient, unitSystem)
        val qtyText = display.quantityText
        val unitText = display.unitText

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
            text = display.nameText,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (onAdd != null) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onAdd, enabled = !isAdded) {
                Icon(
                    imageVector = Icons.Default.AddShoppingCart,
                    contentDescription = stringResource(R.string.add_to_shopping_list),
                    tint = if (isAdded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
