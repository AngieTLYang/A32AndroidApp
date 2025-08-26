// MainActivity.kt
package com.example.a32cheatingapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.PermissionChecker
import com.example.a32cheatingapp.ui.theme.A32CheatingAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy.PlaneProxy

class MainActivity : ComponentActivity() {

    private val TAG = "SocketCamera"
    private lateinit var imageCapture: ImageCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            A32CheatingAppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text("Sending text and capturing image...")
                }
            }
        }

        // Send text first
        lifecycleScope.launch(Dispatchers.IO) {
            sendHelloWorld("10.130.76.4", 12345)
        }

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            val requestPermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) startCamera()
                    else Log.e(TAG, "Camera permission denied")
                }
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)
                // Capture a single image
                captureAndSendImage()
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndSendImage() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val jpegBytes = imageProxyToJpeg(image)
                        sendBytes("10.130.76.4", 12345, jpegBytes)
                        image.close()
                        Log.d(TAG, "Image captured and sent")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed", exception)
                }
            }
        )
    }

    private fun imageProxyToJpeg(image: ImageProxy): ByteArray {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    private fun sendHelloWorld(host: String, port: Int) {
        try {
            Socket(host, port).use { socket ->
                val out: OutputStream = socket.getOutputStream()
                val message = "Hello from Android!".toByteArray()
                val lengthBytes = ByteBuffer.allocate(4).putInt(message.size).array()
                out.write(lengthBytes)
                out.write(message)
                out.flush()
            }
            Log.d(TAG, "Hello sent")
        } catch (e: Exception) {
            Log.e(TAG, "Socket error", e)
        }
    }

    private fun sendBytes(host: String, port: Int, data: ByteArray) {
        try {
            Socket(host, port).use { socket ->
                val out: OutputStream = socket.getOutputStream()
                val lengthBytes = ByteBuffer.allocate(4).putInt(data.size).array()
                out.write(lengthBytes)
                out.write(data)
                out.flush()
            }
            Log.d(TAG, "Bytes sent: ${data.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Socket error", e)
        }
    }
}
