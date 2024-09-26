package me.brandonray.barcodegrader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object BarcodeProcessorMLKit {
    private const val TAG = "BarcodeProcessor"

    // Track if a sharp image with a valid barcode has been captured
    private var isSharpImageCaptured = false

    @OptIn(ExperimentalGetImage::class)
    fun processAndGradeBarcode(
        imageProxy: ImageProxy,
        context: Context,
        onResult: (Bitmap?, BarcodeProcessingResult?) -> Unit
    ) {
        if (isSharpImageCaptured) {
            Log.d(TAG, "Sharp image already captured, skipping further captures.")
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val mediaImage = imageProxy.image

        if (mediaImage == null) {
            Log.e(TAG, "No image found in the ImageProxy.")
            imageProxy.close()
            onResult(null, null)
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, rotation)
        val bitmap = convertImageProxyToBitmap(imageProxy)

        // Ensure the image is sharp before continuing
        if (!isImageSharp(bitmap)) {
            Log.e(TAG, "Image is not sharp enough.")
            imageProxy.close()
            onResult(null, null)
            return
        }

        // Image is sharp, now proceed to barcode scanning
        val scanner = BarcodeScanning.getClient()

        scanner.process(image).addOnSuccessListener { barcodes ->
            if (barcodes.isNotEmpty()) {
                val barcode = barcodes.first()
                val rawValue = barcode.rawValue
                val boundingBox = barcode.cornerPoints?.map { Pair(it.x, it.y) }

                // Rotate the bitmap based on the image rotation
                val rotatedBitmap = rotateBitmap(bitmap, rotation)

                // Crop the bitmap to just the barcode area
                val croppedBitmap = cropBitmap(rotatedBitmap, boundingBox)

                // Extract the number of bars based on the barcode format
                val numberOfBars = croppedBitmap?.let { countBarcodeBars(it) }
                Log.d(TAG, "Number of bars: $numberOfBars")


                val croppedBitmapForGrading =
                    croppedBitmap?.let { cropBarcodeUsingThresholding(it) } // Isolate the bars

                // Grade the barcode using the cropped bitmap
                val grade = gradeBarcode(croppedBitmapForGrading)

                // Apply the overlay to the cropped image
                val overlayBitmap = croppedBitmap?.let {
                    val overlayCopy = it.copy(Bitmap.Config.ARGB_8888, true)
                    drawBoundingBoxOverlay(overlayCopy, boundingBox ?: emptyList(), rawValue)
                    overlayCopy
                }

                // Mark that we've successfully captured a sharp image with a valid barcode
                isSharpImageCaptured = true

                // Pass the sharp image with overlay to be displayed and saved
                onResult(
                    overlayBitmap, BarcodeProcessingResult(
                        rawValue, boundingBox, grade, rotatedBitmap.width, rotatedBitmap.height
                    )
                )

                // Save the image with overlay
                if (croppedBitmapForGrading != null) {
                    saveOriginalWithOverlay(croppedBitmapForGrading, context)
                }

            } else {
                Log.e(TAG, "No barcode found in the image.")
                onResult(null, null)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error detecting barcode: ${e.message}", e)
            onResult(null, null)
        }.addOnCompleteListener {
            imageProxy.close()
        }
    }

    private fun isImageSharp(bitmap: Bitmap): Boolean {
        // Convert Bitmap to Mat
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC1)
        Utils.bitmapToMat(bitmap, mat)

        // Apply the Laplacian filter to the image
        val laplacian = Mat()
        Imgproc.Laplacian(mat, laplacian, CvType.CV_64F)

        // Prepare MatOfDouble objects to hold the mean and stddev values
        val mean = MatOfDouble()
        val stddev = MatOfDouble()

        // Calculate mean and standard deviation of the Laplacian
        Core.meanStdDev(laplacian, mean, stddev)

        // Calculate the variance, which is the square of the standard deviation
        val variance = stddev.toArray()[0] * stddev.toArray()[0]

        // Define a threshold for sharpness; adjust based on testing
        val sharpnessThreshold = 100.0

        // Return whether the image is sharp enough based on the variance
        return variance > sharpnessThreshold
    }

    private fun saveOriginalWithOverlay(bitmap: Bitmap, context: Context) {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "OriginalWithOverlay"
        )
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName =
            "ORIGINAL_WITH_OVERLAY_$timestamp.png" // Use PNG format for lossless compression
        val file = File(directory, fileName)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(
                    Bitmap.CompressFormat.PNG, 100, out
                ) // PNG with 100% quality (lossless)
            }
            Log.d(TAG, "Original with overlay saved to ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e("CameraPreview.kt", "Error saving original with overlay: ${e.message}", e)
        }
    }

    private fun countBarcodeBars(bitmap: Bitmap): Int {
        // Convert Bitmap to OpenCV Mat
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

        // Apply binary thresholding to make barcode bars stand out
        val binary = Mat()
        Imgproc.threshold(gray, binary, 128.0, 255.0, Imgproc.THRESH_BINARY_INV)

        // Define a vertical kernel to detect vertical structures (barcode bars)
        val verticalKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, 20.0))

        // Apply morphological operations to emphasize vertical structures (barcode bars)
        val morphed = Mat()
        Imgproc.morphologyEx(binary, morphed, Imgproc.MORPH_CLOSE, verticalKernel)

        // Find contours of vertical bars
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            morphed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )

        // Count the number of distinct vertical bars
        return contours.size
    }

    private fun enhanceSharpness(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Apply a sharpening kernel
        val kernel = Mat(3, 3, CvType.CV_32F)
        kernel.put(0, 0, -1.0, -1.0, -1.0)
        kernel.put(1, 0, -1.0, 9.0, -1.0)
        kernel.put(2, 0, -1.0, -1.0, -1.0)

        val sharpenedMat = Mat()
        Imgproc.filter2D(mat, sharpenedMat, mat.depth(), kernel)

        val sharpenedBitmap =
            Bitmap.createBitmap(sharpenedMat.cols(), sharpenedMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(sharpenedMat, sharpenedBitmap)

        return sharpenedBitmap
    }

    private fun convertImageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val width = imageProxy.width
        val height = imageProxy.height
        val argbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        var yp = 0
        val frameSize = width * height
        for (j in 0 until height) {
            for (i in 0 until width) {
                val y = nv21[yp].toInt() and 0xff

                // Calculate UV index and ensure it's within bounds
                val uvIndex = frameSize + (j shr 1) * width + (i and 1.inv())
                if (uvIndex + 1 >= nv21.size) {
                    break // Prevent out-of-bounds access
                }

                val v = nv21[uvIndex].toInt() and 0xff
                val u = nv21[uvIndex + 1].toInt() and 0xff

                val pixel = yuvToRgb(y, u, v)
                argbBitmap.setPixel(i, j, pixel)
                yp++
            }
        }
        return argbBitmap
    }

    private fun yuvToRgb(y: Int, u: Int, v: Int): Int {
        val y1 = if (y < 16) 16 else y
        val c = y1 - 16
        val d = u - 128
        val e = v - 128

        val r = (298 * c + 409 * e + 128) shr 8
        val g = (298 * c - 100 * d - 208 * e + 128) shr 8
        val b = (298 * c + 516 * d + 128) shr 8

        return Color.rgb(
            r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255)
        )
    }

    private fun cropBitmap(
        bitmap: Bitmap,
        boundingBox: List<Pair<Int, Int>>?,
        padding: Int = 5  // Add a small padding to include the entire barcode
    ): Bitmap? {
        if (boundingBox == null || boundingBox.size != 4) return null

        val minX = boundingBox.minOf { it.first } - padding
        val minY = boundingBox.minOf { it.second } - padding
        val maxX = boundingBox.maxOf { it.first } + padding
        val maxY = boundingBox.maxOf { it.second } + padding

        val width = maxX - minX
        val height = maxY - minY

        return try {
            Bitmap.createBitmap(bitmap, minX.coerceAtLeast(0), minY.coerceAtLeast(0), width, height)
        } catch (e: IllegalArgumentException) {
            Log.e("BarcodeProcessor", "Error during bitmap cropping: ${e.message}")
            null
        }
    }

    private fun cropBitmapForGrading(
        bitmap: Bitmap, boundingBox: List<Pair<Int, Int>>, padding: Int = 0
    ): Bitmap? {
        if (boundingBox.size != 4) return null

        val minX = boundingBox.minOf { it.first } - padding
        val maxX = boundingBox.maxOf { it.first } + padding

        // Dynamically calculate the start and end of the barcode bars
        val (topY, bottomY) = findBarcodeTopAndBottom(bitmap, boundingBox)

        // Exclude numbers at the bottom (roughly 15% of the height of the barcode)
        val heightWithoutNumbers = bottomY - topY
        val adjustedBottomY = bottomY - (heightWithoutNumbers * 0.15).toInt()

        val width = maxX - minX
        val height = adjustedBottomY - topY

        return try {
            Bitmap.createBitmap(bitmap, minX.coerceAtLeast(0), topY.coerceAtLeast(0), width, height)
        } catch (e: IllegalArgumentException) {
            Log.e("BarcodeProcessor", "Error during bitmap cropping: ${e.message}")
            null
        }
    }

    // Detects the top and bottom of the barcode bars based on contrast
    private fun findBarcodeTopAndBottom(
        bitmap: Bitmap, boundingBox: List<Pair<Int, Int>>
    ): Pair<Int, Int> {
        val minY = boundingBox.minOf { it.second }
        val maxY = boundingBox.maxOf { it.second }

        // Scan for areas with high contrast in the Y direction to detect barcode region
        val topY = findTopOfBarcodeBars(bitmap, minY, maxY)
        val bottomY = findBottomOfBarcodeBars(bitmap, minY, maxY)

        return Pair(topY, bottomY)
    }

    // Detect the top of the barcode bars
    private fun findTopOfBarcodeBars(bitmap: Bitmap, minY: Int, maxY: Int): Int {
        for (y in minY..maxY) {
            if (isHighContrastRow(bitmap, y)) {
                return y // Return the first row with high contrast
            }
        }
        return minY // Default to the top bounding box value
    }

    // Detect the bottom of the barcode bars
    private fun findBottomOfBarcodeBars(bitmap: Bitmap, minY: Int, maxY: Int): Int {
        for (y in maxY downTo minY) {
            if (isHighContrastRow(bitmap, y)) {
                return y // Return the last row with high contrast
            }
        }
        return maxY // Default to the bottom bounding box value
    }

    // Detects high contrast row for barcode bars
    private fun isHighContrastRow(bitmap: Bitmap, y: Int): Boolean {
        var prevPixel = bitmap.getPixel(0, y)
        for (x in 1 until bitmap.width) {
            val currentPixel = bitmap.getPixel(x, y)
            if (abs(prevPixel - currentPixel) > 70) { // Adjust contrast threshold as needed
                return true
            }
            prevPixel = currentPixel
        }
        return false
    }

    private fun cropBarcodeUsingThresholding(bitmap: Bitmap): Bitmap? {
        // Convert Bitmap to OpenCV Mat
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

        // Apply Gaussian blur to reduce noise
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        // Apply binary thresholding
        val binary = Mat()
        Imgproc.threshold(gray, binary, 100.0, 255.0, Imgproc.THRESH_BINARY_INV)

        // Find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )

        // Find the largest contour, which should correspond to the barcode
        var barcodeContour: MatOfPoint? = null
        var maxArea = 0.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > maxArea) {
                maxArea = area
                barcodeContour = contour
            }
        }

        // If no barcode contour found, return null
        if (barcodeContour == null) {
            Log.e("BarcodeProcessor", "No barcode found.")
            return null
        }

        // Get bounding rectangle around the barcode
        val boundingRect = Imgproc.boundingRect(barcodeContour)

        // Crop the bitmap using the bounding rectangle (excluding numbers and white space)
        val croppedBitmap = Bitmap.createBitmap(
            bitmap, boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height
        )

        return croppedBitmap
    }

    private fun isolateBarcodeBars(bitmap: Bitmap, numberOfBars: Int): Bitmap? {
        // Convert Bitmap to OpenCV Mat
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

        // Apply binary thresholding to make barcode bars stand out
        val binary = Mat()
        Imgproc.threshold(gray, binary, 128.0, 255.0, Imgproc.THRESH_BINARY_INV)

        // Define a vertical kernel to detect vertical structures (barcode bars)
        val verticalKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, 20.0))

        // Apply morphological operations to emphasize vertical structures (barcode bars)
        val morphed = Mat()
        Imgproc.morphologyEx(binary, morphed, Imgproc.MORPH_CLOSE, verticalKernel)

        // Find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            morphed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )

        // Filter contours based on the number of bars detected by ML Kit
        val filteredContours = contours.filter { contour ->
            val rect = Imgproc.boundingRect(contour)
            rect.height > src.height() / 3 // Keep only tall contours, likely representing barcode bars
        }

        // If the number of contours matches the number of bars detected by ML Kit, proceed
        if (filteredContours.size == numberOfBars) {
            // Get the bounding rectangle around the filtered contours
            val boundingRect =
                Imgproc.boundingRect(MatOfPoint(*filteredContours.flatMap { it.toList() }
                    .toTypedArray()))

            // Crop the image to the bounding rectangle containing the barcode bars
            val croppedBitmap = Bitmap.createBitmap(
                bitmap, boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height
            )

            return croppedBitmap
        } else {
            // If the number of detected contours doesn't match the expected number of bars, return null
            return null
        }
    }

    fun cropBarcodeUsingFixedRegion(bitmap: Bitmap): Bitmap {
        // Get dimensions of the image
        val width = bitmap.width
        val height = bitmap.height

        // Assuming the barcode is centered, define a region of interest (ROI)
        // You may adjust these values based on the size of the barcode in the image
        val left = (width * 0.2).toInt()  // Start cropping 20% from the left
        val right = (width * 0.8).toInt() // End cropping 80% from the left
        val top = (height * 0.4).toInt()  // Start cropping 40% from the top
        val bottom = (height * 0.6).toInt() // End cropping 60% from the top

        // Crop the image using the calculated ROI
        val croppedBitmap = Bitmap.createBitmap(
            bitmap, left, top, right - left, bottom - top
        )

        return croppedBitmap
    }

    private fun cropBarcodeUsingOpenCV(bitmap: Bitmap, context: Context): Bitmap? {
        // Convert Bitmap to OpenCV Mat
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

        // Apply Gaussian blur to reduce noise
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        // Apply Canny edge detection with adjusted thresholds
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 30.0, 100.0)

        // Apply morphological transformations to close gaps
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
        val morphedEdges = Mat()
        Imgproc.morphologyEx(edges, morphedEdges, Imgproc.MORPH_CLOSE, kernel)

        // Find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            morphedEdges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )

        // DEBUG: Log the number of contours found
        Log.d("BarcodeProcessor", "Number of contours found: ${contours.size}")

        // Create a copy of the source image to draw contours
        val contourImage = Mat()
        src.copyTo(contourImage)

        // Iterate over contours and draw them
        for (contour in contours) {
            val rect = Imgproc.boundingRect(contour)
            Imgproc.rectangle(contourImage, rect.tl(), rect.br(), Scalar(0.0, 255.0, 0.0), 2)
            Log.d(
                "BarcodeProcessor",
                "Contour - Width: ${rect.width}, Height: ${rect.height}, Aspect Ratio: ${rect.width.toDouble() / rect.height.toDouble()}, Area: ${
                    Imgproc.contourArea(contour)
                }"
            )
        }

        // Save the contour image for inspection
        saveMatAsImage(contourImage, "detected_contours.png", context)

        // Now, select the best contour based on stricter criteria
        var barcodeContour: MatOfPoint? = null
        var maxArea = 0.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            val rect = Imgproc.boundingRect(contour)
            val aspectRatio = rect.width.toDouble() / rect.height.toDouble()

            // Stricter filtering criteria, ensuring the contour represents the barcode
            if (aspectRatio > 2.0 && aspectRatio < 5.0 && area > 1500 && rect.width > 100 && rect.height > 20) {
                // Ensure the contour isn't located in the top part where numbers typically are
                if (rect.y > bitmap.height / 4) {
                    maxArea = area
                    barcodeContour = contour
                }
            }
        }

        // If no valid barcode contour is found, return null
        if (barcodeContour == null) {
            Log.e("BarcodeProcessor", "No valid barcode contour found.")
            return null
        }

        // Get bounding rectangle around the selected contour
        var boundingRect = Imgproc.boundingRect(barcodeContour)

        // Manually adjust the bounding box to exclude the numbers above and below the barcode
        val topPadding = boundingRect.height / 6  // Slightly increase top padding
        boundingRect.y += topPadding
        boundingRect.height -= topPadding

        // Manually set a limit for the bottom of the barcode, ignoring the numbers
        val bottomLimit = boundingRect.y + boundingRect.height - (boundingRect.height / 3)
        boundingRect.height = bottomLimit - boundingRect.y

        // Ensure the bounding box is still within the image bounds
        boundingRect.y = boundingRect.y.coerceAtLeast(0)
        boundingRect.height =
            (boundingRect.height + boundingRect.y).coerceAtMost(bitmap.height) - boundingRect.y

        // Crop the bitmap using the adjusted bounding rectangle
        val croppedBitmap = Bitmap.createBitmap(
            bitmap, boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height
        )

        return croppedBitmap
    }

    // Helper function to save Mat as image (for debugging)
    private fun saveMatAsImage(mat: Mat, fileName: String, context: Context) {
        // Get the external files directory specific to this app
        val outputDir = File(context.getExternalFilesDir(null), "OpenCV_Debug")
        if (!outputDir.exists()) {
            outputDir.mkdirs()  // Create the directory if it doesn't exist
        }

        val file = File(outputDir, fileName)
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                Log.d("BarcodeProcessor", "Saved debug image to: ${file.absolutePath}")
            }
        } catch (e: IOException) {
            Log.e("BarcodeProcessor", "Error saving image: ${e.message}")
        }
    }

    // Assuming you already have a decoded barcode from ML Kit
    private fun getNumberOfBars(barcode: Barcode): Int {
        return when (barcode.format) {
            Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A -> 95  // EAN-13 and UPC-A have 95 bars
            Barcode.FORMAT_CODE_128 -> calculateCode128Bars(barcode.rawValue ?: "")
            // Add more formats as needed
            else -> 0  // Unknown or unsupported format
        }
    }

    // For Code 128, calculate the number of bars based on the data (simplified calculation)
    fun calculateCode128Bars(data: String): Int {
        // Each character in Code 128 is represented by 11 bars (3 wide, 3 narrow, and 5 spaces)
        val numberOfCharacters = data.length
        return numberOfCharacters * 11 + 13  // +13 for start, stop, and quiet zones
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun gradeBarcode(bitmap: Bitmap?): String {
        if (bitmap == null) return "F"

        // Use the contrast and edge detection methods to enhance the grading process
        val contrast = calculateContrast(bitmap)
        val edgeCount = detectEdges(bitmap)

        // Define thresholds based on your testing and quality expectations
        val highContrastThreshold = 0.5
        val highEdgeCountThreshold = 200

        var totalLuminance = 0L
        var minLuminance = 255
        var maxLuminance = 0
        var noiseLevel = 0

        // Analyzing the Bitmap pixels
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val luminance =
                    (0.299 * ((pixel shr 16) and 0xFF) + 0.587 * ((pixel shr 8) and 0xFF) + 0.114 * (pixel and 0xFF)).toInt()
                totalLuminance += luminance
                if (luminance < minLuminance) minLuminance = luminance
                if (luminance > maxLuminance) maxLuminance = luminance

                // Noise level analysis: simple variance check
                if (x > 1 && y > 1) {
                    val topPixel = bitmap.getPixel(x, y - 1)
                    val topLuminance =
                        (0.299 * ((topPixel shr 16) and 0xFF) + 0.587 * ((topPixel shr 8) and 0xFF) + 0.114 * (topPixel and 0xFF)).toInt()
                    if (abs(luminance - topLuminance) > 15) {
                        noiseLevel++
                    }
                }
            }
        }

        val luminanceContrast = maxLuminance - minLuminance
        val averageLuminance = totalLuminance / (bitmap.width * bitmap.height)

        Log.d(
            TAG,
            "Min Luminance: $minLuminance, Max Luminance: $maxLuminance, Contrast: $luminanceContrast, Edges: $edgeCount, Noise: $noiseLevel"
        )

        // Grading based on contrast, luminance consistency, edge detection, and noise level
        return when {
            contrast > highContrastThreshold && edgeCount > highEdgeCountThreshold && luminanceContrast > 128 && noiseLevel < (bitmap.width * bitmap.height / 100) -> "A"
            contrast > highContrastThreshold / 2 && edgeCount > highEdgeCountThreshold / 2 && luminanceContrast > 64 && noiseLevel < (bitmap.width * bitmap.height / 50) -> "B"
            contrast > highContrastThreshold / 4 && edgeCount > highEdgeCountThreshold / 4 && luminanceContrast > 32 && noiseLevel < (bitmap.width * bitmap.height / 30) -> "C"
            else -> "D"
        }
    }

    private fun calculateContrast(bitmap: Bitmap): Double {
        var maxLuminance = 0
        var minLuminance = 255
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val luminance =
                    (0.299 * (pixel shr 16 and 0xff) + 0.587 * (pixel shr 8 and 0xff) + 0.114 * (pixel and 0xff)).toInt()
                if (luminance > maxLuminance) maxLuminance = luminance
                if (luminance < minLuminance) minLuminance = luminance
            }
        }
        return (maxLuminance - minLuminance) / 255.0
    }

    private fun detectEdges(bitmap: Bitmap): Int {
        var edgeCount = 0
        for (y in 0 until bitmap.height) {
            for (x in 1 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val previousPixel = bitmap.getPixel(x - 1, y)
                if (abs(pixel - previousPixel) > 50) {
                    edgeCount++
                }
            }
        }
        return edgeCount
    }

    private fun drawBoundingBoxOverlay(
        bitmap: Bitmap, boundingBox: List<Pair<Int, Int>>, decodedValue: String?
    ) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        val textPaint = Paint().apply {
            color = Color.RED
            textSize = 50f
            style = Paint.Style.FILL
        }

        // Draw the bounding box
        if (boundingBox.size == 4) {
            val path = Path().apply {
                moveTo(boundingBox[0].first.toFloat(), boundingBox[0].second.toFloat())
                lineTo(boundingBox[1].first.toFloat(), boundingBox[1].second.toFloat())
                lineTo(boundingBox[2].first.toFloat(), boundingBox[2].second.toFloat())
                lineTo(boundingBox[3].first.toFloat(), boundingBox[3].second.toFloat())
                close()
            }
            canvas.drawPath(path, paint)
        }

        // Draw the decoded barcode value or a simple representation inside the bounding box
        decodedValue?.let {
            canvas.drawText(
                it, boundingBox[0].first.toFloat(), boundingBox[0].second.toFloat() - 10, textPaint
            )
        }

        // Optionally: Draw a representation of the barcode bars (simple vertical lines for illustration)
        val barcodeRepresentationPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
        }

        val barcodeWidth = boundingBox[2].first - boundingBox[0].first
        val barcodeHeight = boundingBox[2].second - boundingBox[0].second

        for (i in 0 until barcodeWidth step 10) {
            val x = boundingBox[0].first + i
            canvas.drawLine(
                x.toFloat(),
                boundingBox[0].second.toFloat(),
                x.toFloat(),
                (boundingBox[0].second + barcodeHeight).toFloat(),
                barcodeRepresentationPaint
            )
        }
    }
}

data class BarcodeProcessingResult(
    val rawValue: String?, val boundingBox: List<Pair<Int, Int>>?, // Points as pairs
    val grade: String?, val imageWidth: Int, val imageHeight: Int
)