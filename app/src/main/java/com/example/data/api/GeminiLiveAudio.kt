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

/**
 * Gemini Live Audio Handler - Direct native audio synthesis with Kore voice support
 * Uses Gemini Native Audio / Gemini Live for production-grade voice output
 * No Android TTS fallback in primary flow - only for diagnostics fallback
 */
class GeminiLiveAudio {

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

    /**
     * Generate native Gemini audio with Kore voice support
     * Prioritizes gemini-2.5-flash-native-audio-preview-12-2025 for Gemini Live
     * Falls back to gemini-2.5-flash-preview-tts if needed
     * Supports all Gemini voice names including "Kore"
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

        // Priority order: Gemini Live native audio first, then TTS fallback
        val modelsToTry = listOf(
            "gemini-2.5-flash-native-audio-preview-12-2025",  // Gemini Live - PRIMARY
            "gemini-2.5-flash-preview-tts"                     // Fallback TTS
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
                                    // Support all Gemini voices including Kore
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
                        Log.w("GeminiLiveAudio", "Model $model returned ${response.code}: $responseStr")
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
                                        Log.d("GeminiLiveAudio", "Successfully generated audio with $model (voice: $voiceName)")
                                        return@withContext Result.success(Pair(rawBytes, mimeType))
                                    }
                                }
                            }
                        }
                    }
                    throw RuntimeException("No audio parts returned in Gemini response.")
                }
            } catch (e: Exception) {
                Log.w("GeminiLiveAudio", "Model $model audio generation attempt failed: ${e.message}")
                lastError = e
            }
        }

        Result.failure(lastError ?: RuntimeException("Unable to generate native audio from Gemini."))
    }
}
