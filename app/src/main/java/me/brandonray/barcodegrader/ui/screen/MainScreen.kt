package me.brandonray.barcodegrader.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import me.brandonray.barcodegrader.R

@Composable
fun MainScreen(navController: NavHostController, isSubscribed: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_barcode), // Replace with your image resource
            contentDescription = "Scan Barcode",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(300.dp)
                .clickable {
                    // Navigate to the barcode scanner screen
                    navController.navigate("barcode_scanner")
                }
        )
    }
}
