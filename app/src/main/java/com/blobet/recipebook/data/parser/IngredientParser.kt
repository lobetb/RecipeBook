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
    )

    // Matches fractions like "1/2", "3/4"
    private val FRACTION_REGEX = Regex("""(\d+)\s*/\s*(\d+)""")

    private val VULGAR_FRACTIONS = mapOf(
        '¼' to "1/4",
        '½' to "1/2",
        '¾' to "3/4",
        '⅐' to "1/7",
        '⅑' to "1/9",
        '⅒' to "1/10",
        '⅓' to "1/3",
        '⅔' to "2/3",
        '⅕' to "1/5",
        '⅖' to "2/5",
        '⅗' to "3/5",
        '⅘' to "4/5",
        '⅙' to "1/6",
        '⅚' to "5/6",
        '⅛' to "1/8",
        '⅜' to "3/8",
        '⅝' to "5/8",
        '⅞' to "7/8",
    )

    fun parse(raw: String, index: Int = 0): Ingredient {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return Ingredient(name = "", orderIndex = index)
        }

        var remaining = trimmed
            .replace(Regex("""^\s*[-•●▪◦*]+\s*"""), "")
            .replace(Regex("""^\s*\d+\s*[.)-]\s+"""), "")
        var quantity: Double? = null

        // Try to extract quantity from first tokens (supports 1/2, 1 1/2, 1½, ½)
        val tokens = remaining.split(Regex("\\s+")).filter { it.isNotBlank() }
        var consumedTokens = 0
        for (count in minOf(3, tokens.size) downTo 1) {
            val candidate = tokens.take(count).joinToString(" ")
            val parsed = parseQuantity(candidate)
            if (parsed != null) {
                quantity = parsed
                consumedTokens = count
                break
            }
        }

        if (consumedTokens > 0) {
            remaining = tokens.drop(consumedTokens).joinToString(" ").trim()
        }

        // Try to extract unit
        var unit: String? = null
        val firstWord = remaining.split(Regex("\\s+"), limit = 2).firstOrNull()?.lowercase() ?: ""
        if (firstWord in UNITS) {
            unit = firstWord
            remaining = remaining.split(Regex("\\s+"), limit = 2).getOrNull(1)?.trim() ?: ""
        }

        // Handle "of" prefix (e.g., "2 cups of flour" -> name = "flour")
        if (remaining.startsWith("of ", ignoreCase = true)) {
            remaining = remaining.substring(3).trim()
        }

        return Ingredient(
            name = remaining,
            quantity = quantity,
            unit = unit,
            orderIndex = index,
        )
    }

    private fun parseQuantity(raw: String): Double? {
        val normalizedRaw = normalizeVulgarFractions(raw.trim())
            .replace(',', '.')
            .replace(Regex("""\s+"""), " ")

        // Mixed number: "1 1/2"
        val parts = normalizedRaw.split(Regex("\\s+"))
        if (parts.size == 2) {
            val whole = parts[0].toDoubleOrNull() ?: return null
            val fraction = parseFraction(parts[1]) ?: return null
            return whole + fraction
        }

        // Simple fraction: "1/2"
        val fraction = parseFraction(normalizedRaw)
        if (fraction != null) return fraction

        // Simple number: "2" or "2.5"
        return normalizedRaw.toDoubleOrNull()
    }

    private fun parseFraction(raw: String): Double? {
        val match = FRACTION_REGEX.matchEntire(raw.trim()) ?: return null
        val numerator = match.groupValues[1].toDoubleOrNull() ?: return null
        val denominator = match.groupValues[2].toDoubleOrNull() ?: return null
        if (denominator == 0.0) return null
        return numerator / denominator
    }

    private fun normalizeVulgarFractions(raw: String): String {
        var out = raw
        VULGAR_FRACTIONS.forEach { (char, replacement) ->
            out = out.replace(char.toString(), " $replacement")
        }
        return out.trim()
    }
}
