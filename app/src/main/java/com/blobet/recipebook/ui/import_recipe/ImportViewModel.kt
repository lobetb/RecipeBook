package com.blobet.recipebook.ui.import_recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.blobet.recipebook.R
import com.blobet.recipebook.data.parser.RecipeParser
import com.blobet.recipebook.data.repository.RecipeRepository
import com.blobet.recipebook.domain.model.Recipe
import com.blobet.recipebook.util.RecipeShareCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import com.google.gson.JsonParser
import javax.inject.Inject

data class ImportUiState(
    val url: String = "",
    val optionalDescriptionText: String = "",
    val isLoading: Boolean = false,
    val parsedRecipe: Recipe? = null,
    val duplicateRecipeId: Long? = null,
    val duplicateRecipeTitle: String? = null,
    val duplicateMatchedByUrl: Boolean = false,
    val pendingSaveRecipe: Recipe? = null,
    val errorMessage: String? = null,
    val savedRecipeId: Long? = null,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {
    private val logTag = "RecipeBookTikTok"
    private val mobileUserAgent =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun onUrlChange(url: String) {
        _uiState.update { it.copy(url = url, errorMessage = null) }
    }

    fun onOptionalDescriptionTextChange(text: String) {
        _uiState.update { it.copy(optionalDescriptionText = text, errorMessage = null) }
    }

    fun importSharedUrl(rawSharedText: String) {
        if (_uiState.value.isLoading || _uiState.value.parsedRecipe != null) return

        val extractedUrl = extractFirstUrl(rawSharedText)
        if (extractedUrl == null) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.error_no_url_in_shared_content)) }
            return
        }

        // TikTok shares sometimes include useful caption text in the shared payload.
        // Try text parsing first before fetching the page.
        if (isTikTokUrl(extractedUrl)) {
            val textParseResult = RecipeParser.parseTextRecipe(rawSharedText, extractedUrl)
            if (textParseResult.recipe != null &&
                (textParseResult.recipe.ingredients.isNotEmpty() ||
                    textParseResult.recipe.instructions.isNotEmpty())
            ) {
                _uiState.update {
                    it.copy(
                        url = extractedUrl,
                        parsedRecipe = textParseResult.recipe,
                        errorMessage = null,
                        isLoading = false,
                    )
                }
                return
            }
        }

        onUrlChange(extractedUrl)
        fetchAndParse()
    }

    fun importSharedRecipePayload(payloadJson: String) {
        if (_uiState.value.isLoading || _uiState.value.parsedRecipe != null) return

        try {
            val payload = RecipeShareCodec.decode(payloadJson)
            val recipe = payload.toRecipe()
                _uiState.update {
                    it.copy(
                        parsedRecipe = recipe,
                        duplicateRecipeId = null,
                        duplicateRecipeTitle = null,
                        duplicateMatchedByUrl = false,
                        pendingSaveRecipe = null,
                        errorMessage = null,
                        isLoading = false,
                        url = recipe.sourceUrl.orEmpty(),
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.error_invalid_recipe_file)) }
        }
    }

    fun fetchAndParse() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.error_enter_url)) }
            return
        }

        val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, parsedRecipe = null) }

            try {
                val result = withContext(Dispatchers.IO) {
                    if (isTikTokUrl(normalizedUrl)) {
                        parseTikTokRecipe(normalizedUrl)
                    } else {
                        parseGenericRecipeWithAmpFallback(normalizedUrl)
                    }
                }

                val finalResult = if (result.recipe == null && _uiState.value.optionalDescriptionText.isNotBlank()) {
                    val textResult = RecipeParser.parseTextRecipe(
                        _uiState.value.optionalDescriptionText,
                        normalizedUrl,
                    )
                    if (textResult.recipe != null) textResult else result
                } else {
                    result
                }

                if (finalResult.recipe != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            parsedRecipe = finalResult.recipe,
                            duplicateRecipeId = null,
                            duplicateRecipeTitle = null,
                            duplicateMatchedByUrl = false,
                            pendingSaveRecipe = null,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = finalResult.error ?: context.getString(R.string.error_extract_recipe),
                        )
                    }
                }
            } catch (e: Exception) {
                val friendlyMessage = when {
                    e is HttpStatusException && e.statusCode == 403 -> {
                        context.getString(R.string.error_site_forbidden)
                    }
                    else -> {
                        context.getString(
                            R.string.error_fetch_page,
                            e.message ?: e.javaClass.simpleName,
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = friendlyMessage,
                    )
                }
            }
        }
    }

    private fun parseGenericRecipeWithAmpFallback(url: String): RecipeParser.ParseResult {
        var mobileHtml: String? = null
        var bestRecipe: Recipe? = null
        var bestError: String? = null

        try {
            mobileHtml = fetchHtml(url, useDesktopUserAgent = false)
            val mobileResult = RecipeParser.parseHtml(mobileHtml, url)
            if (!isLowQualityExtraction(mobileResult.recipe)) {
                return mobileResult
            }
            bestRecipe = mobileResult.recipe
            bestError = mobileResult.error
        } catch (e: Exception) {
            bestError = if (e is HttpStatusException && e.statusCode == 403) {
                context.getString(R.string.error_site_forbidden)
            } else {
                e.message ?: e.javaClass.simpleName
            }
        }

        // Some providers serve richer content when common output query params are present.
        if (bestRecipe == null || isLowQualityExtraction(bestRecipe)) {
            val queryVariants = listOf("output=amp", "output=1", "print=1")
            for (variant in queryVariants) {
                val variantUrl = appendQueryParam(url, variant)
                try {
                    val variantHtml = fetchHtml(variantUrl, useDesktopUserAgent = true)
                    val variantResult = RecipeParser.parseHtml(variantHtml, variantUrl)
                    bestRecipe = mergeRecipeCandidates(bestRecipe, variantResult.recipe)
                    if (bestRecipe == null && variantResult.recipe != null) {
                        bestRecipe = variantResult.recipe
                    }
                    if (bestRecipe != null && !isLowQualityExtraction(bestRecipe)) {
                        return RecipeParser.ParseResult(recipe = bestRecipe)
                    }
                } catch (_: Exception) {
                    // Continue trying other variants
                }
            }
        }

        // Some websites expose richer structured data on desktop markup only.
        try {
            val desktopHtml = fetchHtml(url, useDesktopUserAgent = true)
            val desktopResult = RecipeParser.parseHtml(desktopHtml, url)
            bestRecipe = mergeRecipeCandidates(bestRecipe, desktopResult.recipe)
            if (bestRecipe == null && desktopResult.recipe != null) {
                bestRecipe = desktopResult.recipe
            }
            if (!desktopResult.error.isNullOrBlank()) {
                bestError = desktopResult.error
            }
        } catch (_: Exception) {
            // Keep mobile result as fallback
        }

        val ampUrl = mobileHtml?.let { extractAmpUrl(it, url) }
        if (!ampUrl.isNullOrBlank() && ampUrl != url) {
            try {
                val ampHtml = fetchHtml(ampUrl)
                val ampResult = RecipeParser.parseHtml(ampHtml, ampUrl)
                bestRecipe = mergeRecipeCandidates(bestRecipe, ampResult.recipe)
                if (bestRecipe == null && ampResult.recipe != null) {
                    bestRecipe = ampResult.recipe
                }
                if (!ampResult.error.isNullOrBlank()) {
                    bestError = ampResult.error
                }
            } catch (_: Exception) {
                // Ignore AMP failures
            }
        }

        return if (bestRecipe != null) {
            RecipeParser.ParseResult(recipe = bestRecipe)
        } else {
            // Last-resort fallback for anti-bot/login shells: fetch a text-rendered copy
            // and parse it as plain recipe text.
            val textFallback = tryParseViaTextMirror(url)
            if (textFallback.recipe != null || !textFallback.error.isNullOrBlank()) {
                textFallback
            } else {
                RecipeParser.ParseResult(recipe = null, error = bestError)
            }
        }
    }

    private fun tryParseViaTextMirror(url: String): RecipeParser.ParseResult {
        return try {
            val mirrorUrl = "https://r.jina.ai/http://${url.removePrefix("https://").removePrefix("http://")}" 
            val rendered = fetchHtml(mirrorUrl, useDesktopUserAgent = true)
            val parsed = RecipeParser.parseTextRecipe(rendered, url)
            val recipe = parsed.recipe
            if (recipe != null) {
                val blockedTitle = recipe.title.contains("human verification", ignoreCase = true) ||
                    recipe.title.contains("cloudflare", ignoreCase = true)
                val blockedBody = recipe.description.contains("method not allowed", ignoreCase = true) ||
                    recipe.description.contains("security check", ignoreCase = true) ||
                    recipe.instructions.any {
                        it.contains("human", ignoreCase = true) ||
                            it.contains("security check", ignoreCase = true) ||
                            it.contains("verification", ignoreCase = true)
                    }
                if (blockedTitle || blockedBody) {
                    return RecipeParser.ParseResult(
                        recipe = null,
                        error = context.getString(R.string.error_site_human_verification),
                    )
                }
            }
            parsed
        } catch (_: Exception) {
            RecipeParser.ParseResult(recipe = null)
        }
    }

    private fun appendQueryParam(url: String, param: String): String {
        return if (url.contains("?")) {
            "$url&$param"
        } else {
            "$url?$param"
        }
    }

    /**
     * Fetch HTML using HttpURLConnection which handles redirects and SSL
     * reliably on Android without needing extra dependencies.
     */
    private fun fetchHtml(urlString: String, useDesktopUserAgent: Boolean = false): String {
        var currentUrl = urlString
        var redirectCount = 0
        val maxRedirects = 5

        while (redirectCount < maxRedirects) {
            val url = URL(currentUrl)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                applyBrowserLikeHeaders(
                    connection = connection,
                    useDesktopUserAgent = useDesktopUserAgent,
                    referer = currentUrl,
                )

                val responseCode = connection.responseCode

                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw Exception("Redirect without Location header")
                    currentUrl = if (location.startsWith("http")) {
                        location
                    } else {
                        // Relative redirect
                        URL(URL(currentUrl), location).toString()
                    }
                    redirectCount++
                    continue
                }

                if (responseCode != 200) {
                    throw HttpStatusException(responseCode, "HTTP $responseCode: ${connection.responseMessage}")
                }

                val html = readResponseText(connection)
                // If we received a login/paywall shell, retry once with lightweight anti-bot headers.
                if (looksLikeLoginShell(html)) {
                    connection.disconnect()
                    return fetchHtmlWithBypassHeaders(currentUrl, useDesktopUserAgent)
                }
                return html
            } finally {
                connection.disconnect()
            }
        }

        throw Exception("Too many redirects")
    }

    private fun fetchHtmlWithBypassHeaders(urlString: String, useDesktopUserAgent: Boolean): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            applyBrowserLikeHeaders(
                connection = connection,
                useDesktopUserAgent = useDesktopUserAgent,
                referer = urlString,
            )
            connection.setRequestProperty("Cache-Control", "no-cache, max-age=0")
            connection.setRequestProperty("Pragma", "no-cache")
            val code = connection.responseCode
            if (code != 200) {
                throw HttpStatusException(code, "HTTP $code: ${connection.responseMessage}")
            }
            return readResponseText(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun looksLikeLoginShell(html: String): Boolean {
        val lower = html.lowercase()
        return (lower.contains("inloggen") || lower.contains("log in") || lower.contains("registreer") || lower.contains("forgot_password")) &&
            !lower.contains("recipeinstructions") &&
            !lower.contains("recipeingredient")
    }

    private fun applyBrowserLikeHeaders(
        connection: HttpURLConnection,
        useDesktopUserAgent: Boolean,
        referer: String,
    ) {
        connection.setRequestProperty(
            "User-Agent",
            if (useDesktopUserAgent) desktopUserAgent else mobileUserAgent,
        )
        connection.setRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        )
        connection.setRequestProperty("Accept-Language", "fr-BE,fr;q=0.9,en-US;q=0.8,en;q=0.7")
        connection.setRequestProperty("Connection", "keep-alive")
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1")
        connection.setRequestProperty("Sec-Fetch-Dest", "document")
        connection.setRequestProperty("Sec-Fetch-Mode", "navigate")
        connection.setRequestProperty("Sec-Fetch-Site", "none")
        connection.setRequestProperty("Sec-Fetch-User", "?1")
        connection.setRequestProperty("Referer", referer)
    }

    private fun readResponseText(connection: HttpURLConnection): String {
        val rawStream = connection.inputStream
        val encoding = connection.getHeaderField("Content-Encoding")?.lowercase().orEmpty()
        val decodedStream = when {
            encoding.contains("gzip") -> GZIPInputStream(rawStream)
            encoding.contains("deflate") -> InflaterInputStream(rawStream)
            else -> rawStream
        }

        val contentType = connection.getHeaderField("Content-Type").orEmpty()
        val charsetName = Regex("charset=([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
            .find(contentType)
            ?.groupValues
            ?.getOrNull(1)
            ?: "UTF-8"
        val charset = try {
            Charset.forName(charsetName)
        } catch (_: Exception) {
            Charsets.UTF_8
        }

        return decodedStream.bufferedReader(charset).use { it.readText() }
    }

    private fun parseTikTokRecipe(url: String): RecipeParser.ParseResult {
        return try {
            Log.d(logTag, "parseTikTokRecipe start url=$url")
            val normalized = url.substringBefore('#')
            val expandedUrl = expandShortUrl(normalized)
            Log.d(logTag, "expandedUrl=$expandedUrl")

            var fallbackRecipeFromOEmbed: RecipeParser.ParseResult? = null

            // Try oEmbed first (often available even when full page parsing is blocked)
            val oembedText = fetchTikTokOEmbedText(expandedUrl)
            Log.d(logTag, "oembedTextLength=${oembedText?.length ?: 0}")
            if (!oembedText.isNullOrBlank()) {
                val oembedResult = RecipeParser.parseTextRecipe(oembedText, expandedUrl)
                if (oembedResult.recipe != null) {
                    val hasSteps = oembedResult.recipe.instructions.isNotEmpty()
                    Log.d(logTag, "oembed parse success hasSteps=$hasSteps")
                    if (hasSteps) return oembedResult
                    fallbackRecipeFromOEmbed = oembedResult
                }
                Log.d(logTag, "oembed parse returned no recipe")
            }

            val mobileHtml = fetchHtml(expandedUrl, useDesktopUserAgent = false)
            val desktopHtml = fetchHtml(expandedUrl, useDesktopUserAgent = true)
            Log.d(logTag, "htmlLengthMobile=${mobileHtml.length} htmlLengthDesktop=${desktopHtml.length}")

            val mobileCandidates = buildDescriptionCandidates(Jsoup.parse(mobileHtml), mobileHtml)
            val desktopCandidates = buildDescriptionCandidates(Jsoup.parse(desktopHtml), desktopHtml)

            val bestMobile = parseBestRecipeFromCandidates(mobileCandidates, expandedUrl)
            val bestDesktop = parseBestRecipeFromCandidates(desktopCandidates, expandedUrl)

            Log.d(
                logTag,
                "mobileCandidates=${mobileCandidates.size} mobileBestSteps=${bestMobile?.instructions?.size ?: 0} mobileBestIngredients=${bestMobile?.ingredients?.size ?: 0}",
            )
            Log.d(
                logTag,
                "desktopCandidates=${desktopCandidates.size} desktopBestSteps=${bestDesktop?.instructions?.size ?: 0} desktopBestIngredients=${bestDesktop?.ingredients?.size ?: 0}",
            )

            val bestFromCandidates = pickRicherRecipe(bestDesktop, bestMobile)
            Log.d(
                logTag,
                "chosenBestSteps=${bestFromCandidates?.instructions?.size ?: 0} chosenBestIngredients=${bestFromCandidates?.ingredients?.size ?: 0}",
            )

            if (bestFromCandidates == null) {
                Log.d(logTag, "no TikTok description found")
                fallbackRecipeFromOEmbed ?: RecipeParser.ParseResult(
                    recipe = null,
                    error = context.getString(R.string.error_tiktok_no_description),
                )
            } else {
                RecipeParser.ParseResult(
                    recipe = mergeRecipeData(bestFromCandidates, fallbackRecipeFromOEmbed?.recipe)
                )
            }
        } catch (e: Exception) {
            Log.e(logTag, "parseTikTokRecipe failed", e)
            RecipeParser.ParseResult(
                recipe = null,
                error = context.getString(
                    R.string.error_tiktok_fetch,
                    e.message ?: e.javaClass.simpleName,
                ),
            )
        }
    }

    private fun isTikTokUrl(url: String): Boolean {
        return url.contains("tiktok.com", ignoreCase = true)
    }

    private fun expandShortUrl(urlString: String): String {
        var currentUrl = urlString
        repeat(6) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    "User-Agent",
                    mobileUserAgent,
                )

                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return currentUrl
                    currentUrl = if (location.startsWith("http")) location else URL(URL(currentUrl), location).toString()
                } else {
                    return currentUrl
                }
            } finally {
                connection.disconnect()
            }
        }
        return currentUrl
    }

    private fun fetchTikTokOEmbedText(videoUrl: String): String? {
        return try {
            val encoded = URLEncoder.encode(videoUrl, "UTF-8")
            val oembedUrl = "https://www.tiktok.com/oembed?url=$encoded"
            val json = fetchHtml(oembedUrl)
            val obj = JsonParser.parseString(json).asJsonObject
            val title = obj.get("title")?.asString?.trim().orEmpty()
            val author = obj.get("author_name")?.asString?.trim().orEmpty()
            listOf(cleanTikTokDescriptionCandidate(title), author)
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDescCandidatesFromEmbeddedJson(html: String): List<String> {
        val escapedPattern = Regex("\\\"desc\\\":\\\"(.*?)\\\"", RegexOption.DOT_MATCHES_ALL)
        val plainPattern = Regex("\"desc\":\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)

        val escapedMatches = escapedPattern.findAll(html).map { it.groupValues[1] }
        val plainMatches = plainPattern.findAll(html).map { it.groupValues[1] }

        return (escapedMatches + plainMatches)
            .map { raw ->
                raw
                    .replace("\\n", " ")
                    .replace("\\/", "/")
                    .replace("\\\"", "\"")
                    .replace(Regex("\\\\u([0-9a-fA-F]{4})")) { m ->
                        m.groupValues[1].toInt(16).toChar().toString()
                    }
                    .trim()
            }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun parseBestRecipeFromCandidates(candidates: List<String>, sourceUrl: String): Recipe? {
        return candidates
            .distinct()
            .asSequence()
            .map { candidate -> cleanTikTokDescriptionCandidate(candidate) }
            .filter { it.length >= 20 }
            .map { candidate -> RecipeParser.parseTextRecipe(candidate, sourceUrl).recipe }
            .filterNotNull()
            .maxByOrNull { recipe ->
                // Prioritize extracted steps first, then ingredients.
                recipe.instructions.size * 100 + recipe.ingredients.size
            }
    }

    private fun pickRicherRecipe(first: Recipe?, second: Recipe?): Recipe? {
        fun score(recipe: Recipe?): Int {
            if (recipe == null) return -1
            return recipe.instructions.size * 100 + recipe.ingredients.size
        }
        return if (score(first) >= score(second)) first else second
    }

    private fun buildDescriptionCandidates(doc: org.jsoup.nodes.Document, html: String): List<String> {
        return buildList {
            addAll(
                listOfNotNull(
                    doc.selectFirst("meta[property=og:description]")?.attr("content"),
                    doc.selectFirst("meta[name=description]")?.attr("content"),
                )
            )
            addAll(extractParagraphFromEmbeddedHtml(html))
            addAll(extractDescCandidatesFromEmbeddedJson(html))
        }.map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun extractParagraphFromEmbeddedHtml(html: String): List<String> {
        val result = mutableListOf<String>()

        Regex("""<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .forEach { match ->
                val raw = match.groupValues[1]
                val cleaned = raw
                    .replace(Regex("""<a[^>]*>.*?</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
                    .replace(Regex("""<[^>]+>"""), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                if (cleaned.length > 120 && (cleaned.contains("1.") || cleaned.contains("Steps to Prepare", ignoreCase = true))) {
                    result.add(cleaned)
                }
            }

        return result
    }

    private fun cleanTikTokDescriptionCandidate(candidate: String): String {
        var text = candidate.trim()

        // Remove common TikTok metrics prefix like:
        // "536.2K likes, 716 comments. \"..."
        text = text.replace(
            Regex("""^\s*\d+[\d.,]*\s*[KMB]?\s+likes?,\s*\d+[\d.,]*\s*[KMB]?\s+comments?\.\s*[\"“]?\s*""", RegexOption.IGNORE_CASE),
            "",
        )

        // Trim wrapping quotes
        text = text.trim().trim('"', '“', '”')

        return text
    }

    private fun mergeRecipeData(primary: Recipe, fallback: Recipe?): Recipe {
        if (fallback == null) return primary

        return primary.copy(
            ingredients = if (primary.ingredients.isNotEmpty()) primary.ingredients else fallback.ingredients,
            instructions = if (primary.instructions.isNotEmpty()) primary.instructions else fallback.instructions,
            description = if (primary.description.isNotBlank()) primary.description else fallback.description,
        )
    }

    private fun extractAmpUrl(html: String, baseUrl: String): String? {
        val doc: Document = Jsoup.parse(html, baseUrl)
        val ampLink = doc.selectFirst("link[rel=amphtml]") ?: return null
        val abs = ampLink.absUrl("href").trim()
        if (abs.isNotBlank()) return abs
        return ampLink.attr("href").trim().ifBlank { null }
    }

    private fun isLowQualityExtraction(recipe: Recipe?): Boolean {
        if (recipe == null) return true
        val score = extractionQualityScore(recipe)
        return score < 200
    }

    private fun extractionQualityScore(recipe: Recipe): Int {
        val titleScore = when {
            recipe.title.isBlank() -> 0
            recipe.title.equals("Untitled Recipe", ignoreCase = true) -> 0
            recipe.title.equals("TikTok Recipe", ignoreCase = true) -> 0
            else -> 20
        }
        val descriptionScore = if (recipe.description.length >= 40) 10 else 0
        val ingredientsScore = recipe.ingredients.size * 20
        val instructionsScore = recipe.instructions.size * 40
        return titleScore + descriptionScore + ingredientsScore + instructionsScore
    }

    private fun mergeRecipeCandidates(primary: Recipe?, secondary: Recipe?): Recipe? {
        if (primary == null) return secondary
        if (secondary == null) return primary

        val richerIngredients = if (primary.ingredients.size >= secondary.ingredients.size) {
            primary.ingredients
        } else {
            secondary.ingredients
        }

        val richerInstructions = if (primary.instructions.size >= secondary.instructions.size) {
            primary.instructions
        } else {
            secondary.instructions
        }

        val merged = primary.copy(
            title = chooseBestTitle(primary.title, secondary.title),
            description = choosePreferredText(primary.description, secondary.description),
            imageUrl = primary.imageUrl ?: secondary.imageUrl,
            servings = chooseServings(primary.servings, secondary.servings),
            prepTimeMinutes = primary.prepTimeMinutes ?: secondary.prepTimeMinutes,
            cookTimeMinutes = primary.cookTimeMinutes ?: secondary.cookTimeMinutes,
            ingredients = richerIngredients,
            instructions = richerInstructions,
        )

        val primaryScore = extractionQualityScore(primary)
        val secondaryScore = extractionQualityScore(secondary)
        val mergedScore = extractionQualityScore(merged)

        return when {
            mergedScore >= primaryScore && mergedScore >= secondaryScore -> merged
            secondaryScore > primaryScore -> secondary
            else -> primary
        }
    }

    private fun chooseBestTitle(first: String, second: String): String {
        val firstClean = first.trim()
        val secondClean = second.trim()
        val firstGeneric = firstClean.equals("Untitled Recipe", ignoreCase = true) ||
            firstClean.equals("TikTok Recipe", ignoreCase = true)
        val secondGeneric = secondClean.equals("Untitled Recipe", ignoreCase = true) ||
            secondClean.equals("TikTok Recipe", ignoreCase = true)

        return when {
            firstClean.isBlank() -> secondClean
            secondClean.isBlank() -> firstClean
            firstGeneric && !secondGeneric -> secondClean
            secondGeneric && !firstGeneric -> firstClean
            secondClean.length > firstClean.length + 6 -> secondClean
            else -> firstClean
        }
    }

    private fun choosePreferredText(first: String, second: String): String {
        val a = first.trim()
        val b = second.trim()
        return when {
            a.isBlank() -> b
            b.isBlank() -> a
            b.length > a.length + 30 -> b
            else -> a
        }
    }

    private fun chooseServings(first: Int, second: Int): Int {
        return when {
            first != 4 -> first
            second != 4 -> second
            else -> first
        }
    }

    fun saveRecipe(recipe: Recipe) {
        viewModelScope.launch {
            try {
                val duplicate = recipeRepository.findDuplicateForImport(recipe.sourceUrl, recipe.title)
                if (duplicate != null) {
                    val byUrl = !recipe.sourceUrl.isNullOrBlank() &&
                        !duplicate.sourceUrl.isNullOrBlank() &&
                        recipe.sourceUrl.trim().equals(duplicate.sourceUrl.trim(), ignoreCase = true)
                    _uiState.update {
                        it.copy(
                            duplicateRecipeId = duplicate.id,
                            duplicateRecipeTitle = duplicate.title,
                            duplicateMatchedByUrl = byUrl,
                            pendingSaveRecipe = recipe,
                        )
                    }
                    return@launch
                }

                val id = recipeRepository.saveRecipe(recipe)
                _uiState.update {
                    it.copy(
                        savedRecipeId = id,
                        pendingSaveRecipe = null,
                        duplicateRecipeId = null,
                        duplicateRecipeTitle = null,
                        duplicateMatchedByUrl = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = context.getString(
                            R.string.error_save,
                            e.message ?: e.javaClass.simpleName,
                        )
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun confirmSaveDuplicate() {
        val recipe = _uiState.value.pendingSaveRecipe ?: return
        viewModelScope.launch {
            try {
                val id = recipeRepository.saveRecipe(recipe)
                _uiState.update {
                    it.copy(
                        savedRecipeId = id,
                        pendingSaveRecipe = null,
                        duplicateRecipeId = null,
                        duplicateRecipeTitle = null,
                        duplicateMatchedByUrl = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        pendingSaveRecipe = null,
                        duplicateRecipeId = null,
                        duplicateRecipeTitle = null,
                        duplicateMatchedByUrl = false,
                        errorMessage = context.getString(
                            R.string.error_save,
                            e.message ?: e.javaClass.simpleName,
                        )
                    )
                }
            }
        }
    }

    fun cancelSaveDuplicate() {
        _uiState.update {
            it.copy(
                pendingSaveRecipe = null,
                duplicateRecipeId = null,
                duplicateRecipeTitle = null,
                duplicateMatchedByUrl = false,
            )
        }
    }

    private fun extractFirstUrl(text: String): String? {
        val match = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE).find(text)
            ?: return null
        return match.value.trim().trimEnd('.', ',', ';', ')', ']', '"', '\'')
    }

    private class HttpStatusException(
        val statusCode: Int,
        message: String,
    ) : Exception(message)
}
