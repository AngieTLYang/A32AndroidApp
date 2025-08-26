package com.example.a32cheatingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.a32cheatingapp.ui.theme.A32CheatingAppTheme
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var imageCapture: ImageCapture
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val laptopIP = "10.130.76.4" // replace with your laptop's IP
    private val port = 12345

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            A32CheatingAppTheme { /* empty UI */ }
        }

        // Send "Hello" on app launch
        CoroutineScope(Dispatchers.IO).launch {
            sendHelloWorld()
        }

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(android.util.Size(1080, 1440)) // lower res to speed up sending
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)

            // Start periodic image capture every 8 seconds
            CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    delay(8000)
                    captureAndSendImage()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndSendImage() {
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    println("Image captured, sending...")

                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    CoroutineScope(Dispatchers.IO).launch {
                        sendBytes(bytes)
                    }

                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    println("Capture failed: ${exception.message}")
                }
            })
    }

    private fun sendHelloWorld() {
        try {
            Socket(laptopIP, port).use { socket ->
                println("Sending hello to $laptopIP:$port")
                val out: OutputStream = socket.getOutputStream()
                val message = "Hello from Android!".toByteArray()
                val lengthBytes = ByteBuffer.allocate(4).putInt(message.size).array()
                out.write(lengthBytes)
                out.write(message)
                out.flush()
                println("Hello sent")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendBytes(bytes: ByteArray) {
        try {
            Socket(laptopIP, port).use { socket ->
                val out: OutputStream = socket.getOutputStream()
                val lengthBytes = ByteBuffer.allocate(4).putInt(bytes.size).array()
                out.write(lengthBytes)
                out.write(bytes)
                out.flush()
                println("Image sent: ${bytes.size} bytes")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
