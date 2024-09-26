package me.brandonray.barcodegrader.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import me.brandonray.barcodegrader.R


@Composable
fun BottomNavigationBar(navController: NavHostController) {
    NavigationBar {
        NavigationBarItem(
            selected = navController.currentDestination?.route == "history",
            onClick = { navController.navigate("history") },
            icon = { Icon(painterResource(R.drawable.ic_history), contentDescription = "History") },
            label = { Text("History") }
        )
        NavigationBarItem(
            selected = navController.currentDestination?.route == "settings",
            onClick = { navController.navigate("settings") },
            icon = {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = "Settings"
                )
            },
            label = { Text("Settings") }
        )
    }
}
