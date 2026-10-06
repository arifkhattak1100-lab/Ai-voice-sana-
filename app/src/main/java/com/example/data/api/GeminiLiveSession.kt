package com.example.data.api

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class GeminiLiveConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

/**
 * Gemini Live Session Manager - Persistent WebSocket connection for real-time audio conversation
 * Features:
 * - Maintains a live session with Gemini API
 * - Handles audio input/output streaming
 * - Automatic reconnection with exponential backoff
 * - Real connection state tracking (CONNECTING, CONNECTED, DISCONNECTED, ERROR)
 */
class GeminiLiveSession(
    private val onAudioReceived: (ByteArray, String) -> Unit,  // callback with audio bytes and mime type
    private val onStateChanged: (GeminiLiveConnectionState) -> Unit,
    private val onError: (String) -> Unit
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // No read timeout for streaming
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val mutex = Mutex()
    private var webSocket: WebSocket? = null
    private var state = GeminiLiveConnectionState.DISCONNECTED
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
    }

    private fun isApiKeyConfigured(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun connect(voiceName: String = "Aoede") {
        mutex.withLock {
            if (!isApiKeyConfigured()) {
                setError("GEMINI_API_KEY is not configured")
                return@withLock
            }

            if (state == GeminiLiveConnectionState.CONNECTING || state == GeminiLiveConnectionState.CONNECTED) {
                Log.w("GeminiLiveSession", "Already connecting or connected")
                return@withLock
            }

            setState(GeminiLiveConnectionState.CONNECTING)
            reconnectAttempts = 0
            performConnect(voiceName)
        }
    }

    private suspend fun performConnect(voiceName: String) = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey()
            val url = "wss://generativelanguage.googleapis.com/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=\$apiKey"

            // Build initial setup message for Gemini Live
            val setupMessage = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", "gemini-2.5-flash")
                    put("generationConfig", JSONObject().apply {
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", voiceName)
                                })
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    Log.d("GeminiLiveSession", "WebSocket connected")
                    scope.launch {
                        mutex.withLock {
                            setState(GeminiLiveConnectionState.CONNECTED)
                            reconnectAttempts = 0

                            // Send setup message
                            webSocket.send(setupMessage.toString())
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        
                        // Parse server-sent message for audio content
                        val serverContent = json.optJSONObject("serverContent")
                        if (serverContent != null) {
                            val modelTurn = serverContent.optJSONObject("modelTurn")
                            if (modelTurn != null) {
                                val parts = modelTurn.optJSONArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        val inlineData = part.optJSONObject("inlineData")
                                        if (inlineData != null) {
                                            val mimeType = inlineData.optString("mimeType", "audio/pcm")
                                            val base64Data = inlineData.optString("data", "")
                                            if (base64Data.isNotBlank()) {
                                                try {
                                                    val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                                    scope.launch {
                                                        onAudioReceived(audioBytes, mimeType)
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("GeminiLiveSession", "Failed to decode audio: \${e.message}")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiLiveSession", "Error parsing server message: \${e.message}")
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("GeminiLiveSession", "WebSocket closing: \$code \$reason")
                    scope.launch {
                        mutex.withLock {
                            setState(GeminiLiveConnectionState.DISCONNECTED)
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("GeminiLiveSession", "WebSocket closed: \$code \$reason")
                    scope.launch {
                        mutex.withLock {
                            if (state != GeminiLiveConnectionState.DISCONNECTED) {
                                setState(GeminiLiveConnectionState.DISCONNECTED)
                            }
                            attemptReconnect(voiceName)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    Log.e("GeminiLiveSession", "WebSocket error: \${t.message}", t)
                    scope.launch {
                        mutex.withLock {
                            setError("WebSocket connection failed: \${t.message}")
                            attemptReconnect(voiceName)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("GeminiLiveSession", "Failed to create WebSocket: \${e.message}", e)
            setError("Connection failed: \${e.message}")
        }
    }

    private suspend fun attemptReconnect(voiceName: String) {
        if (reconnectAttempts >= maxReconnectAttempts) {
            setError("Max reconnection attempts reached")
            return
        }

        reconnectAttempts++
        val backoffMs = (1000 * Math.pow(2.0, (reconnectAttempts - 1).toDouble())).toLong()
            .coerceAtMost(30000)  // Max 30 second backoff

        Log.d("GeminiLiveSession", "Scheduling reconnect attempt \$reconnectAttempts in \${backoffMs}ms")

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffMs)
            mutex.withLock {
                if (state == GeminiLiveConnectionState.DISCONNECTED || state == GeminiLiveConnectionState.ERROR) {
                    Log.d("GeminiLiveSession", "Attempting reconnect \$reconnectAttempts")
                    performConnect(voiceName)
                }
            }
        }
    }

    suspend fun sendAudio(audioBytes: ByteArray, voiceName: String = "Aoede") {
        mutex.withLock {
            if (state != GeminiLiveConnectionState.CONNECTED) {
                Log.w("GeminiLiveSession", "Cannot send audio: not connected. State: \$state")
                return@withLock
            }

            webSocket?.let { ws ->
                try {
                    val audioData = JSONObject().apply {
                        put("clientContent", JSONObject().apply {
                            put("turns", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("parts", JSONArray().apply {
                                        put(JSONObject().apply {
                                            put("inlineData", JSONObject().apply {
                                                put("mimeType", "audio/pcm")
                                                put("data", Base64.encodeToString(audioBytes, Base64.NO_WRAP))
                                            })
                                        })
                                    })
                                })
                            })
                            put("turnComplete", true)
                        })
                    }
                    ws.send(audioData.toString())
                    Log.d("GeminiLiveSession", "Sent audio: \${audioBytes.size} bytes")
                } catch (e: Exception) {
                    Log.e("GeminiLiveSession", "Failed to send audio: \${e.message}", e)
                    setError("Failed to send audio: \${e.message}")
                }
            }
        }
    }

    suspend fun disconnect() {
        mutex.withLock {
            reconnectJob?.cancel()
            webSocket?.close(1000, "User disconnect")
            webSocket = null
            setState(GeminiLiveConnectionState.DISCONNECTED)
        }
    }

    fun getState(): GeminiLiveConnectionState {
        return state
    }

    private fun setState(newState: GeminiLiveConnectionState) {
        if (state != newState) {
            state = newState
            Log.d("GeminiLiveSession", "State changed to: \$newState")
            scope.launch {
                onStateChanged(newState)
            }
        }
    }

    private fun setError(message: String) {
        Log.e("GeminiLiveSession", "Error: \$message")
        setState(GeminiLiveConnectionState.ERROR)
        scope.launch {
            onError(message)
        }
    }
}
