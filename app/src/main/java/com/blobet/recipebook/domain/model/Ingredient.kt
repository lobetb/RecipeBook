package com.blobet.recipebook.domain.model

import kotlin.math.abs
import java.util.Locale

data class Ingredient(
    val id: Long = 0,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val orderIndex: Int = 0,
) {
    fun scaled(originalServings: Int, desiredServings: Int): Ingredient {
        if (quantity == null || originalServings <= 0) return this
        val factor = desiredServings.toDouble() / originalServings.toDouble()
        return copy(quantity = quantity * factor)
    }

    fun displayQuantity(): String {
        val qty = quantity ?: return ""
        // Display as fraction-friendly format
        return when {
            qty == qty.toLong().toDouble() -> qty.toLong().toString()
            else -> quantityToFractionString(qty) ?: String.format(Locale.US, "%.2f", qty).trimEnd('0').trimEnd('.')
        }
    }

    private fun quantityToFractionString(value: Double): String? {
        val whole = value.toInt()
        val fraction = value - whole
        if (abs(fraction) < 0.02) return null

        val candidates = listOf(
            2 to 1, // 1/2
            3 to 1, // 1/3
            3 to 2, // 2/3
            4 to 1, // 1/4
            4 to 3, // 3/4
            8 to 1, // 1/8
            8 to 3, // 3/8
            8 to 5, // 5/8
            8 to 7, // 7/8
        )

        val best = candidates.minByOrNull { (den, num) -> abs(fraction - (num.toDouble() / den.toDouble())) }
            ?: return null
        val bestValue = best.second.toDouble() / best.first.toDouble()
        if (abs(fraction - bestValue) > 0.04) return null

        val fracText = "${best.second}/${best.first}"
        return if (whole > 0) "$whole $fracText" else fracText
    }

    fun scaleFactor(originalServings: Int, desiredServings: Int): Double {
        if (originalServings <= 0) return 1.0
        return desiredServings.toDouble() / originalServings.toDouble()
    }

    fun scaledDisplayText(
        originalServings: Int,
        desiredServings: Int,
        unitSystem: UnitSystem,
    ): String {
        val scaledIngredient = scaled(originalServings, desiredServings)
        val converted = IngredientConversion.convertIngredient(scaledIngredient, unitSystem)
        val parts = mutableListOf<String>()
        val qtyStr = converted.displayQuantity()
        if (qtyStr.isNotEmpty()) parts.add(qtyStr)
        if (!converted.unit.isNullOrBlank()) parts.add(converted.unit)

        val convertedName = IngredientConversion.convertTemperatureText(
            text = converted.name,
            system = unitSystem,
        )
        parts.add(convertedName)
        return parts.joinToString(" ")
    }

    fun displayText(): String {
        val parts = mutableListOf<String>()
        val qtyStr = displayQuantity()
        if (qtyStr.isNotEmpty()) parts.add(qtyStr)
        if (!unit.isNullOrBlank()) parts.add(unit)
        parts.add(name)
        return parts.joinToString(" ")
    }
}
