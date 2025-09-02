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
    private val laptopIP = "10.130.76.4"
    private val port = 12345

    private var isCapturing = true
    private var commandSocket: Socket? = null
    private var imageSocket: Socket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { A32CheatingAppTheme { /* empty UI */ } }

        // Connect sockets
        CoroutineScope(Dispatchers.IO).launch { connectCommandSocket() }
        CoroutineScope(Dispatchers.IO).launch { connectImageSocket() }

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(android.util.Size(1080, 1440))
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)

            CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    delay(8000)
                    if (isCapturing) captureAndSendImage()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndSendImage() {
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    CoroutineScope(Dispatchers.IO).launch { sendImage(bytes) }
                    println("Image captured, sent ${bytes.size} bytes")
                }

                override fun onError(exception: ImageCaptureException) {
                    println("Capture failed: ${exception.message}")
                }
            })
    }

    // Persistent command socket
    private fun connectCommandSocket() {
        try {
            commandSocket = Socket(laptopIP, port)
            val reader = commandSocket!!.getInputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val read = reader.read(buffer)
                if (read > 0) {
                    val cmd = String(buffer, 0, read).trim().uppercase()
                    when (cmd) {
                        "PAUSE" -> {
                            isCapturing = false
                            println("Capture paused")
                        }
                        "RESUME" -> {
                            isCapturing = true
                            println("Capture resumed")
                        }
                        else -> println("Unknown command: $cmd")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Persistent image socket
    private fun connectImageSocket() {
        try {
            imageSocket = Socket(laptopIP, port)
            println("Image socket connected")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendImage(bytes: ByteArray) {
        try {
            imageSocket?.let { socket ->
                val out: OutputStream = socket.getOutputStream()
                val lengthBytes = ByteBuffer.allocate(4).putInt(bytes.size).array()
                out.write(lengthBytes)
                out.write(bytes)
                out.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        commandSocket?.close()
        imageSocket?.close()
    }
}