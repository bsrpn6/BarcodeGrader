package me.brandonray.barcodegrader.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.brandonray.barcodegrader.util.BarcodeProcessingResult
import me.brandonray.barcodegrader.util.BarcodeProcessorOpenCV

class BarcodeViewModel : ViewModel() {

    private val _barcodeResult = MutableStateFlow<String?>(null)
    val barcodeResult: StateFlow<String?> = _barcodeResult

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState

    private val _barcodePoints = MutableStateFlow<List<Pair<Int, Int>>?>(null)
    val barcodePoints: StateFlow<List<Pair<Int, Int>>?> = _barcodePoints

    private val _overlayImage = MutableStateFlow<Bitmap?>(null)
    val overlayImage: StateFlow<Bitmap?> = _overlayImage

    fun scanBarcode(
        imageProxy: ImageProxy,
        context: Context,
        onBarcodeDetected: (BarcodeProcessingResult?) -> Unit
    ) {
        viewModelScope.launch {
            BarcodeProcessorOpenCV.processAndGradeBarcode(
                imageProxy, context
            ) { bitmap, processingResult ->
                if (bitmap != null) {
                    _overlayImage.value = bitmap // Update the overlay image state
                }
                resetState()
                if (processingResult != null) {
                    _barcodeResult.value = processingResult.grade
                    _barcodePoints.value = processingResult.boundingBox

                    onBarcodeDetected(processingResult)
                } else {
                    _errorState.value = "No barcode found."
                    onBarcodeDetected(null)
                }
            }
        }
    }

    private fun resetState() {
        _barcodeResult.value = null
        _errorState.value = null
        _barcodePoints.value = null
    }
}