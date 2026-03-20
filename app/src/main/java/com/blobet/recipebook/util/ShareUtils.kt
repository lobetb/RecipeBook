package com.blobet.recipebook.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.blobet.recipebook.R
import com.blobet.recipebook.domain.model.Recipe
import java.io.File

object ShareUtils {

    private const val RECIPE_FILE_SUFFIX = ".recipebook"
    const val RECIPE_FILE_MIME = "application/vnd.blobet.recipebook+json"

    fun shareRecipe(context: Context, recipe: Recipe) {
        val text = buildString {
            appendLine(recipe.title)
            appendLine()

            if (recipe.description.isNotBlank()) {
                appendLine(recipe.description)
                appendLine()
            }

            if (recipe.ingredients.isNotEmpty()) {
                appendLine(context.getString(R.string.share_ingredients_header))
                appendLine("----------")
                recipe.ingredients.forEach { ingredient ->
                    appendLine("- ${ingredient.displayText()}")
                }
                appendLine()
            }

            if (recipe.instructions.isNotEmpty()) {
                appendLine(context.getString(R.string.share_instructions_header))
                appendLine("------------")
                recipe.instructions.forEachIndexed { index, step ->
                    appendLine("${index + 1}. $step")
                }
                appendLine()
            }

            val time = recipe.totalTimeMinutes
            if (time != null) {
                appendLine(context.getString(R.string.share_total_time, formatMinutes(time)))
            }
            appendLine(context.getString(R.string.share_servings, recipe.servings))

            if (!recipe.sourceUrl.isNullOrBlank()) {
                appendLine()
                appendLine(context.getString(R.string.share_source, recipe.sourceUrl))
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, recipe.title)
            putExtra(Intent.EXTRA_TEXT, text)
        }

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_recipe)))
    }

    fun shareRecipeUrl(context: Context, recipe: Recipe): Boolean {
        val url = recipe.sourceUrl?.takeIf { it.isNotBlank() } ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, recipe.title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_recipe_url)))
        return true
    }

    fun shareRecipeAsFile(context: Context, recipe: Recipe): Boolean {
        return try {
            val payload = RecipeSharePayload.fromRecipe(recipe)
            val json = RecipeShareCodec.encode(payload)
            val fileName = recipe.title
                .ifBlank { "recipe" }
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "recipe" } + RECIPE_FILE_SUFFIX

            val shareDir = File(context.cacheDir, "shared_recipes").apply { mkdirs() }
            val outFile = File(shareDir, fileName)
            outFile.writeText(json)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile,
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, recipe.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(context.contentResolver, fileName, uri)
            }

            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_recipe_file_chooser)))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun formatMinutes(minutes: Int): String {
        return when {
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}min"
        }
    }
}
