package me.brandonray.barcodegrader

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader

@HiltAndroidApp
class BarcodeGraderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Failed to initialize OpenCV")
        } else {
            Log.d("OpenCV", "OpenCV initialized successfully")
        }
    }
}