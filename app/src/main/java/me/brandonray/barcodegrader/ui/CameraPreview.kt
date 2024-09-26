package me.brandonray.barcodegrader.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import me.brandonray.barcodegrader.ui.viewmodel.BarcodeViewModel
import me.brandonray.barcodegrader.util.BarcodeProcessingResult
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    viewModel: BarcodeViewModel = hiltViewModel(),
    onBarcodeDetected: (BarcodeProcessingResult?) -> Unit,
    onPreviewSizeChanged: (Int, Int) -> Unit,
    isBarcodeProcessed: Boolean
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraExecutor = Executors.newSingleThreadExecutor()

    // State to hold the processed overlay image
    val overlayImage = viewModel.overlayImage.collectAsState()

    if (!isBarcodeProcessed && overlayImage.value == null) { // Continue if no image has been processed
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalyzer = ImageAnalysis.Builder().build().also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            viewModel.scanBarcode(imageProxy, context) { processingResult ->
                                onBarcodeDetected(processingResult)
                            }
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        // Handle exceptions
                    }

                    previewView.post {
                        onPreviewSizeChanged(previewView.width, previewView.height)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }, modifier = modifier.fillMaxSize()
        )
    } else {
        // Display the processed overlay image
        overlayImage.value?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Overlay Barcode Image",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
