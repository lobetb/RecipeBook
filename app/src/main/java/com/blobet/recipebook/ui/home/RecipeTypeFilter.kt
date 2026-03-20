package com.blobet.recipebook.ui.home

import androidx.annotation.StringRes
import com.blobet.recipebook.R
import com.blobet.recipebook.domain.model.Recipe

enum class RecipeTypeFilter(
    @StringRes val labelRes: Int,
    private val keywords: Set<String>,
) {
    BREAKFAST(
        R.string.type_breakfast,
        setOf("breakfast", "petit-dejeuner", "brunch", "pancake", "omelet", "oatmeal"),
    ),
    LUNCH(R.string.type_lunch, setOf("lunch", "dejeuner", "sandwich", "wrap", "bowl")),
    DINNER(R.string.type_dinner, setOf("dinner", "diner", "main", "entree", "supper")),
    DESSERT(R.string.type_dessert, setOf("dessert", "cake", "cookie", "brownie", "sweet", "pie")),
    SNACK(R.string.type_snack, setOf("snack", "en-cas", "bite", "granola", "trail mix")),
    SOUP(R.string.type_soup, setOf("soup", "soupe", "stew", "chowder", "broth")),
    SALAD(R.string.type_salad, setOf("salad", "salade", "slaw")),
    SIDE(R.string.type_side, setOf("side", "accompagnement", "side dish")),
    DRINK(R.string.type_drink, setOf("drink", "boisson", "beverage", "smoothie", "shake", "juice", "tea", "coffee"));

    fun matches(recipe: Recipe): Boolean {
        val haystack = buildString {
            append(recipe.title)
            append(' ')
            append(recipe.description)
            append(' ')
            append(recipe.tags.joinToString(" ") { it.name })
        }.lowercase()

        return keywords.any { keyword -> haystack.contains(keyword) }
    }
}
