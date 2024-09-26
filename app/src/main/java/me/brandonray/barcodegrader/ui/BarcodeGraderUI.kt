package me.brandonray.barcodegrader.ui

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import me.brandonray.barcodegrader.ui.screen.BarcodeScannerScreen
import me.brandonray.barcodegrader.ui.screen.HistoryScreen
import me.brandonray.barcodegrader.ui.screen.MainScreen
import me.brandonray.barcodegrader.ui.screen.PrivacyPolicyScreen
import me.brandonray.barcodegrader.ui.screen.SettingsScreen
import me.brandonray.barcodegrader.ui.screen.TermsOfServiceScreen
import me.brandonray.barcodegrader.util.BillingClientManager
import me.brandonray.barcodegrader.util.SampleData.generateSampleHistoryItems
import me.brandonray.barcodegrader.util.ThemePreferences


@Composable
fun BarcodeGraderApp(onLogout: () -> Unit, onUpgradeToPro: () -> Unit) {
    val context = LocalContext.current
    val isDarkMode by ThemePreferences.getThemePreference(context).collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()  // Remember the coroutine scope

    // Manage historyItems using a MutableState
    val historyItems = remember { mutableStateOf(generateSampleHistoryItems()) }

    val billingClientManager = remember { BillingClientManager(context) }
    val isSubscribed = remember { mutableStateOf(false) }

    // Start the billing client and query the subscription status
    LaunchedEffect(Unit) {
        billingClientManager.startConnection {
            // After connection is established, query purchases to check subscription status
            billingClientManager.queryPurchases { hasActiveSubscription ->
                isSubscribed.value = hasActiveSubscription
            }
        }
    }

    // Set the theme
    val colors = if (isDarkMode) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }

    // Function to clear history
    fun clearHistory() {
        Log.d("BarcodeGraderApp", "Clearing history data")
        historyItems.value = emptyList() // Clear the history items
    }

    MaterialTheme(colorScheme = colors) {
        val navController = rememberNavController()
        Scaffold(bottomBar = { BottomNavigationBar(navController = navController) }) { innerPadding ->
            // Apply the padding provided by Scaffold to the NavHost
            NavHost(
                navController = navController,
                startDestination = "main",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("main") {
                    MainScreen(
                        navController = navController,
                        isSubscribed = isSubscribed.value
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        navController = navController,
                        onLogout = onLogout,
                        onUpgradeToPro = onUpgradeToPro,
                        isDarkMode = isDarkMode,
                        onDarkModeToggle = { newValue ->
                            coroutineScope.launch {
                                ThemePreferences.saveThemePreference(context, newValue)
                            }
                        },
                        billingClientManager = billingClientManager,
                        isSubscribed = isSubscribed.value
                    )
                }
                composable("history") {
                    HistoryScreen(
                        navController = navController,
                        historyItems = historyItems.value,
                        onClearData = { clearHistory() }
                    )
                }
                composable("barcode_scanner") {
                    BarcodeScannerScreen(
                        navController = navController,
                        isSubscribed = isSubscribed.value
                    )
                }
                composable("privacy_policy") {
                    PrivacyPolicyScreen(navController = navController)
                }
                composable("terms_of_service") {
                    TermsOfServiceScreen(navController = navController)
                }
            }
        }
    }
}
