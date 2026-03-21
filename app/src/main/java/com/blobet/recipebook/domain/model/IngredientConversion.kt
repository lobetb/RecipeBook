package com.blobet.recipebook.domain.model

data class IngredientDisplay(
    val quantityText: String,
    val unitText: String,
    val nameText: String,
)

object IngredientConversion {

    private val metricWeightToGrams = mapOf(
        "g" to 1.0,
        "gram" to 1.0,
        "grams" to 1.0,
        "kg" to 1000.0,
        "kilogram" to 1000.0,
        "kilograms" to 1000.0,
    )

    private val metricVolumeToMilliliters = mapOf(
        "ml" to 1.0,
        "milliliter" to 1.0,
        "milliliters" to 1.0,
        "l" to 1000.0,
        "liter" to 1000.0,
        "liters" to 1000.0,
    )

    private val imperialWeightToGrams = mapOf(
        "oz" to 28.349523125,
        "ounce" to 28.349523125,
        "ounces" to 28.349523125,
        "lb" to 453.59237,
        "lbs" to 453.59237,
        "pound" to 453.59237,
        "pounds" to 453.59237,
    )

    private val imperialVolumeToMilliliters = mapOf(
        "tsp" to 4.92892159375,
        "teaspoon" to 4.92892159375,
        "teaspoons" to 4.92892159375,
        "tbsp" to 14.78676478125,
        "tablespoon" to 14.78676478125,
        "tablespoons" to 14.78676478125,
        "cup" to 236.5882365,
        "cups" to 236.5882365,
        "pt" to 473.176473,
        "pint" to 473.176473,
        "pints" to 473.176473,
        "qt" to 946.352946,
        "quart" to 946.352946,
        "quarts" to 946.352946,
        "gal" to 3785.411784,
        "gallon" to 3785.411784,
        "gallons" to 3785.411784,
    )

    fun toDisplay(ingredient: Ingredient, system: UnitSystem): IngredientDisplay {
        val converted = convertIngredient(ingredient, system)
        return IngredientDisplay(
            quantityText = converted.displayQuantity(),
            unitText = converted.unit.orEmpty(),
            nameText = converted.name,
        )
    }

    fun convertIngredient(ingredient: Ingredient, system: UnitSystem): Ingredient {
        if (system == UnitSystem.ORIGINAL) return ingredient

        val quantity = ingredient.quantity ?: return ingredient
        val unit = ingredient.unit?.trim()?.lowercase().orEmpty()
        if (unit.isBlank()) return ingredient

        val converted = when (system) {
            UnitSystem.METRIC -> convertToMetric(quantity, unit)
            UnitSystem.IMPERIAL -> convertToImperial(quantity, unit)
            UnitSystem.ORIGINAL -> null
        } ?: return ingredient

        return ingredient.copy(quantity = converted.first, unit = converted.second)
    }

    private fun convertToMetric(quantity: Double, unit: String): Pair<Double, String>? {
        val grams = imperialWeightToGrams[unit]?.let { quantity * it }
            ?: metricWeightToGrams[unit]?.let { quantity * it }
        if (grams != null) {
            val (q, u) = if (grams >= 1000.0) grams / 1000.0 to "kg" else grams to "g"
            return q to u
        }

        val ml = imperialVolumeToMilliliters[unit]?.let { quantity * it }
            ?: metricVolumeToMilliliters[unit]?.let { quantity * it }
        if (ml != null) {
            val (q, u) = if (ml >= 1000.0) ml / 1000.0 to "l" else ml to "ml"
            return q to u
        }

        return null
    }

    private fun convertToImperial(quantity: Double, unit: String): Pair<Double, String>? {
        val grams = metricWeightToGrams[unit]?.let { quantity * it }
            ?: imperialWeightToGrams[unit]?.let { quantity * it }
        if (grams != null) {
            val pounds = grams / 453.59237
            return if (pounds >= 1.0) {
                pounds to "lb"
            } else {
                (grams / 28.349523125) to "oz"
            }
        }

        val ml = metricVolumeToMilliliters[unit]?.let { quantity * it }
            ?: imperialVolumeToMilliliters[unit]?.let { quantity * it }
        if (ml != null) {
            val cups = ml / 236.5882365
            return when {
                cups >= 4.0 -> cups / 4.0 to "qt"
                cups >= 1.0 -> cups to "cup"
                else -> (ml / 14.78676478125) to "tbsp"
            }
        }

        return null
    }

}
