package com.blobet.recipebook.domain.model

import java.util.Locale
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.round

data class IngredientDisplay(
    val quantityText: String,
    val unitText: String,
    val nameText: String,
)

object IngredientConversion {

    private val celsiusRegex = Regex("""(-?\d+(?:[.,]\d+)?)\s*(?:Â)?[°º˚⁰]?\s*C\b""", RegexOption.IGNORE_CASE)
    private val fahrenheitRegex = Regex("""(-?\d+(?:[.,]\d+)?)\s*(?:Â)?[°º˚⁰]?\s*F\b""", RegexOption.IGNORE_CASE)
    private val bareDegreeRegex = Regex(
        """(-?\d+(?:[.,]\d+)?)\s*(?:Â)?[°º˚⁰](?!\s*(?:[CFcf]\b|celsius\b|fahrenheit\b))""",
        RegexOption.IGNORE_CASE,
    )
    private val bareDegreeWordRegex = Regex("""(-?\d+(?:[.,]\d+)?)\s*degrees?\b""", RegexOption.IGNORE_CASE)
    private val quantityWithUnitRegex = Regex(
        """(?<!\d)(\d+\s+\d+\s*/\s*\d+|\d+\s*/\s*\d+|\d+(?:[.,]\d+)?)([\s-]*)([A-Za-z]+)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val metricUnitTokens = setOf(
        "g", "gram", "grams", "kg", "kilogram", "kilograms",
        "ml", "milliliter", "milliliters", "l", "liter", "liters",
        "cl", "c", "cm", "mm",
    )

    private val imperialUnitTokens = setOf(
        "oz", "ounce", "ounces", "lb", "lbs", "pound", "pounds",
        "tsp", "teaspoon", "teaspoons", "tbsp", "tablespoon", "tablespoons",
        "cup", "cups", "pt", "pint", "pints", "qt", "quart", "quarts", "gal", "gallon", "gallons",
        "in", "inch", "inches",
    )

    private val scalableInstructionUnits = metricUnitTokens + imperialUnitTokens + setOf(
        "cda", "cdas", "cdta", "cdita", "cditas",
        "cucharada", "cucharadas", "cucharadita", "cucharaditas",
        "gramo", "gramos", "kilogramo", "kilogramos", "litro", "litros", "mililitro", "mililitros",
    )

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

    // Approximate cups->grams densities for common solids
    // (based on dovesfarm conversion guidance)
    private val solidsGramsPerCup = listOf(
        listOf("flour", "bread flour", "all-purpose flour", "plain flour", "farine", "harina") to 120.0,
        listOf("sugar", "granulated sugar", "white sugar", "caster sugar", "sucre", "azucar") to 200.0,
        listOf("icing sugar", "powdered sugar", "confectioners sugar", "sucre glace", "azucar glas", "azucar en polvo") to 100.0,
        listOf("brown sugar", "sucre brun", "cassonade", "azucar moreno") to 180.0,
        listOf("cornflour", "corn starch", "cornstarch", "maizena", "fecule de mais", "almidon de maiz") to 120.0,
        listOf("rice", "riz", "arroz") to 190.0,
        listOf("couscous") to 180.0,
        listOf("oats", "avoine", "avena") to 90.0,
        listOf("salt", "table salt", "sel", "sal") to 300.0,
        listOf("butter", "beurre", "mantequilla") to 240.0,
        listOf("shortening", "margarine", "manteca vegetal") to 190.0,
        listOf("chopped nuts", "noix hachees", "nueces picadas") to 150.0,
        listOf("ground nuts", "nut meal", "fruits a coque moulus", "nueces molidas") to 120.0,
        listOf("breadcrumbs", "bread crumbs", "chapelure", "pan rallado") to 150.0,
        listOf("raisins", "sultanas", "raisins secs", "pasas") to 200.0,
    )

    fun toDisplay(ingredient: Ingredient, system: UnitSystem): IngredientDisplay {
        val converted = convertIngredient(ingredient, system)
        return IngredientDisplay(
            quantityText = formatQuantityForUnit(converted.quantity, converted.unit) ?: "",
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
            UnitSystem.METRIC -> convertToMetric(ingredient)
            UnitSystem.IMPERIAL -> convertToImperial(quantity, unit)
            UnitSystem.ORIGINAL -> null
        } ?: return ingredient

        return ingredient.copy(quantity = converted.first, unit = converted.second)
    }

    fun convertTemperatureText(text: String, system: UnitSystem): String {
        return when (system) {
            UnitSystem.ORIGINAL -> text
            UnitSystem.METRIC -> convertFahrenheitToCelsius(text)
            UnitSystem.IMPERIAL -> convertCelsiusToFahrenheit(text)
        }
    }

    fun inferSourceUnitSystem(ingredients: List<Ingredient>): UnitSystem {
        var metricHits = 0
        var imperialHits = 0

        ingredients.forEach { ingredient ->
            val unit = ingredient.unit?.trim()?.lowercase().orEmpty()
            if (unit in metricUnitTokens) metricHits++
            if (unit in imperialUnitTokens) imperialHits++
        }

        return when {
            metricHits > imperialHits -> UnitSystem.METRIC
            imperialHits > metricHits -> UnitSystem.IMPERIAL
            else -> UnitSystem.ORIGINAL
        }
    }

    fun inferInstructionTemperatureSource(
        instructions: List<String>,
        ingredientSourceSystem: UnitSystem,
    ): UnitSystem {
        val joined = instructions.joinToString("\n")
        val hasExplicitF = fahrenheitRegex.containsMatchIn(joined) ||
            Regex("""\bfahrenheit\b""", RegexOption.IGNORE_CASE).containsMatchIn(joined)
        val hasExplicitC = celsiusRegex.containsMatchIn(joined) ||
            Regex("""\bcelsius\b""", RegexOption.IGNORE_CASE).containsMatchIn(joined)

        return when {
            hasExplicitF && !hasExplicitC -> UnitSystem.IMPERIAL
            hasExplicitC && !hasExplicitF -> UnitSystem.METRIC
            ingredientSourceSystem != UnitSystem.ORIGINAL -> ingredientSourceSystem
            else -> UnitSystem.ORIGINAL
        }
    }

    fun convertInstructionText(
        text: String,
        targetSystem: UnitSystem,
        sourceSystem: UnitSystem,
        originalServings: Int,
        desiredServings: Int,
    ): String {
        val normalized = normalizeInstructionFractions(text)
        val factor = if (originalServings > 0) desiredServings.toDouble() / originalServings.toDouble() else 1.0
        val scaled = scaleAndConvertInstructionQuantities(normalized, factor, targetSystem)
        return convertInstructionTemperatureText(scaled, targetSystem, sourceSystem)
    }

    private fun normalizeInstructionFractions(text: String): String {
        var result = text.replace('⁄', '/')
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
            result = result.replace(Regex("""(?<=\d)$symbol"""), " $fraction")
            result = result.replace(symbol, fraction)
        }

        return result
    }

    private fun convertFahrenheitToCelsius(text: String): String {
        return fahrenheitRegex.replace(text) { match ->
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@replace match.value
            val converted = ((value - 32.0) * 5.0 / 9.0)
            "${formatTemperature(converted)}°C"
        }
    }

    private fun convertCelsiusToFahrenheit(text: String): String {
        return celsiusRegex.replace(text) { match ->
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@replace match.value
            val converted = (value * 9.0 / 5.0) + 32.0
            "${formatTemperature(converted)}°F"
        }
    }

    private fun convertInstructionTemperatureText(
        text: String,
        targetSystem: UnitSystem,
        sourceSystem: UnitSystem,
    ): String {
        var result = text

        // First convert explicit units when present.
        result = when (targetSystem) {
            UnitSystem.METRIC -> convertFahrenheitToCelsius(result)
            UnitSystem.IMPERIAL -> convertCelsiusToFahrenheit(result)
            UnitSystem.ORIGINAL -> result
        }

        if (targetSystem == UnitSystem.ORIGINAL) return result

        // Convert bare degree values using source inference first, then numeric oven heuristics.
        result = bareDegreeRegex.replace(result) { match ->
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@replace match.value
            when (targetSystem) {
                UnitSystem.METRIC -> {
                    if (sourceSystem == UnitSystem.IMPERIAL || value >= 300.0) {
                        "${formatTemperature((value - 32.0) * 5.0 / 9.0)}°C"
                    } else {
                        match.value
                    }
                }
                UnitSystem.IMPERIAL -> {
                    if (sourceSystem == UnitSystem.METRIC || value in 120.0..260.0) {
                        "${formatTemperature((value * 9.0 / 5.0) + 32.0)}°F"
                    } else {
                        match.value
                    }
                }
                UnitSystem.ORIGINAL -> match.value
            }
        }

        return bareDegreeWordRegex.replace(result) { match ->
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@replace match.value
            when (targetSystem) {
                UnitSystem.METRIC -> {
                    if (sourceSystem == UnitSystem.IMPERIAL || value >= 300.0) {
                        "${formatTemperature((value - 32.0) * 5.0 / 9.0)}°C"
                    } else {
                        match.value
                    }
                }
                UnitSystem.IMPERIAL -> {
                    if (sourceSystem == UnitSystem.METRIC || value in 120.0..260.0) {
                        "${formatTemperature((value * 9.0 / 5.0) + 32.0)}°F"
                    } else {
                        match.value
                    }
                }
                UnitSystem.ORIGINAL -> match.value
            }
        }
    }

    private fun scaleAndConvertInstructionQuantities(text: String, factor: Double, targetSystem: UnitSystem): String {
        return quantityWithUnitRegex.replace(text) { match ->
            val qtyRaw = match.groupValues[1]
            val spacing = match.groupValues[2]
            val unitRaw = match.groupValues[3]
            val unit = unitRaw.lowercase()

            if (unit !in scalableInstructionUnits) return@replace match.value

            val parsedQty = parseQuantity(qtyRaw) ?: return@replace match.value
            val scaledQty = if (abs(factor - 1.0) < 0.0001) parsedQty else parsedQty * factor

            if (targetSystem == UnitSystem.ORIGINAL) {
                return@replace "${formatQuantity(scaledQty)}$spacing$unitRaw"
            }

            val ingredientHint = inferInstructionIngredientHint(text, match.range.last + 1)

            val convertedIngredient = convertIngredient(
                ingredient = Ingredient(name = ingredientHint, quantity = scaledQty, unit = unit),
                system = targetSystem,
            )

            val convertedQty = convertedIngredient.quantity ?: scaledQty
            val convertedUnit = convertedIngredient.unit ?: unitRaw
            val outSpacing = if (spacing.isNotBlank()) spacing else " "
            val qtyText = formatQuantityForUnit(convertedQty, convertedUnit) ?: formatQuantity(convertedQty)
            "$qtyText$outSpacing$convertedUnit"
        }
    }

    private fun inferInstructionIngredientHint(fullText: String, fromIndex: Int): String {
        if (fromIndex >= fullText.length) return ""
        val tail = fullText.substring(fromIndex)
            .replace(Regex("""^[\s-]*(of|de|du|des|the|la|le|el|los|las)\b\s*""", RegexOption.IGNORE_CASE), "")

        val untilBoundary = tail.substringBefore(',')
            .substringBefore('.')
            .substringBefore(';')
            .trim()

        val conjunctionMatch = Regex("""\b(and|et|y)\b""", RegexOption.IGNORE_CASE).find(untilBoundary)
        val truncated = if (conjunctionMatch != null) {
            untilBoundary.substring(0, conjunctionMatch.range.first).trim()
        } else {
            untilBoundary
        }

        if (truncated.isBlank()) return ""

        return truncated
            .split(Regex("""\s+"""))
            .take(3)
            .joinToString(" ")
            .trim()
    }

    private fun formatQuantityForUnit(quantity: Double?, unit: String?): String? {
        val value = quantity ?: return null
        val normalizedUnit = unit?.trim()?.lowercase().orEmpty()
        return when (normalizedUnit) {
            "ml", "g", "gram", "grams" -> round(value).toInt().toString()
            else -> formatQuantity(value)
        }
    }

    private fun parseQuantity(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.').replace("\\s+".toRegex(), " ")

        val mixedMatch = Regex("""^(\d+)\s+(\d+)\s*/\s*(\d+)$""").matchEntire(normalized)
        if (mixedMatch != null) {
            val whole = mixedMatch.groupValues[1].toDoubleOrNull() ?: return null
            val num = mixedMatch.groupValues[2].toDoubleOrNull() ?: return null
            val den = mixedMatch.groupValues[3].toDoubleOrNull() ?: return null
            if (den == 0.0) return null
            return whole + (num / den)
        }

        val fractionMatch = Regex("""^(\d+)\s*/\s*(\d+)$""").matchEntire(normalized)
        if (fractionMatch != null) {
            val num = fractionMatch.groupValues[1].toDoubleOrNull() ?: return null
            val den = fractionMatch.groupValues[2].toDoubleOrNull() ?: return null
            if (den == 0.0) return null
            return num / den
        }

        return normalized.toDoubleOrNull()
    }

    private fun formatQuantity(value: Double): String {
        val rounded = round(value)
        if (abs(value - rounded) < 0.02) return rounded.toInt().toString()

        val whole = value.toInt()
        val fraction = value - whole
        val candidates = listOf(
            2 to 1,
            3 to 1,
            3 to 2,
            4 to 1,
            4 to 3,
            8 to 1,
            8 to 3,
            8 to 5,
            8 to 7,
        )
        val best = candidates.minByOrNull { (den, num) -> abs(fraction - (num.toDouble() / den.toDouble())) }
        if (best != null) {
            val fractionValue = best.second.toDouble() / best.first.toDouble()
            if (abs(fraction - fractionValue) < 0.04) {
                val frac = "${best.second}/${best.first}"
                return if (whole > 0) "$whole $frac" else frac
            }
        }

        return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }

    private fun formatTemperature(value: Double): String {
        return round(value).toInt().toString()
    }

    private fun convertToMetric(ingredient: Ingredient): Pair<Double, String>? {
        val quantity = ingredient.quantity ?: return null
        val unit = ingredient.unit?.trim()?.lowercase().orEmpty()

        // For known solids measured with cup/tbsp/tsp, convert directly to grams.
        val gramsPerCup = gramsPerCupForIngredientName(ingredient.name)
        if (gramsPerCup != null) {
            val cupAmount = cupEquivalent(quantity, unit)
            if (cupAmount != null) {
                val grams = cupAmount * gramsPerCup
                val (q, u) = if (grams >= 1000.0) grams / 1000.0 to "kg" else grams to "g"
                return q to u
            }
        }

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

    private fun gramsPerCupForIngredientName(name: String): Double? {
        val normalized = normalizeToken(name)
        return solidsGramsPerCup.firstOrNull { (tokens, _) ->
            tokens.any { token -> normalized.contains(normalizeToken(token)) }
        }?.second
    }

    private fun normalizeToken(value: String): String {
        return Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace("'", " ")
    }

    private fun cupEquivalent(quantity: Double, unit: String): Double? {
        return when (unit) {
            "cup", "cups" -> quantity
            "tbsp", "tablespoon", "tablespoons" -> quantity / 16.0
            "tsp", "teaspoon", "teaspoons" -> quantity / 48.0
            else -> null
        }
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
