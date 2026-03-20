package com.blobet.recipebook.data.parser

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.blobet.recipebook.domain.model.Ingredient
import com.blobet.recipebook.domain.model.Recipe
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Extracts recipe data from HTML by looking for schema.org/Recipe
 * structured data in JSON-LD format, then falling back to microdata.
 */
object RecipeParser {

    private val ingredientHeadings = listOf(
        "ingredients", "ingredient", "what you need", "shopping list",
        "ingrédients", "ingredients", "ce qu'il faut", "ce quil faut"
    )

    private val instructionHeadings = listOf(
        "instructions", "directions", "method", "steps", "preparation",
        "préparation", "preparation", "méthode", "methode", "étapes", "etapes"
    )

    private val quantityUnitRegex = Regex(
        """^\s*(\d+[\d\s/.,-]*)\s*(g|kg|mg|ml|l|cl|cup|cups|tbsp|tsp|oz|lb|pinch|slice|slices|c\.?\s?à\.?\s?s\.?|c\.?\s?à\.?\s?c\.?)\b""",
        RegexOption.IGNORE_CASE,
    )

    private const val heuristicConfidenceThreshold = 0.55f
    private val textStepVerbRegex = Regex(
        """\b(mix|stir|bake|cook|add|heat|serve|boil|preheat|pour|grill|fry|melt|whisk|combine|blend|chop|slice|cuire|ajouter|melanger|mélanger|chauffer|servir|faire\s+bouillir|prechauffer|préchauffer)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val inlineIngredientRegex = Regex(
        """(\d+[\d\s/.,-]*\s*(g|kg|mg|ml|l|cl|cup|cups|tbsp|tsp|oz|lb|pinch|cloves?|slice|slices|onions?|garlic\s+cloves?|c\.?\s?a\.?\s?s\.?|c\.?\s?a\.?\s?c\.?)\s+[^\d\n#]{2,60})""",
        RegexOption.IGNORE_CASE,
    )

    data class ParseResult(
        val recipe: Recipe?,
        val error: String? = null,
    )

    private data class HeuristicResult(
        val recipe: Recipe,
        val confidence: Float,
    )

    fun parseHtml(html: String, sourceUrl: String): ParseResult {
        return try {
            val doc = Jsoup.parse(html)

            // Try JSON-LD first (most common and reliable)
            val jsonLdRecipe = extractFromJsonLd(doc, sourceUrl)
            if (jsonLdRecipe != null) return ParseResult(recipe = jsonLdRecipe)

            // Fallback: try microdata
            val microdataRecipe = extractFromMicrodata(doc, sourceUrl)
            if (microdataRecipe != null) return ParseResult(recipe = microdataRecipe)

            // Fallback: heuristic extraction for non-standard pages
            val heuristicRecipe = extractByHeuristics(doc, sourceUrl)
            if (heuristicRecipe != null && heuristicRecipe.confidence >= heuristicConfidenceThreshold) {
                return ParseResult(recipe = heuristicRecipe.recipe)
            }

            ParseResult(recipe = null, error = "Could not find recipe data on this page. The site may not use standard recipe markup.")
        } catch (e: Exception) {
            ParseResult(recipe = null, error = "Error parsing page: ${e.message}")
        }
    }

    fun parseTextRecipe(text: String, sourceUrl: String): ParseResult {
        val normalized = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("|", "\n")
            .replace(";", "\n")
            .replace(Regex("""\s*[•●▪◦·]\s*"""), "\n- ")
            .replace(Regex("""(?<!\n)\s+(?=\d+[.)]\s+)"""), "\n")
            .replace(Regex("""(?<!\n)\s+-\s+(?=[A-Za-z0-9])"""), "\n- ")
            .replace(Regex("#[\\p{L}0-9_]+"), "")
            .trim()

        if (normalized.isBlank()) {
            return ParseResult(recipe = null, error = "No text recipe content found")
        }

        val lines = normalized
            .split(Regex("\n+"))
            .map { cleanHtml(it).trim() }
            .filter { it.isNotBlank() }
            .flatMap { splitCompoundListLine(it) }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return ParseResult(recipe = null, error = "No text recipe content found")
        }

        val title = lines.firstOrNull { line ->
            !isHeadingLine(line) && !isLikelyNoiseLine(line)
        }?.take(80) ?: "TikTok Recipe"
        val ingredients = mutableListOf<Ingredient>()
        val instructions = mutableListOf<String>()

        lines.forEachIndexed { index, line ->
            when {
                looksLikeIngredientLine(line) -> ingredients.add(IngredientParser.parse(line, ingredients.size))
                looksLikeInstructionLine(line) -> instructions.add(cleanHtml(line))
            }
        }

        if (instructions.isEmpty()) {
            val inlineSteps = Regex("""(\d+\.\s+[^\n]{15,400})(?=(\s+\d+\.\s+)|$)""")
                .findAll(normalized)
                .map { it.groupValues[1].trim() }
                .filter { looksLikeInstructionLine(it) }
                .toList()
            if (inlineSteps.isNotEmpty()) {
                instructions.addAll(inlineSteps)
            }
        }

        if (instructions.isEmpty()) {
            val numberedSteps = Regex(
                """(?:^|\s)(\d+[\.)]\s+.{15,900}?)(?=(?:\s\d+[\.)]\s+)|$)""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            )
                .findAll(normalized)
                .map { it.groupValues[1].replace(Regex("\\s+"), " ").trim() }
                .filter { it.length in 12..900 }
                .toList()

            if (numberedSteps.size >= 2) {
                instructions.addAll(numberedSteps)
            }
        }

        if (ingredients.isEmpty()) {
            inlineIngredientRegex.findAll(normalized).forEach { match ->
                val candidate = cleanHtml(match.groupValues[1]).trim().trim(',', '.', ';')
                if (candidate.length in 4..90) {
                    ingredients.add(IngredientParser.parse(candidate, ingredients.size))
                }
            }
        }

        if (ingredients.isEmpty() && instructions.isEmpty()) {
            return ParseResult(recipe = null, error = "Could not detect ingredients or instructions in shared text")
        }

        if (instructions.size == 1) {
            // Try splitting one long line into usable steps
            val split = instructions.first().split(Regex("(?<=\\.)\\s+(?=[A-Z0-9])"))
                .map { it.trim() }
                .filter { it.length > 8 }
            if (split.size > 1) {
                instructions.clear()
                instructions.addAll(split)
            }
        }

        return ParseResult(
            recipe = Recipe(
                title = title,
                description = lines.take(3).joinToString(" ").take(240),
                sourceUrl = sourceUrl,
                servings = 4,
                ingredients = ingredients,
                instructions = instructions,
            )
        )
    }

    private fun looksLikeIngredientLine(line: String): Boolean {
        val lower = line.lowercase()
        if (lower.startsWith("ingredients")) return false
        if (line.length < 3 || line.length > 140) return false
        return quantityUnitRegex.containsMatchIn(line) ||
            Regex("""^[-•]\s+.+""").containsMatchIn(line)
    }

    private fun looksLikeInstructionLine(line: String): Boolean {
        val lower = line.lowercase()
        if (lower.startsWith("instructions") || lower.startsWith("directions") || lower.startsWith("method")) {
            return false
        }
        if (line.length < 8 || line.length > 800) return false
        val hasStepPrefix = Regex("""^(step\s*\d+|\d+[.)])""", RegexOption.IGNORE_CASE).containsMatchIn(line)
        return hasStepPrefix || textStepVerbRegex.containsMatchIn(line)
    }

    private fun isHeadingLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.startsWith("ingredients") ||
            lower.startsWith("instructions") ||
            lower.startsWith("directions") ||
            lower.startsWith("method")
    }

    private fun splitCompoundListLine(line: String): List<String> {
        val normalizedLine = line.trim()

        val numberedMatches = Regex("""\d+[.)]\s+[^\d].{2,220}(?=(\s+\d+[.)]\s+)|$)""")
            .findAll(normalizedLine)
            .map { it.value.trim() }
            .toList()
        if (numberedMatches.size >= 2) return numberedMatches

        val bulletMatches = Regex("""[-•]\s+.{2,180}(?=(\s+[-•]\s+)|$)""")
            .findAll(normalizedLine)
            .map { it.value.trim() }
            .toList()
        if (bulletMatches.size >= 2) return bulletMatches

        return listOf(normalizedLine)
    }

    private fun isLikelyNoiseLine(line: String): Boolean {
        val lower = line.lowercase()
        return (lower.contains("likes") && lower.contains("comments")) ||
            lower.startsWith("share an #")
    }

    private fun extractFromJsonLd(doc: Document, sourceUrl: String): Recipe? {
        val scripts = doc.select("script[type=application/ld+json]")

        for (script in scripts) {
            try {
                val json = JsonParser.parseString(script.data())
                val recipeJson = findRecipeInJson(json) ?: continue
                return jsonObjectToRecipe(recipeJson, sourceUrl)
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    private fun findRecipeInJson(json: JsonElement): JsonObject? {
        return when {
            json.isJsonObject -> {
                val obj = json.asJsonObject
                val type = obj.get("@type")
                val isRecipe = when {
                    type == null -> false
                    type.isJsonPrimitive -> type.asString.equals("Recipe", ignoreCase = true)
                    type.isJsonArray -> type.asJsonArray.any {
                        it.isJsonPrimitive && it.asString.equals("Recipe", ignoreCase = true)
                    }
                    else -> false
                }
                if (isRecipe) obj
                else {
                    // Check @graph
                    val graph = obj.get("@graph")
                    if (graph != null && graph.isJsonArray) {
                        findRecipeInJson(graph)
                    } else null
                }
            }
            json.isJsonArray -> {
                json.asJsonArray.asSequence()
                    .mapNotNull { findRecipeInJson(it) }
                    .firstOrNull()
            }
            else -> null
        }
    }

    private fun jsonObjectToRecipe(json: JsonObject, sourceUrl: String): Recipe {
        val title = json.getStringOrNull("name") ?: "Untitled Recipe"
        val description = json.getStringOrNull("description") ?: ""
        val imageUrl = extractImageUrl(json)
        val servings = extractServings(json)
        val prepTime = parseDuration(json.getStringOrNull("prepTime"))
        val cookTime = parseDuration(json.getStringOrNull("cookTime"))
        val instructions = extractInstructions(json)
        val ingredients = extractIngredients(json)

        return Recipe(
            title = cleanHtml(title),
            description = cleanHtml(description),
            sourceUrl = sourceUrl,
            imageUrl = imageUrl,
            servings = servings ?: 4,
            prepTimeMinutes = prepTime,
            cookTimeMinutes = cookTime,
            instructions = instructions,
            ingredients = ingredients,
        )
    }

    private fun extractImageUrl(json: JsonObject): String? {
        val image = json.get("image") ?: return null
        return when {
            image.isJsonPrimitive -> image.asString
            image.isJsonArray -> {
                val arr = image.asJsonArray
                if (arr.size() > 0) {
                    val first = arr[0]
                    if (first.isJsonPrimitive) first.asString
                    else if (first.isJsonObject) first.asJsonObject.getStringOrNull("url")
                    else null
                } else null
            }
            image.isJsonObject -> image.asJsonObject.getStringOrNull("url")
            else -> null
        }
    }

    private fun extractServings(json: JsonObject): Int? {
        val yield = json.get("recipeYield") ?: return null
        return when {
            yield.isJsonPrimitive -> {
                val str = yield.asString
                Regex("""\d+""").find(str)?.value?.toIntOrNull()
            }
            yield.isJsonArray && yield.asJsonArray.size() > 0 -> {
                val first = yield.asJsonArray[0]
                if (first.isJsonPrimitive) {
                    Regex("""\d+""").find(first.asString)?.value?.toIntOrNull()
                } else null
            }
            else -> null
        }
    }

    private fun extractInstructions(json: JsonObject): List<String> {
        val instructions = json.get("recipeInstructions") ?: return emptyList()
        val result = mutableListOf<String>()

        when {
            instructions.isJsonPrimitive -> {
                // Single string, split by newlines or periods
                val text = instructions.asString
                result.addAll(
                    text.split(Regex("\n+"))
                        .map { cleanHtml(it.trim()) }
                        .filter { it.isNotBlank() }
                )
            }
            instructions.isJsonArray -> {
                for (item in instructions.asJsonArray) {
                    when {
                        item.isJsonPrimitive -> {
                            val cleaned = cleanHtml(item.asString.trim())
                            if (cleaned.isNotBlank()) result.add(cleaned)
                        }
                        item.isJsonObject -> {
                            val obj = item.asJsonObject
                            val type = obj.getStringOrNull("@type") ?: ""

                            when {
                                type.equals("HowToStep", ignoreCase = true) -> {
                                    val text = obj.getStringOrNull("text")
                                        ?: obj.getStringOrNull("name")
                                        ?: ""
                                    val cleaned = cleanHtml(text.trim())
                                    if (cleaned.isNotBlank()) result.add(cleaned)
                                }
                                type.equals("HowToSection", ignoreCase = true) -> {
                                    val sectionName = obj.getStringOrNull("name")
                                    if (!sectionName.isNullOrBlank()) {
                                        result.add("--- ${cleanHtml(sectionName)} ---")
                                    }
                                    val itemList = obj.get("itemListElement")
                                    if (itemList != null && itemList.isJsonArray) {
                                        for (step in itemList.asJsonArray) {
                                            if (step.isJsonObject) {
                                                val text = step.asJsonObject.getStringOrNull("text")
                                                    ?: step.asJsonObject.getStringOrNull("name")
                                                    ?: ""
                                                val cleaned = cleanHtml(text.trim())
                                                if (cleaned.isNotBlank()) result.add(cleaned)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    private fun extractIngredients(json: JsonObject): List<Ingredient> {
        val ingredients = json.get("recipeIngredient")
            ?: json.get("ingredients")
            ?: return emptyList()

        if (!ingredients.isJsonArray) {
            return if (ingredients.isJsonPrimitive) {
                listOf(IngredientParser.parse(ingredients.asString, 0))
            } else emptyList()
        }

        return ingredients.asJsonArray.mapIndexed { index, element ->
            val raw = if (element.isJsonPrimitive) element.asString else element.toString()
            IngredientParser.parse(cleanHtml(raw), index)
        }
    }

    private fun extractFromMicrodata(doc: Document, sourceUrl: String): Recipe? {
        val recipeElement = doc.select("[itemtype*=schema.org/Recipe]").firstOrNull()
            ?: return null

        val title = recipeElement.select("[itemprop=name]").firstOrNull()?.text() ?: "Untitled Recipe"
        val description = recipeElement.select("[itemprop=description]").firstOrNull()?.text() ?: ""
        val imageUrl = recipeElement.select("[itemprop=image]").firstOrNull()?.let {
            it.attr("src").ifBlank { it.attr("content") }
        }

        val servingsText = recipeElement.select("[itemprop=recipeYield]").firstOrNull()?.text()
        val servings = servingsText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }

        val prepTimeStr = recipeElement.select("[itemprop=prepTime]").firstOrNull()?.attr("content")
        val cookTimeStr = recipeElement.select("[itemprop=cookTime]").firstOrNull()?.attr("content")

        val ingredientElements = recipeElement.select("[itemprop=recipeIngredient], [itemprop=ingredients]")
        val ingredients = ingredientElements.mapIndexed { index, el ->
            IngredientParser.parse(el.text(), index)
        }

        val instructionElements = recipeElement.select("[itemprop=recipeInstructions]")
        val instructions = if (instructionElements.size == 1 && instructionElements[0].tagName() != "li") {
            instructionElements[0].text()
                .split(Regex("\\.\\s+|\n+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        } else {
            instructionElements.map { it.text().trim() }.filter { it.isNotBlank() }
        }

        return Recipe(
            title = title,
            description = description,
            sourceUrl = sourceUrl,
            imageUrl = imageUrl,
            servings = servings ?: 4,
            prepTimeMinutes = parseDuration(prepTimeStr),
            cookTimeMinutes = parseDuration(cookTimeStr),
            instructions = instructions,
            ingredients = ingredients,
        )
    }

    private fun extractByHeuristics(doc: Document, sourceUrl: String): HeuristicResult? {
        val root = findMainContentRoot(doc)
        val title = guessTitle(doc, root)
        if (title.isBlank()) return null

        val ingredientLines = extractSectionLines(root, ingredientHeadings)
            .ifEmpty { guessIngredientsWithoutHeading(root) }
        val instructionLines = extractInstructionLines(root)

        val ingredients = ingredientLines
            .mapIndexed { index, line -> IngredientParser.parse(line, index) }
            .filter { it.displayText().isNotBlank() }
        val instructions = instructionLines

        val description = root.select("p")
            .asSequence()
            .map { cleanHtml(it.text()) }
            .firstOrNull { it.length > 40 }
            .orEmpty()

        val servings = extractServingsFromText(root.text()) ?: 4
        val prepTime = extractTimeFromText(root.text(), listOf("prep", "preparation", "préparation"))
        val cookTime = extractTimeFromText(root.text(), listOf("cook", "cuisson"))

        val confidence = computeHeuristicConfidence(
            ingredientCount = ingredients.size,
            instructionCount = instructions.size,
            hasIngredientHeading = hasAnyHeading(root, ingredientHeadings),
            hasInstructionHeading = hasAnyHeading(root, instructionHeadings),
        )

        if (ingredients.isEmpty() || instructions.isEmpty()) return null

        return HeuristicResult(
            recipe = Recipe(
                title = title,
                description = description,
                sourceUrl = sourceUrl,
                servings = servings,
                prepTimeMinutes = prepTime,
                cookTimeMinutes = cookTime,
                instructions = instructions,
                ingredients = ingredients,
            ),
            confidence = confidence,
        )
    }

    private fun findMainContentRoot(doc: Document): Element {
        return doc.selectFirst("main, article, [role=main]")
            ?: doc.body()
    }

    private fun guessTitle(doc: Document, root: Element): String {
        val h1 = root.selectFirst("h1")?.text()?.trim().orEmpty()
        if (h1.isNotBlank()) return cleanHtml(h1)
        return cleanHtml(doc.title().ifBlank { "Untitled Recipe" })
    }

    private fun hasAnyHeading(root: Element, candidates: List<String>): Boolean {
        return root.select("h1, h2, h3, h4, h5, h6, strong")
            .any { heading ->
                val text = heading.text().trim().lowercase()
                candidates.any { candidate -> text.contains(candidate) }
            }
    }

    private fun extractSectionLines(root: Element, headings: List<String>): List<String> {
        val heading = root.select("h1, h2, h3, h4, h5, h6, strong")
            .firstOrNull {
                val headingText = it.text().trim().lowercase()
                headings.any { candidate -> headingText.contains(candidate) }
            } ?: return emptyList()

        val lines = mutableListOf<String>()
        var cursor = heading.nextElementSibling()
        var hop = 0

        while (cursor != null && hop < 8) {
            if (cursor.tagName() in setOf("h1", "h2", "h3", "h4", "h5", "h6")) break

            when (cursor.tagName()) {
                "ul", "ol" -> {
                    cursor.select("li")
                        .map { cleanHtml(it.text()) }
                        .filter { it.isNotBlank() }
                        .forEach { lines.add(it) }
                }
                "p" -> {
                    val text = cleanHtml(cursor.text())
                    if (text.isNotBlank()) lines.add(text)
                }
                else -> {
                    cursor.select("li").forEach { li ->
                        val text = cleanHtml(li.text())
                        if (text.isNotBlank()) lines.add(text)
                    }
                }
            }

            if (lines.size >= 30) break
            cursor = cursor.nextElementSibling()
            hop++
        }

        return lines
    }

    private fun guessIngredientsWithoutHeading(root: Element): List<String> {
        val candidateLines = root.select("li")
            .map { cleanHtml(it.text()) }
            .filter { it.length in 4..120 }

        return candidateLines
            .filter { line -> quantityUnitRegex.containsMatchIn(line) }
            .take(30)
    }

    private fun extractInstructionLines(root: Element): List<String> {
        val headingBased = extractSectionLines(root, instructionHeadings)
            .flatMap { chunk -> splitInstructionChunk(chunk) }
            .map { cleanHtml(it) }
            .filter { it.length > 8 }
        if (headingBased.size >= 2) return headingBased.take(40)

        val orderedList = root.select("ol li")
            .map { cleanHtml(it.text()) }
            .filter { text -> looksLikeInstruction(text) }
        if (orderedList.size >= 2) return orderedList.take(40)

        val looseListItems = root.select("li")
            .asSequence()
            .filter { li ->
                val parentTag = li.parent()?.tagName()
                parentTag !in setOf("ul", "ol")
            }
            .map { li -> cleanHtml(li.text()) }
            .filter { text -> looksLikeInstruction(text) }
            .toList()
        if (looseListItems.size >= 2) return looseListItems.take(40)

        return root.select("p")
            .map { cleanHtml(it.text()) }
            .flatMap { paragraph -> splitInstructionChunk(paragraph) }
            .map { cleanHtml(it) }
            .filter { text -> looksLikeInstruction(text) }
            .take(20)
    }

    private fun splitInstructionChunk(text: String): List<String> {
        if (text.contains("\n")) {
            return text.split(Regex("\n+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
        return text.split(Regex("(?<=\\.)\\s+(?=[A-Z0-9])"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun looksLikeInstruction(text: String): Boolean {
        if (text.length < 12 || text.length > 480) return false
        val lower = text.lowercase()

        // Avoid common non-step list items (navigation/category noise)
        if (lower.contains("copyright")) return false
        if (lower.contains(" recipes") && text.length < 60) return false

        val hasVerbCue = listOf(
            "mix", "stir", "bake", "cook", "add", "heat", "serve", "boil", "preheat",
            "place", "pour", "cover", "drain", "grill", "let",
            "mélanger", "melanger", "cuire", "ajouter", "chauffer", "servir", "faire bouillir",
            "préchauffer", "prechauffer", "verser", "couvrir", "égoutter", "egoutter", "laisser"
        ).any { cue -> lower.contains(cue) }
        val hasStepPrefix = Regex("""^(step\s*\d+|\d+[.)])""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        return hasVerbCue || hasStepPrefix
    }

    private fun computeHeuristicConfidence(
        ingredientCount: Int,
        instructionCount: Int,
        hasIngredientHeading: Boolean,
        hasInstructionHeading: Boolean,
    ): Float {
        var score = 0f
        if (ingredientCount >= 3) score += 0.35f
        if (instructionCount >= 2) score += 0.35f
        if (hasIngredientHeading) score += 0.15f
        if (hasInstructionHeading) score += 0.15f
        return score.coerceIn(0f, 1f)
    }

    private fun extractServingsFromText(text: String): Int? {
        val match = Regex("""(serves?|yield|portions?)\s*[:\-]?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(text)
        return match?.groupValues?.getOrNull(2)?.toIntOrNull()
    }

    private fun extractTimeFromText(text: String, labels: List<String>): Int? {
        val labelPattern = labels.joinToString("|") { Regex.escape(it) }
        val regex = Regex("""($labelPattern)\s*[:\-]?\s*(\d{1,3})\s*(min|minutes?)""", RegexOption.IGNORE_CASE)
        val match = regex.find(text) ?: return null
        return match.groupValues.getOrNull(2)?.toIntOrNull()
    }

    /**
     * Parse ISO 8601 duration (e.g., "PT30M", "PT1H30M") to minutes
     */
    private fun parseDuration(duration: String?): Int? {
        if (duration == null) return null
        val regex = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""", RegexOption.IGNORE_CASE)
        val match = regex.find(duration) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        val totalMinutes = hours * 60 + minutes
        return if (totalMinutes > 0) totalMinutes else null
    }

    /**
     * Strip HTML tags and decode common HTML entities without
     * spinning up a full Jsoup Document for every string.
     */
    private fun cleanHtml(text: String): String {
        if (!text.contains('<') && !text.contains('&')) return text.trim()
        return text
            .replace(Regex("<[^>]*>"), "")       // strip tags
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { chr ->
                val code = chr.groupValues[1].toIntOrNull()
                if (code != null) code.toChar().toString() else chr.value
            }
            .replace(Regex("\\s+"), " ")         // collapse whitespace
            .trim()
    }

    private fun JsonObject.getStringOrNull(key: String): String? {
        val element = get(key) ?: return null
        return if (element.isJsonPrimitive) element.asString else null
    }
}
