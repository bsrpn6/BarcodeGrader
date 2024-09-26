package me.brandonray.barcodegrader.ui.screen

import android.Manifest
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import me.brandonray.barcodegrader.ui.CameraPreview
import me.brandonray.barcodegrader.ui.viewmodel.BarcodeViewModel

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    viewModel: BarcodeViewModel = hiltViewModel(),
    navController: NavHostController,
    isSubscribed: Boolean
) {
    val barcodeResult by viewModel.barcodeResult.collectAsState()
    val errorState by viewModel.errorState.collectAsState()
    val barcodePoints by viewModel.barcodePoints.collectAsState()

    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    val isBarcodeProcessed = remember { mutableStateOf(false) }
    val savedImagePath = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Barcode Scanner") },
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
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isBarcodeProcessed.value && savedImagePath.value != null) {
                    // Display the saved image instead of the camera preview
                    Image(
                        bitmap = BitmapFactory.decodeFile(savedImagePath.value).asImageBitmap(),
                        contentDescription = "Captured Barcode Image",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        CameraPreview(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                            onBarcodeDetected = { result ->
                                if (result != null) {
                                    isBarcodeProcessed.value = true
                                }
                            },
                            onPreviewSizeChanged = { _, _ -> /* Handle size change if needed */ },
                            isBarcodeProcessed = isBarcodeProcessed.value // Pass the state
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                barcodeResult?.let {
                    Text(text = "Barcode Grade: $it")
                }

                errorState?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = {
                        isBarcodeProcessed.value = false
                        savedImagePath.value = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Scan Again")
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Camera permission is required to scan barcodes.")
        }
    }
}