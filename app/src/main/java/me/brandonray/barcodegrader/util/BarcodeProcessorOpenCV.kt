package me.brandonray.barcodegrader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.BarcodeDetector
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BarcodeProcessorOpenCV {
    private const val TAG = "BarcodeProcessorNew"

    fun processAndGradeBarcode(
        imageProxy: ImageProxy,
        context: Context,
        onProcessingComplete: (Bitmap?, BarcodeProcessingResult?) -> Unit
    ) {
        // Convert ImageProxy to Bitmap
        var bitmap = imageProxyToBitmap(imageProxy)

        // Rotate the bitmap if needed
        bitmap = rotateBitmapIfNeeded(bitmap, imageProxy.imageInfo.rotationDegrees)

        Log.d(TAG, "Image rotation degrees: ${imageProxy.imageInfo.rotationDegrees}")

        // Check if the image is sharp enough
        if (!isImageSharp(bitmap)) {
            Log.d(TAG, "Image is not sharp, skipping.")
            imageProxy.close()
            return
        }

        // Save the bitmap for debugging purposes
        saveBitmap(bitmap, context)

        // Convert bitmap to OpenCV Mat
        val mat = bitmapToMat(bitmap)

        // Convert the image to grayscale
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        // Apply histogram equalization to enhance contrast
        val equalized = Mat()
        Imgproc.equalizeHist(gray, equalized)

        // Apply adaptive thresholding to binarize the image
        val thresholded = Mat()
        Imgproc.adaptiveThreshold(
            equalized,
            thresholded,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            11,
            2.0
        )

        // Save the preprocessed image for debugging
        val preprocessedBitmap = matToBitmap(thresholded)
        saveBitmap(preprocessedBitmap, context)

        // Step 1: Use BarcodeDetector to detect and decode the barcode
        val barcodeDetector = BarcodeDetector()

        // Mat to hold decoded barcodes and bounding boxes
        val decodedBarcodes = mutableListOf<String>()
        val boundingBoxes = Mat()

        // Detect and decode the barcode with the preprocessed image
        if (barcodeDetector.detectAndDecodeMulti(thresholded, decodedBarcodes, boundingBoxes)) {
            if (decodedBarcodes.isNotEmpty() && boundingBoxes.rows() > 0) {
                // Process the successfully detected barcodes
                for (i in 0 until boundingBoxes.rows()) {
                    val x = boundingBoxes.get(i, 0)[0].toInt()
                    val y = boundingBoxes.get(i, 1)[0].toInt()
                    val width = boundingBoxes.get(i, 2)[0].toInt()
                    val height = boundingBoxes.get(i, 3)[0].toInt()

                    // Ensure bounding box is within image bounds
                    val clampedX = x.coerceIn(0, mat.cols() - 1)
                    val clampedY = y.coerceIn(0, mat.rows() - 1)
                    val clampedWidth = width.coerceIn(1, mat.cols() - clampedX)
                    val clampedHeight = height.coerceIn(1, mat.rows() - clampedY)

                    // Log the extracted bounding box values
                    Log.d(
                        TAG,
                        "Bounding Box: x=$clampedX, y=$clampedY, width=$clampedWidth, height=$clampedHeight"
                    )

                    // Use org.opencv.core.Rect for cropping with OpenCV
                    val boundingRect =
                        org.opencv.core.Rect(clampedX, clampedY, clampedWidth, clampedHeight)
                    val decodedValue = decodedBarcodes[i]

                    // Crop the barcode area from the original image using org.opencv.core.Rect
                    val croppedBarcode = Mat(mat, boundingRect)

                    // Now proceed with the post-processing steps
                    val grayCropped = Mat()
                    Imgproc.cvtColor(croppedBarcode, grayCropped, Imgproc.COLOR_BGR2GRAY)

                    val sobelX = Mat()
                    Imgproc.Sobel(grayCropped, sobelX, CvType.CV_32F, 1, 0)

                    val sobelX8U = Mat()
                    sobelX.convertTo(sobelX8U, CvType.CV_8UC1)

                    val morphKernel =
                        Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(21.0, 7.0))
                    Imgproc.morphologyEx(sobelX8U, sobelX8U, Imgproc.MORPH_CLOSE, morphKernel)
                    Imgproc.morphologyEx(sobelX8U, sobelX8U, Imgproc.MORPH_OPEN, morphKernel)

                    val binary = Mat()
                    Imgproc.threshold(
                        sobelX8U, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU
                    )

                    val processedBitmap = matToBitmap(croppedBarcode)
                    val processingResult = BarcodeProcessingResult(
                        rawValue = decodedValue,
                        boundingBox = listOf(
                            Pair(boundingRect.x, boundingRect.y),
                            Pair(boundingRect.x + boundingRect.width, boundingRect.y),
                            Pair(
                                boundingRect.x + boundingRect.width,
                                boundingRect.y + boundingRect.height
                            ),
                            Pair(boundingRect.x, boundingRect.y + boundingRect.height)
                        ),
                        grade = calculateGrade(decodedValue),
                        imageWidth = boundingRect.width,
                        imageHeight = boundingRect.height
                    )

                    saveBitmap(processedBitmap, context)
                    onProcessingComplete(processedBitmap, processingResult)
                }
            } else {
                Log.d(TAG, "No barcode found in the image.")
            }
        } else {
            Log.d(TAG, "Failed to detect and decode barcode.")
        }

        imageProxy.close()
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) {
            return bitmap  // No rotation needed
        }
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun saveBitmap(bitmap: Bitmap, context: Context) {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "BITMAP"
        )
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "BITMAP$timestamp.png"
        val file = File(directory, fileName)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d(TAG, "BITMAP saved to ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Error saving bitmap: ${e.message}", e)
        }
    }

    private fun isImageSharp(bitmap: Bitmap): Boolean {
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC1)
        Utils.bitmapToMat(bitmap, mat)

        val laplacian = Mat()
        Imgproc.Laplacian(mat, laplacian, CvType.CV_64F)

        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        Core.meanStdDev(laplacian, mean, stddev)

        val variance = stddev.toArray()[0] * stddev.toArray()[0]
        val sharpnessThreshold = 100.0

        return variance > sharpnessThreshold
    }

    @OptIn(ExperimentalGetImage::class)
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        if (imageProxy.format != ImageFormat.YUV_420_888) {
            throw IllegalArgumentException("Expected YUV_420_888 format")
        }

        val image = imageProxy.image ?: throw IllegalArgumentException("Image is null")
        val yuvImage = yuvToYuvImage(image)

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val byteArray = out.toByteArray()

        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    private fun yuvToYuvImage(image: Image): YuvImage {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        return YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    }

    private fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }

    private fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }

    private fun calculateGrade(barcodeValue: String?): String {
        return "A"  // Implement your barcode grading logic here
    }
}