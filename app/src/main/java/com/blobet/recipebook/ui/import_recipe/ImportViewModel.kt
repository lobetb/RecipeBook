package com.blobet.recipebook.ui.import_recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.net.URL
import javax.inject.Inject

data class ImportUiState(
    val url: String = "",
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

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun onUrlChange(url: String) {
        _uiState.update { it.copy(url = url, errorMessage = null) }
    }

    fun importSharedUrl(rawSharedText: String) {
        if (_uiState.value.isLoading || _uiState.value.parsedRecipe != null) return

        val extractedUrl = extractFirstUrl(rawSharedText)
        if (extractedUrl == null) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.error_no_url_in_shared_content)) }
            return
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
                    val html = fetchHtml(normalizedUrl)
                    RecipeParser.parseHtml(html, normalizedUrl)
                }

                if (result.recipe != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            parsedRecipe = result.recipe,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.error ?: context.getString(R.string.error_extract_recipe),
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
    private fun fetchHtml(urlString: String): String {
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
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
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
