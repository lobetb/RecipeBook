package com.blobet.recipebook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.blobet.recipebook.ui.detail.DetailScreen
import com.blobet.recipebook.ui.edit.EditScreen
import com.blobet.recipebook.ui.home.HomeScreen
import com.blobet.recipebook.ui.import_recipe.ImportScreen
import com.blobet.recipebook.ui.settings.SettingsScreen
import com.blobet.recipebook.ui.shopping_list.ShoppingListScreen
import com.blobet.recipebook.ui.splash.SplashScreen

@Composable
fun NavGraph(
    sharedText: String? = null,
    sharedRecipePayloadJson: String? = null,
) {
    val navController = rememberNavController()
    val hasSharedImport = !sharedText.isNullOrBlank() || !sharedRecipePayloadJson.isNullOrBlank()
    val startDestination = if (hasSharedImport) Screen.ImportRecipe.route else Screen.Splash.route
    var pendingSharedText by remember { mutableStateOf<String?>(sharedText) }
    var pendingSharedRecipePayloadJson by remember { mutableStateOf<String?>(sharedRecipePayloadJson) }
    var lastHandledShare by remember { mutableStateOf<String?>(null) }
    val latestSharedText: State<String?> = rememberUpdatedState(sharedText)
    val latestSharedRecipePayload: State<String?> = rememberUpdatedState(sharedRecipePayloadJson)

    LaunchedEffect(sharedText) {
        val incoming = sharedText?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (incoming != lastHandledShare) {
            pendingSharedText = incoming
            navController.navigate(Screen.ImportRecipe.route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(sharedRecipePayloadJson) {
        val incoming = sharedRecipePayloadJson?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (incoming != lastHandledShare) {
            pendingSharedRecipePayloadJson = incoming
            navController.navigate(Screen.ImportRecipe.route) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onRecipeClick = { recipeId ->
                    navController.navigate(Screen.RecipeDetail.createRoute(recipeId))
                },
                onAddRecipeClick = {
                    navController.navigate(Screen.AddRecipe.route)
                },
                onImportRecipeClick = {
                    navController.navigate(Screen.ImportRecipe.route)
                },
                onShoppingListClick = {
                    navController.navigate(Screen.ShoppingList.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.RecipeDetail.route,
            arguments = listOf(navArgument("recipeId") { type = NavType.LongType })
        ) {
            DetailScreen(
                onNavigateBack = {
                    val currentRoutes = navController.previousBackStackEntry?.destination?.route
                    val popped = navController.popBackStack()
                    if (!popped || currentRoutes == null) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                },
                onEditClick = { recipeId ->
                    navController.navigate(Screen.EditRecipe.createRoute(recipeId))
                }
            )
        }

        composable(Screen.AddRecipe.route) {
            EditScreen(
                onNavigateBack = { navController.popBackStack() },
                onSaved = { recipeId ->
                    navController.popBackStack()
                    navController.navigate(Screen.RecipeDetail.createRoute(recipeId))
                }
            )
        }

        composable(
            route = Screen.EditRecipe.route,
            arguments = listOf(navArgument("recipeId") { type = NavType.LongType })
        ) {
            EditScreen(
                onNavigateBack = { navController.popBackStack() },
                onSaved = { _ ->
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.ImportRecipe.route) {
            val incomingSharedText = pendingSharedText
                ?: latestSharedText.value
                ?: ""
            val incomingSharedRecipePayload = pendingSharedRecipePayloadJson
                ?: latestSharedRecipePayload.value
                ?: ""

            ImportScreen(
                onNavigateBack = {
                    val popped = navController.popBackStack()
                    if (!popped) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.ImportRecipe.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onRecipeImported = { recipeId ->
                    navController.popBackStack()
                    navController.navigate(Screen.RecipeDetail.createRoute(recipeId))
                },
                sharedUrl = incomingSharedText,
                sharedRecipePayloadJson = incomingSharedRecipePayload,
            )

            LaunchedEffect(incomingSharedText, incomingSharedRecipePayload) {
                if (incomingSharedText.isNotBlank()) {
                    lastHandledShare = incomingSharedText
                    pendingSharedText = null
                }
                if (incomingSharedRecipePayload.isNotBlank()) {
                    lastHandledShare = incomingSharedRecipePayload
                    pendingSharedRecipePayloadJson = null
                }
            }
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ShoppingList.route) {
            ShoppingListScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
