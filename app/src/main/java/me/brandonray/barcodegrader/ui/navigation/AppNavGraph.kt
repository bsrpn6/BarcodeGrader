package me.brandonray.barcodegrader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun AppNavGraph(navController: NavHostController, onLogout: () -> Unit) {
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {

        }
    }
}
