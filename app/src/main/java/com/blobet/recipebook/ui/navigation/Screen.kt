package com.blobet.recipebook.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Home : Screen("home")
    data object RecipeDetail : Screen("recipe/{recipeId}") {
        fun createRoute(recipeId: Long) = "recipe/$recipeId"
    }
    data object AddRecipe : Screen("add_recipe")
    data object EditRecipe : Screen("edit_recipe/{recipeId}") {
        fun createRoute(recipeId: Long) = "edit_recipe/$recipeId"
    }
    data object ImportRecipe : Screen("import_recipe")
    data object Settings : Screen("settings")
}
