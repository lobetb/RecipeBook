package com.blobet.recipebook.data.parser

import com.blobet.recipebook.domain.model.Ingredient

/**
 * Parses raw ingredient strings like "2 cups all-purpose flour" into
 * structured Ingredient objects with quantity, unit, and name.
 */
object IngredientParser {

    private val UNITS = setOf(
        "cup", "cups", "tablespoon", "tablespoons", "tbsp", "tbs",
        "teaspoon", "teaspoons", "tsp", "ounce", "ounces", "oz",
        "pound", "pounds", "lb", "lbs", "gram", "grams", "g",
        "kilogram", "kilograms", "kg", "milliliter", "milliliters", "ml",
        "liter", "liters", "l", "gallon", "gallons", "gal",
        "quart", "quarts", "qt", "pint", "pints", "pt",
        "pinch", "pinches", "dash", "dashes",
        "slice", "slices", "piece", "pieces",
        "clove", "cloves", "head", "heads",
        "bunch", "bunches", "sprig", "sprigs",
        "can", "cans", "jar", "jars", "package", "packages",
        "stick", "sticks", "bag", "bags",
        // Spanish
        "cda", "cdas", "cdta", "cdita", "cditas",
        "cucharada", "cucharadas", "cucharadita", "cucharaditas",
        "onza", "onzas", "libra", "libras",
        "gramo", "gramos", "kilogramo", "kilogramos",
        "litro", "litros", "mililitro", "mililitros",
        "pizca", "pizcas", "rebanada", "rebanadas",
        "diente", "dientes", "rama", "ramas",
        "lata", "latas", "frasco", "frascos", "paquete", "paquetes",
    )

    // Matches fractions like "1/2", "3/4"
    private val FRACTION_REGEX = Regex("""(\d+)\s*/\s*(\d+)""")

    // Matches mixed numbers like "1 1/2" or simple numbers like "2" or "2.5"
    private val QUANTITY_REGEX = Regex("""^(\d+\s+\d+\s*/\s*\d+|\d+\s*/\s*\d+|\d+\.?\d*)\s*""")

    private val leadingNoiseRegex = Regex("""^\s*[-•●▪◦·*]+\s*""")

    fun parse(raw: String, index: Int = 0): Ingredient {
        val trimmed = normalizeIngredientRaw(raw).trim()
        if (trimmed.isBlank()) {
            return Ingredient(name = "", orderIndex = index)
        }

        var remaining = trimmed
        var quantity: Double? = null

        // Try to extract quantity
        val qtyMatch = QUANTITY_REGEX.find(remaining)
        if (qtyMatch != null) {
            quantity = parseQuantity(qtyMatch.groupValues[1].trim())
            remaining = remaining.substring(qtyMatch.range.last + 1).trim()
        }

        // Try to extract unit
        var unit: String? = null
        val firstWord = remaining.split(Regex("\\s+"), limit = 2).firstOrNull()?.lowercase() ?: ""
        if (firstWord in UNITS) {
            unit = firstWord
            remaining = remaining.split(Regex("\\s+"), limit = 2).getOrNull(1)?.trim() ?: ""
        }

        // Handle "of" / "de" prefix (e.g., "2 cups of flour" -> name = "flour")
        if (remaining.startsWith("of ", ignoreCase = true)) {
            remaining = remaining.substring(3).trim()
        } else if (remaining.startsWith("de ", ignoreCase = true)) {
            remaining = remaining.substring(3).trim()
        }

        return Ingredient(
            name = remaining,
            quantity = quantity,
            unit = unit,
            orderIndex = index,
        )
    }

    private fun normalizeIngredientRaw(raw: String): String {
        var text = raw.trim()
        text = text.replace(leadingNoiseRegex, "")
        text = text.replace('⁄', '/')

        // Convert common unicode fractions to ascii fractions for parser consistency.
        val unicodeFractions = listOf(
            "½" to "1/2",
            "¼" to "1/4",
            "¾" to "3/4",
            "⅓" to "1/3",
            "⅔" to "2/3",
            "⅛" to "1/8",
            "⅜" to "3/8",
            "⅝" to "5/8",
            "⅞" to "7/8",
        )

        unicodeFractions.forEach { (symbol, fraction) ->
            text = text.replace(Regex("""(?<=\d)$symbol"""), " $fraction")
            text = text.replace(symbol, fraction)
        }

        return text
    }

    private fun parseQuantity(raw: String): Double? {
        // Mixed number: "1 1/2"
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size == 2) {
            val whole = parts[0].toDoubleOrNull() ?: return null
            val fraction = parseFraction(parts[1]) ?: return null
            return whole + fraction
        }

        // Simple fraction: "1/2"
        val fraction = parseFraction(raw)
        if (fraction != null) return fraction

        // Simple number: "2" or "2.5"
        return raw.toDoubleOrNull()
    }

    private fun parseFraction(raw: String): Double? {
        val match = FRACTION_REGEX.matchEntire(raw.trim()) ?: return null
        val numerator = match.groupValues[1].toDoubleOrNull() ?: return null
        val denominator = match.groupValues[2].toDoubleOrNull() ?: return null
        if (denominator == 0.0) return null
        return numerator / denominator
    }
}
