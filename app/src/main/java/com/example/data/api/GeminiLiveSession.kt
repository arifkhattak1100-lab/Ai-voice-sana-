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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

enum class GeminiLiveConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

/**
 * Gemini Live Session Manager - Real bidirectional WebSocket connection for Gemini Live.
 * Implements Google's BidiGenerateContent protocol with real 16kHz PCM streaming and 24kHz audio output.
 */
class GeminiLiveSession(
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onStateChanged: (GeminiLiveConnectionState) -> Unit,
    private val onTurnComplete: () -> Unit = {},
    private val onInterrupted: () -> Unit = {},
    private val onTranscriptReceived: (String) -> Unit = {},
    private val onError: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "SanaLive"
        private const val MODEL_PRIMARY = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        private const val MODEL_FALLBACK = "models/gemini-2.0-flash-exp"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Bidirectional stream with no read timeout
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()
    private var webSocket: WebSocket? = null
    private var state = GeminiLiveConnectionState.DISCONNECTED
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val scope = CoroutineScope(Dispatchers.IO)
    private var activeVoiceName = "Kore"
    private var currentModel = MODEL_PRIMARY

    @Volatile
    private var isSetupComplete = false
    private val pendingAudioQueue = ConcurrentLinkedQueue<ByteArray>()

    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }
    }

    fun isApiKeyConfigured(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun connect(voiceName: String = "Kore") {
        mutex.withLock {
            activeVoiceName = voiceName
            if (!isApiKeyConfigured()) {
                val errorMsg = "GEMINI_API_KEY is not configured in AI Studio Secrets panel."
                Log.e(TAG, "ERROR: $errorMsg")
                setError(errorMsg)
                return@withLock
            }

            if (state == GeminiLiveConnectionState.CONNECTING || state == GeminiLiveConnectionState.CONNECTED) {
                Log.d(TAG, "Already connected or connecting to Gemini Live")
                return@withLock
            }

            setState(GeminiLiveConnectionState.CONNECTING)
            isSetupComplete = false
            reconnectAttempts = 0
            performConnect(voiceName, currentModel)
        }
    }

    private suspend fun performConnect(voiceName: String, modelName: String) = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey()
            val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"

            // Setup message conforming to official BidiGenerateContentSetup
            val setupMessage = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", modelName)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            put("AUDIO")
                        })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", voiceName)
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put(
                                    "text",
                                    "You are SANA, a cute, warm, sweet, loving Android AI companion and voice assistant. " +
                                            "You address the user as Boss. Respond in spoken audio naturally, expressively, and concisely. " +
                                            "Do not output markdown formatting or long monologues; keep responses conversational and sweet."
                                )
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "LIVE_CONNECTED: WebSocket open, sending BidiGenerateContentSetup with model $modelName, voice $voiceName")
                    scope.launch {
                        mutex.withLock {
                            // Send initial configuration setup
                            webSocket.send(setupMessage.toString())
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)

                        // Handle BidiGenerateContentSetupComplete
                        if (json.has("setupComplete")) {
                            Log.d(TAG, "LIVE_CONNECTED: setupComplete received from Gemini Live server")
                            isSetupComplete = true
                            setState(GeminiLiveConnectionState.CONNECTED)
                            reconnectAttempts = 0
                            flushPendingAudio()
                            return
                        }

                        val serverContent = json.optJSONObject("serverContent")
                        if (serverContent != null) {
                            val isInterrupted = serverContent.optBoolean("interrupted", false)
                            if (isInterrupted) {
                                Log.d(TAG, "Model interrupted by user barge-in")
                                onInterrupted()
                            }

                            val modelTurn = serverContent.optJSONObject("modelTurn")
                            if (modelTurn != null) {
                                val parts = modelTurn.optJSONArray("parts")
                                if (parts != null) {
                                    val textBuilder = StringBuilder()
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        val inlineData = part.optJSONObject("inlineData")
                                        if (inlineData != null) {
                                            val base64Data = inlineData.optString("data", "")
                                            if (base64Data.isNotBlank()) {
                                                try {
                                                    val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                                    Log.d(TAG, "GEMINI_AUDIO_RECEIVED: ${audioBytes.size} bytes (24kHz PCM)")
                                                    onAudioReceived(audioBytes)
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "ERROR: Decoding PCM audio: ${e.message}")
                                                }
                                            }
                                        }

                                        val transcriptText = part.optString("text", "")
                                        if (transcriptText.isNotBlank()) {
                                            textBuilder.append(transcriptText)
                                        }
                                    }
                                    if (textBuilder.isNotEmpty()) {
                                        onTranscriptReceived(textBuilder.toString())
                                    }
                                }
                            }

                            val isTurnComplete = serverContent.optBoolean("turnComplete", false)
                            if (isTurnComplete) {
                                onTurnComplete()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ERROR: Parsing Gemini Live message: ${e.message}")
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "LIVE_DISCONNECTED: code=$code, reason=$reason")
                    scope.launch {
                        mutex.withLock {
                            isSetupComplete = false
                            setState(GeminiLiveConnectionState.DISCONNECTED)
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "LIVE_DISCONNECTED: code=$code")
                    scope.launch {
                        mutex.withLock {
                            isSetupComplete = false
                            setState(GeminiLiveConnectionState.DISCONNECTED)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val code = response?.code
                    val errorMsg = "Gemini Live failure: ${t.message} (HTTP $code)"
                    Log.e(TAG, "ERROR: $errorMsg")

                    scope.launch {
                        mutex.withLock {
                            isSetupComplete = false
                            setError(errorMsg)
                            // If primary model failed, try fallback model
                            if (currentModel == MODEL_PRIMARY) {
                                Log.w(TAG, "Primary live model failed, switching to fallback model: $MODEL_FALLBACK")
                                currentModel = MODEL_FALLBACK
                            }
                            attemptReconnect()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            val errorMsg = "Failed to connect to Gemini Live: ${e.message}"
            Log.e(TAG, "ERROR: $errorMsg")
            setError(errorMsg)
        }
    }

    private suspend fun attemptReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.w(TAG, "Max reconnection attempts reached")
            return
        }

        reconnectAttempts++
        val backoffMs = (1000L * (1 shl (reconnectAttempts - 1))).coerceAtMost(10000L)
        Log.d(TAG, "Attempting Gemini Live reconnect #$reconnectAttempts in ${backoffMs}ms")

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffMs)
            mutex.withLock {
                if (state == GeminiLiveConnectionState.DISCONNECTED || state == GeminiLiveConnectionState.ERROR) {
                    performConnect(activeVoiceName, currentModel)
                }
            }
        }
    }

    /**
     * Streams real microphone PCM audio (16kHz 16-bit Mono Little-Endian) to Gemini Live.
     * Adheres strictly to official MIME type "audio/pcm;rate=16000".
     */
    fun sendRealtimeAudio(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return

        val ws = webSocket
        if (ws == null || !isSetupComplete || state != GeminiLiveConnectionState.CONNECTED) {
            // Buffer during connection setup handshake so no user speech is dropped
            if (pendingAudioQueue.size < 40) {
                pendingAudioQueue.offer(pcmBytes)
            }
            return
        }

        try {
            val base64Audio = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Audio)
                        })
                    })
                })
            }

            ws.send(json.toString())
            Log.d(TAG, "AUDIO_SENT: ${pcmBytes.size} bytes (audio/pcm;rate=16000)")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR: Sending audio bytes: ${e.message}")
        }
    }

    private fun flushPendingAudio() {
        val ws = webSocket ?: return
        if (!isSetupComplete) return

        var count = 0
        while (pendingAudioQueue.isNotEmpty()) {
            val chunk = pendingAudioQueue.poll() ?: break
            try {
                val base64Audio = Base64.encodeToString(chunk, Base64.NO_WRAP)
                val json = JSONObject().apply {
                    put("realtimeInput", JSONObject().apply {
                        put("mediaChunks", JSONArray().apply {
                            put(JSONObject().apply {
                                put("mimeType", "audio/pcm;rate=16000")
                                put("data", base64Audio)
                            })
                        })
                    })
                }
                ws.send(json.toString())
                count++
            } catch (e: Exception) {
                Log.e(TAG, "ERROR: Flushing buffered audio: ${e.message}")
                break
            }
        }
        if (count > 0) {
            Log.d(TAG, "AUDIO_SENT: Flushed $count buffered audio chunks to Gemini Live")
        }
    }

    /**
     * Sends typed text message into the live session so voice and text share context.
     */
    fun sendTextMessage(text: String) {
        val ws = webSocket
        if (ws == null || !isSetupComplete || state != GeminiLiveConnectionState.CONNECTED) {
            return
        }

        try {
            val json = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", text)
                                })
                            })
                        })
                    })
                    put("turnComplete", true)
                })
            }
            ws.send(json.toString())
            Log.d(TAG, "AUDIO_SENT: Sent text turn to Gemini Live")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR: Sending text turn: ${e.message}")
        }
    }

    suspend fun disconnect() {
        mutex.withLock {
            reconnectJob?.cancel()
            reconnectJob = null
            isSetupComplete = false
            pendingAudioQueue.clear()
            try {
                webSocket?.close(1000, "Normal closure")
            } catch (_: Throwable) {}
            webSocket = null
            setState(GeminiLiveConnectionState.DISCONNECTED)
            Log.d(TAG, "LIVE_DISCONNECTED: Session closed")
        }
    }

    fun isConnected(): Boolean = state == GeminiLiveConnectionState.CONNECTED && isSetupComplete

    fun getState(): GeminiLiveConnectionState = state

    private fun setState(newState: GeminiLiveConnectionState) {
        if (state != newState) {
            state = newState
            onStateChanged(newState)
        }
    }

    private fun setError(message: String) {
        setState(GeminiLiveConnectionState.ERROR)
        onError(message)
    }
}

