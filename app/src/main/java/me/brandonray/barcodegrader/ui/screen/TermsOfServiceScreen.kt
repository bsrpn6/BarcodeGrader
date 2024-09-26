package me.brandonray.barcodegrader.ui.screen

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import me.brandonray.barcodegrader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(navController: NavHostController) {
    val context = LocalContext.current

    // State to hold the terms of service text
    val termsOfServiceText = remember { mutableStateOf("") }

    // Load the terms of service text when the composable is first composed
    LaunchedEffect(Unit) {
        termsOfServiceText.value = loadTermsOfService(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms of Service") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) {
        // Display the terms of service text in a scrollable column
        Column(
            modifier = Modifier
                .padding(it)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Terms of Service",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = termsOfServiceText.value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

fun loadTermsOfService(context: Context): String {
    return try {
        val text = context.resources.openRawResource(R.raw.terms_of_service)
            .bufferedReader().use { it.readText() }
        Log.d("TermsOfServiceScreen", "Terms of Service loaded successfully")
        text
    } catch (e: Exception) {
        Log.e("TermsOfServiceScreen", "Failed to load Terms of Service", e)
        "Failed to load Terms of Service."
    }
}