package me.brandonray.barcodegrader.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object ThemePreferences {
    private val Context.dataStore by preferencesDataStore("theme_preferences")
    private val THEME_KEY = booleanPreferencesKey("dark_mode_enabled")

    suspend fun saveThemePreference(context: Context, isDarkMode: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = isDarkMode
        }
    }

    fun getThemePreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[THEME_KEY] ?: false
        }
    }
}