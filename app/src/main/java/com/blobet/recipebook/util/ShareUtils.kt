package com.blobet.recipebook.util

import android.content.Context
import android.content.Intent
import com.blobet.recipebook.R
import com.blobet.recipebook.domain.model.Recipe

object ShareUtils {

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

    private fun formatMinutes(minutes: Int): String {
        return when {
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}min"
        }
    }
}
