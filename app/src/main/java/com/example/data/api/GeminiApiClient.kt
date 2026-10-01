package com.example.data.api

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
    }

    fun isApiKeyConfigured(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * Pings the Gemini API to verify live connectivity and measure latency.
     */
    suspend fun pingGeminiConnection(): Result<Long> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("GEMINI_API_KEY is not configured in Secrets panel."))
        }

        val startTime = System.currentTimeMillis()
        val models = listOf("gemini-3.8-flash", "gemini-3.5-flash", "gemini-flash-latest")

        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val body = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", "ping") })
                            })
                        })
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val latency = System.currentTimeMillis() - startTime
                        return@withContext Result.success(latency)
                    }
                }
            } catch (e: Exception) {
                Log.w("GeminiApiClient", "Ping model $model failed: ${e.message}")
            }
        }

        Result.failure(RuntimeException("Could not establish live connection to Gemini API."))
    }

    /**
     * Calls Gemini Native TTS/Audio to produce an authentic Gemini voice waveform.
     * Prioritizes high-speed, active 3.8 Flash TTS models with seamless fallback.
     */
    suspend fun generateNativeAudio(
        textToSpeak: String,
        voiceName: String = "Aoede"
    ): Result<Pair<ByteArray, String>> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("GEMINI_API_KEY is not configured. Please set your Gemini API key in AI Studio Secrets panel.")
            )
        }

        val modelsToTry = listOf(
            "gemini-3.8-flash-tts",
            "gemini-3.8-flash-lite-tts",
            "gemini-3.1-flash-tts-preview",
            "gemini-2.5-flash-preview-tts"
        )

        var lastError: Exception? = null

        for (model in modelsToTry) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", textToSpeak)
                                })
                            })
                        })
                    })
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
                }

                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.w("GeminiApiClient", "TTS model $model returned ${response.code}: $responseStr")
                        throw RuntimeException("Gemini Audio error (${response.code}): $responseStr")
                    }

                    val jsonObj = JSONObject(responseStr)
                    val candidates = jsonObj.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                val inlineData = part.optJSONObject("inlineData")
                                if (inlineData != null) {
                                    val mimeType = inlineData.optString("mimeType", "audio/wav")
                                    val base64Data = inlineData.optString("data", "")
                                    if (base64Data.isNotBlank()) {
                                        val rawBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                        return@withContext Result.success(Pair(rawBytes, mimeType))
                                    }
                                }
                            }
                        }
                    }
                    throw RuntimeException("No audio parts returned in Gemini response.")
                }
            } catch (e: Exception) {
                Log.w("GeminiApiClient", "Model $model audio generation attempt failed: ${e.message}")
                lastError = e
            }
        }

        Result.failure(lastError ?: RuntimeException("Unable to generate native audio from Gemini."))
    }

    /**
     * Conversational reasoning using Gemini 3.8 Flash with full context, emotion, personality & tool detection.
     */
    suspend fun generateChatResponse(
        userMessage: String,
        conversationHistory: List<Pair<String, String>>, // sender, text
        systemInstructionText: String,
        imageBase64: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("GEMINI_API_KEY is not configured. Please set your key in AI Studio Secrets.")
            )
        }

        val modelsToTry = listOf(
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-3.5-flash",
            "gemini-flash-latest"
        )

        val contentsArray = JSONArray()

        // Add recent history turns (limit to last 8 turns for latency and token safety)
        val historySlice = if (conversationHistory.size > 8) {
            conversationHistory.takeLast(8)
        } else {
            conversationHistory
        }

        for ((sender, text) in historySlice) {
            val role = if (sender.equals("USER", ignoreCase = true)) "user" else "model"
            contentsArray.put(JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", text) })
                })
            })
        }

        // Current user turn
        val currentUserParts = JSONArray().apply {
            put(JSONObject().apply { put("text", userMessage) })
            if (!imageBase64.isNullOrBlank()) {
                put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", imageBase64)
                    })
                })
            }
        }

        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", currentUserParts)
        })

        val jsonBody = JSONObject().apply {
            put("contents", contentsArray)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstructionText) })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.75)
                put("topP", 0.95)
                put("topK", 40)
            })
        }

        var lastError: Exception? = null

        for (model in modelsToTry) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        throw RuntimeException("Gemini Chat API error ($model ${response.code}): $responseStr")
                    }

                    val jsonObj = JSONObject(responseStr)
                    val candidates = jsonObj.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val reply = parts.getJSONObject(0).optString("text", "")
                            if (reply.isNotBlank()) {
                                return@withContext Result.success(reply)
                            }
                        }
                    }
                    throw RuntimeException("Empty reply from model $model")
                }
            } catch (e: Exception) {
                Log.w("GeminiApiClient", "Chat model $model failed: ${e.message}")
                lastError = e
            }
        }

        Result.failure(lastError ?: RuntimeException("Failed to generate chat response."))
    }

    /**
     * Image generation using gemini-2.5-flash-image
     */
    suspend fun generateImage(prompt: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("GEMINI_API_KEY missing."))
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key=$apiKey"
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("TEXT")
                        put("IMAGE")
                    })
                    put("imageConfig", JSONObject().apply {
                        put("aspectRatio", "1:1")
                        put("imageSize", "1K")
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw RuntimeException("Gemini Image API error (${response.code}): $responseStr")
                }
                val jsonObj = JSONObject(responseStr)
                val candidates = jsonObj.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val base64Data = inlineData.optString("data", "")
                                if (base64Data.isNotBlank()) {
                                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    return@withContext Result.success(bytes)
                                }
                            }
                        }
                    }
                }
                Result.failure(RuntimeException("No image data in Gemini response."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
