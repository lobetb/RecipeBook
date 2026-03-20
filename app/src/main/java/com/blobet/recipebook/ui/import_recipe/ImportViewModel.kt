package com.blobet.recipebook.ui.import_recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.blobet.recipebook.R
import com.blobet.recipebook.data.parser.RecipeParser
import com.blobet.recipebook.data.repository.RecipeRepository
import com.blobet.recipebook.domain.model.Recipe
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
import org.jsoup.Jsoup
import com.google.gson.JsonParser
import javax.inject.Inject

data class ImportUiState(
    val url: String = "",
    val optionalDescriptionText: String = "",
    val isLoading: Boolean = false,
    val parsedRecipe: Recipe? = null,
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
                textParseResult.recipe.ingredients.isNotEmpty() &&
                textParseResult.recipe.instructions.isNotEmpty()
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
                        val html = fetchHtml(normalizedUrl)
                        RecipeParser.parseHtml(html, normalizedUrl)
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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = context.getString(
                            R.string.error_fetch_page,
                            e.message ?: e.javaClass.simpleName,
                        ),
                    )
                }
            }
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
                connection.setRequestProperty(
                    "User-Agent",
                    if (useDesktopUserAgent) desktopUserAgent else mobileUserAgent,
                )
                connection.setRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
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
                    throw Exception("HTTP $responseCode: ${connection.responseMessage}")
                }

                return connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }

        throw Exception("Too many redirects")
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

    fun saveRecipe(recipe: Recipe) {
        viewModelScope.launch {
            try {
                val id = recipeRepository.saveRecipe(recipe)
                _uiState.update { it.copy(savedRecipeId = id) }
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

    private fun extractFirstUrl(text: String): String? {
        val match = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE).find(text)
            ?: return null
        return match.value.trim().trimEnd('.', ',', ';', ')', ']', '"', '\'')
    }
}
