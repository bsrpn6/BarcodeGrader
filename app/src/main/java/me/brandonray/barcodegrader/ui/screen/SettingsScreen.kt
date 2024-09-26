package me.brandonray.barcodegrader.ui.screen

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import me.brandonray.barcodegrader.ui.viewmodel.SettingsViewModel
import me.brandonray.barcodegrader.util.BillingClientManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    onUpgradeToPro: () -> Unit,
    isDarkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    billingClientManager: BillingClientManager,
    isSubscribed: Boolean

) {
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showChangePasswordDialog) {
        ChangePasswordDialog(onPasswordChange = { currentPassword, newPassword ->
            viewModel.changeUserPassword(currentPassword, newPassword, onPasswordChanged = {
                showChangePasswordDialog = false
                Toast.makeText(context, "Password changed successfully", Toast.LENGTH_SHORT).show()
            }, onError = { errorMessage ->
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            })
        }, onDismiss = { showChangePasswordDialog = false })
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back"
                )
            }
        })
    }) {
        // Content of the Settings Screen
        Column(
            modifier = Modifier
                .padding(it) // Padding provided by Scaffold to avoid overlap with the TopAppBar
                .fillMaxSize()
                .verticalScroll(rememberScrollState()), // Making the column scrollable
            verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.Start
        ) {
            // Section for Account settings
            Text(
                text = "Account",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider()

            if (!isSubscribed) {
                SettingItem(text = "Upgrade to Pro", onClick = {
                    billingClientManager.startConnection {
                        billingClientManager.queryProductDetails("your_subscription_id") { productDetails ->
                            if (productDetails != null) {
                                billingClientManager.launchPurchaseFlow(
                                    context as Activity,
                                    productDetails
                                )
                            } else {
                                // Handle the error: product details not found
                                Toast.makeText(
                                    context,
                                    "Product details not found",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                })
            }
            SettingItem(text = "Change Password", onClick = { showChangePasswordDialog = true })
//            SettingItem(
//                text = "Manage Subscriptions",
//                onClick = { /* Handle subscription management */ })

            Spacer(modifier = Modifier.height(20.dp))

            // Section for App settings
            Text(
                text = "App Settings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider()

            SettingItem(
                text = "Dark Mode",
                onClick = {}, // No specific action when clicking the item, the toggle manages the state
                toggleState = isDarkMode,
                onToggle = onDarkModeToggle
            )
//            SettingItem(text = "Automatic Image Capture",
//                onClick = {},
//                toggleState = viewModel.isAutoCaptureEnabled.value,
//                onToggle = { viewModel.toggleAutoCapture(it) })
//            SettingItem(text = "Notifications", onClick = { /* Handle notifications */ })
//            SettingItem(text = "Language", onClick = { /* Handle language selection */ })

            SettingItem(text = "Privacy Policy",
                onClick = { navController.navigate("privacy_policy") })
            SettingItem(text = "Terms of Service",
                onClick = { navController.navigate("terms_of_service") })

            Spacer(modifier = Modifier.height(20.dp))

            // Section for Logout
            Button(
                onClick = onLogout, modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Logout")
            }
        }
    }
}

@Composable
fun SettingItem(
    text: String,
    onClick: () -> Unit,
    toggleState: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)
        )

        // Show the toggle if the toggleState and onToggle are provided
        if (toggleState != null && onToggle != null) {
            Switch(
                checked = toggleState,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}