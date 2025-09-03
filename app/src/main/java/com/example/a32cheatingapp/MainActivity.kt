package com.example.a32cheatingapp

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider

import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.a32cheatingapp.ui.theme.A32CheatingAppTheme
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var imageCapture: ImageCapture
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var tts: TextToSpeech
    private val laptopIP = "10.130.76.4"
    private val portImage = 12345
    private val portText = 12346
    @Volatile
    private var isCapturing = true
    private var commandSocket: Socket? = null
    private var imageSocket: Socket? = null

    private val commandReady = CompletableDeferred<Unit>()
    private val imageReady = CompletableDeferred<Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { A32CheatingAppTheme { /* empty UI */ } }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            } else {
                Log.e("TTS", "Initialization failed")
            }
        }

        // Command socket coroutine
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                if (connectCommandSocket()) {
                    if (!commandReady.isCompleted) commandReady.complete(Unit)
                    break
                }
                delay(1000)
            }
        }

        // Image socket coroutine
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                if (connectImageSocket()) {
                    if (!imageReady.isCompleted) imageReady.complete(Unit)
                    break
                }
                delay(1000)
            }
        }

        // Wait for both sockets, then start camera
        CoroutineScope(Dispatchers.Main).launch {
            commandReady.await()
            imageReady.await()
            startCamera()
        }
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

                    if (isCapturing) {
                        CoroutineScope(Dispatchers.IO).launch { sendImage(bytes) }
                        println("Image captured, sent ${bytes.size} bytes")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    println("Capture failed: ${exception.message}")
                }
            })
    }
    private fun sendImage(bytes: ByteArray) {
        if (!isCapturing) return  // skip sending if paused
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

    private fun processCommands(socket: Socket) {
        val reader = socket.getInputStream().bufferedReader()
        try {
            while (true) {
                val line = reader.readLine() ?: break
                Log.d("ClientSocket", "Received raw command: '$line'")
                when (line.trim().uppercase()) {
                    "PAUSE" -> {
                        isCapturing = false
                        Log.d("ClientSocket", "Capture paused")
                    }
                    else -> {
                        // Process the text message
                        processTextMessage(line)

                        // Resume capturing
                        isCapturing = true
                        Log.d("ClientSocket", "Capture resumed automatically")

                        // Send RESUME command back to server
                        sendCommand("RESUME")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ClientSocket", "Error reading commands", e)
        }
    }
    private fun processTextMessage(message: String) {
        Log.d("ClientSocket", "Text message: $message")
        // TODO: update UI, store logs, etc.
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "helloWorldUtterance")
    }
    // Send commands/messages
    fun sendCommand(message: String) {
        try {
            commandSocket?.let { socket ->
                val writer = socket.getOutputStream().bufferedWriter()
                writer.write(message)
                writer.newLine()
                writer.flush()
                Log.d("ClientSocket", "Sent command: $message")
            } ?: Log.e("ClientSocket", "Command socket not connected")
        } catch (e: Exception) {
            Log.e("ClientSocket", "Failed to send command", e)
        }
    }
    private fun connectCommandSocket(): Boolean {
        return try {
            Log.d("ClientSocket", "try connect Command socket")
            commandSocket = Socket(laptopIP, portText)
            Log.d("ClientSocket", "Command socket connected")
            commandSocket?.let { socket ->
                CoroutineScope(Dispatchers.IO).launch {
                    processCommands(socket)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("ClientSocket", "Failed to connect command socket", e)
            false
        }
    }
    // Persistent image socket
    private fun connectImageSocket(): Boolean {
        try {
            if (imageSocket?.isConnected == true) {
                Log.d("ClientSocket", "Image socket already connected")
                return true
            }
            Log.d("ClientSocket", "Trying to connect Image socket...")
            imageSocket = Socket(laptopIP, portImage)
            Log.d("ClientSocket", "Image socket connected")
            return true
        } catch (e: Exception) {
            Log.e("ClientSocket", "Failed to connect image socket", e)
            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        commandSocket?.close()
        imageSocket?.close()
        tts.stop()
        tts.shutdown()
    }
}