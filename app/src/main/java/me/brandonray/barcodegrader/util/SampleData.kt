package me.brandonray.barcodegrader.util

import android.graphics.Bitmap
import me.brandonray.barcodegrader.ui.screen.HistoryItem

object SampleData {
    fun generateSampleHistoryItems(): List<HistoryItem> {
        val sampleThumbnail = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.LTGRAY) // Set the color of the thumbnail
        }

        val sampleImage = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.DKGRAY) // Set the color of the full image
        }

        return List(20) { index ->
            HistoryItem(
                decodedBarcode = "1234567890$index",
                grade = listOf("A", "B", "C", "D", "F").random(),
                thumbnail = sampleThumbnail,
                image = sampleImage
            )
        }
    }
}