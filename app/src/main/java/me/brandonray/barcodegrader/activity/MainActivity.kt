package me.brandonray.barcodegrader.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.remoteConfig
import dagger.hilt.android.AndroidEntryPoint
import me.brandonray.barcodegrader.BuildConfig
import me.brandonray.barcodegrader.ui.BarcodeGraderApp
import me.brandonray.barcodegrader.ui.screen.ForceUpdateScreen
import me.brandonray.barcodegrader.ui.screen.UpdateAvailableScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fetch and activate Remote Config
        val remoteConfig = Firebase.remoteConfig
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val forceUpdateVersion = remoteConfig.getString("force_update_version")
                val latestVersion = remoteConfig.getString("latest_version")
                val currentVersion = BuildConfig.VERSION_NAME

                when {
                    isVersionOlder(currentVersion, forceUpdateVersion) -> {
                        // Show Force Update Screen
                        setContent {
                            ForceUpdateScreen()
                        }
                    }

                    isVersionOlder(currentVersion, latestVersion) -> {
                        // Show Update Available Screen
                        setContent {
                            UpdateAvailableScreen {
                                // Redirect to app store for update
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=$packageName")
                                )
                                startActivity(intent)
                                finish()
                            }
                        }
                    }

                    else -> {
                        // Proceed with the normal flow
                        proceedToMainScreen()
                    }
                }
            }
        }
    }

    private fun handleLogout() {
        FirebaseAuth.getInstance().signOut()
        // Redirect to SignInActivity after logging out
        startActivity(Intent(this, SignInActivity::class.java))
        finish()
    }

    private fun handleUpgradeToPro() {
        // Handle the upgrade to Pro logic here
        // For example, you might start a new activity or launch a purchase flow
    }

    private fun isVersionOlder(currentVersion: String, requiredVersion: String): Boolean {
        val current = currentVersion.split(".").map { it.toInt() }
        val required = requiredVersion.split(".").map { it.toInt() }

        for (i in current.indices) {
            if (current[i] < required[i]) return true
            if (current[i] > required[i]) return false
        }
        return false
    }

    private fun proceedToMainScreen() {
        // Normal app flow goes here
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, SignInActivity::class.java))
        } else {
            setContent {
                MainScreen(
                    onLogout = { handleLogout() },
                    onUpgradeToPro = { handleUpgradeToPro() }
                )
            }
        }
    }
}

@Composable
fun MainScreen(onLogout: () -> Unit, onUpgradeToPro: () -> Unit) {
    BarcodeGraderApp(onLogout = onLogout, onUpgradeToPro = onUpgradeToPro)
}