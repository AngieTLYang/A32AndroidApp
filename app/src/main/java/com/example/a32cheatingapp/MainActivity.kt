package com.example.a32cheatingapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.a32cheatingapp.ui.theme.A32CheatingAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            A32CheatingAppTheme {
                // UI can just be empty or a text
            }
        }

        val laptopIP = "10.130.76.4"  // replace with your laptop's IP
        val port = 12345

        Log.d("SocketDebug", "App started, preparing to connect to $laptopIP:$port")

        CoroutineScope(Dispatchers.IO).launch {
            sendHelloWorld(laptopIP, port)
        }
    }
}

fun sendHelloWorld(host: String, port: Int) {
    try {
        Log.d("SocketDebug", "Attempting to connect to server at $host:$port")

        Socket(host, port).use { socket ->
            Log.d("SocketDebug", "Connected to server")

            val out: OutputStream = socket.getOutputStream()
            val message = "Hello from Android!".toByteArray()
            Log.d("SocketDebug", "Message prepared: ${String(message)}")

            val lengthBytes = ByteBuffer.allocate(4).putInt(message.size).array()
            Log.d("SocketDebug", "Message length (bytes): ${message.size}, Raw lengthBytes: ${lengthBytes.joinToString()}")

            out.write(lengthBytes)  // send length first
            Log.d("SocketDebug", "Sent message length to server")

            out.write(message)      // then send message
            Log.d("SocketDebug", "Sent actual message to server")

            out.flush()
            Log.d("SocketDebug", "Flushed output stream")
        }

        Log.d("SocketDebug", "Message successfully sent, socket closed")
    } catch (e: Exception) {
        Log.e("SocketDebug", "Error during socket communication", e)
    }
}
