package me.brandonray.barcodegrader.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import dagger.hilt.android.AndroidEntryPoint
import me.brandonray.barcodegrader.BuildConfig
import me.brandonray.barcodegrader.R

@AndroidEntryPoint
class SignInActivity : ComponentActivity() {
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { result: FirebaseAuthUIAuthenticationResult ->
        handleSignInResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Choose authentication providers
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
            // Add more providers if needed (e.g., Facebook, Twitter, etc.)
        )

        // Create and launch sign-in intent
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setLogo(R.drawable.ic_barcode_scanner) // Set your app's logo
            .setTheme(R.style.FirebaseSignInTheme)
            .setAvailableProviders(providers)
            .setIsSmartLockEnabled(false)
            .build()

        // Show bypass option only in debug builds
        if (BuildConfig.DEBUG) {
            Log.d("SignInActivity.kt", "Is DEBUG build, showing bypass login dialog")
            showBypassLoginDialog(signInIntent)
        } else {
            signInLauncher.launch(signInIntent)
        }
    }

    private fun showBypassLoginDialog(signInIntent: Intent) {
        Log.d("SignInActivity.kt", "Showing bypass login dialog")
        AlertDialog.Builder(this)
            .setTitle("Debug Options")
            .setMessage("Do you want to bypass login?")
            .setPositiveButton("Bypass") { _, _ ->
                Toast.makeText(this, "Bypassing login", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .setNegativeButton("Sign In") { _, _ ->
                signInLauncher.launch(signInIntent)
            }
            .setCancelable(false)
            .show()
    }

    private fun handleSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            // Successfully signed in
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            // Sign in failed, handle the error
            val response = result.idpResponse
            response?.error?.let {
                // Log or display the error
                Log.e("SignInActivity", "Sign-in error: ${it.localizedMessage}")
                // You can also show a Toast or Dialog to the user here
                Toast.makeText(this, "Sign-in failed: ${it.localizedMessage}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }
}