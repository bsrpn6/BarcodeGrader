package me.brandonray.barcodegrader.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    val isDarkModeEnabled = mutableStateOf(loadDarkModeSetting())
    val isAutoCaptureEnabled = mutableStateOf(loadAutoCaptureSetting())

    fun toggleDarkMode(enabled: Boolean) {
        isDarkModeEnabled.value = enabled
        saveDarkModeSetting(enabled)
    }

    fun toggleAutoCapture(enabled: Boolean) {
        isAutoCaptureEnabled.value = enabled
        saveAutoCaptureSetting(enabled)
    }

    private fun loadDarkModeSetting(): Boolean {
        return sharedPreferences.getBoolean("dark_mode", false)
    }

    private fun saveDarkModeSetting(enabled: Boolean) {
        viewModelScope.launch {
            sharedPreferences.edit().putBoolean("dark_mode", enabled).apply()
        }
    }

    private fun loadAutoCaptureSetting(): Boolean {
        return sharedPreferences.getBoolean("auto_capture", true)
    }

    private fun saveAutoCaptureSetting(enabled: Boolean) {
        viewModelScope.launch {
            sharedPreferences.edit().putBoolean("auto_capture", enabled).apply()
        }
    }

    fun changeUserPassword(
        currentPassword: String,
        newPassword: String,
        onPasswordChanged: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        user?.let {
            val email = it.email ?: return@let

            val credential = EmailAuthProvider.getCredential(email, currentPassword)

            it.reauthenticate(credential)
                .addOnCompleteListener { reauthTask ->
                    if (reauthTask.isSuccessful) {
                        it.updatePassword(newPassword)
                            .addOnCompleteListener { updateTask ->
                                if (updateTask.isSuccessful) {
                                    onPasswordChanged()
                                } else {
                                    onError(
                                        updateTask.exception?.message ?: "Password update failed"
                                    )
                                }
                            }
                    } else {
                        onError(reauthTask.exception?.message ?: "Re-authentication failed")
                    }
                }
        } ?: onError("User not authenticated")
    }
}