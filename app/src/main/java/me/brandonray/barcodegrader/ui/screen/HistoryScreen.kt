package me.brandonray.barcodegrader.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavHostController, historyItems: List<HistoryItem>, onClearData: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("History") }, navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }, actions = {
            // Trash can icon for clearing the data
            IconButton(onClick = {
                onClearData() // Clear the data and refresh the screen
            }) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear Data")
            }
        })
    }) {
        // If historyItems is empty, show a message instead
        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Text("No history available", style = MaterialTheme.typography.headlineSmall)
            }
        } else {
            // Content of the History Screen
            Column(
                modifier = Modifier
                    .padding(it) // Padding provided by Scaffold to avoid overlap with the TopAppBar
                    .fillMaxSize()
            ) {
                // Summary section showing counts of each grade
                SummarySection(historyItems)

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)
                ) {
                    items(historyItems) { historyItem ->
                        HistoryItemCard(historyItem)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SummarySection(historyItems: List<HistoryItem>) {
    val gradeCounts = remember(historyItems) {
        historyItems.groupingBy { it.grade }.eachCount()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Summary",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )


        Row {
            Text(
                text = "A: ${gradeCounts["A"] ?: 0}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF006400), // Dark Green
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "B: ${gradeCounts["B"] ?: 0}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFFFD700), // Darker Yellow (Gold)
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "C: ${gradeCounts["C"] ?: 0}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFFFA500), // Orange color
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "F: ${gradeCounts["F"] ?: 0}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.Red, fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
fun HistoryItemCard(historyItem: HistoryItem) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with click to enlarge
            Image(bitmap = historyItem.thumbnail.asImageBitmap(),
                contentDescription = "Thumbnail",
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showDialog = true })

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Barcode: ${historyItem.decodedBarcode}",
                    style = MaterialTheme.typography.bodyLarge, // Updated from body1
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Grade: ${historyItem.grade}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = when (historyItem.grade) {
                            "A" -> Color(0xFF006400) // Green color
                            "B" -> Color(0xFFFFD700) // Yellow color
                            "C" -> Color(0xFFFFA500) // Orange color
                            else -> Color.Red
                        }
                    )
                )
            }
        }
    }

    // Dialog to show enlarged image
    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    Image(
                        bitmap = historyItem.image.asImageBitmap(),
                        contentDescription = "Enlarged Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showDialog = false }, modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

data class HistoryItem(
    val decodedBarcode: String, val grade: String, val thumbnail: Bitmap, val image: Bitmap
)