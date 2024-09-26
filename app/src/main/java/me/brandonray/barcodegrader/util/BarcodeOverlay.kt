package me.brandonray.barcodegrader.util

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun BarcodeOverlay(
    resultPoints: List<Pair<Int, Int>>?,
    imageWidth: Int,
    imageHeight: Int,
    previewWidth: Int,
    previewHeight: Int
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        resultPoints?.let { points ->
            if (points.size == 4) {
                // Calculate scale factors
                val scaleX = previewWidth.toFloat() / imageWidth
                val scaleY = previewHeight.toFloat() / imageHeight

                // Ensure correct aspect ratio
                val scale = minOf(scaleX, scaleY)

                // Center the overlay in the preview
                val offsetX = (previewWidth - imageWidth * scale) / 2
                val offsetY = (previewHeight - imageHeight * scale) / 2

                val path = Path().apply {
                    moveTo(
                        offsetX + points[0].first * scale,
                        offsetY + points[0].second * scale
                    )
                    lineTo(
                        offsetX + points[1].first * scale,
                        offsetY + points[1].second * scale
                    )
                    lineTo(
                        offsetX + points[2].first * scale,
                        offsetY + points[2].second * scale
                    )
                    lineTo(
                        offsetX + points[3].first * scale,
                        offsetY + points[3].second * scale
                    )
                    close()
                }

                drawPath(
                    path = path,
                    color = Color.Red,
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}
