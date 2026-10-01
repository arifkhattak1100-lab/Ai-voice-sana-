package com.example.data.model

enum class VoiceMode(
    val label: String,
    val description: String,
    val defaultVoiceName: String,
    val pitchMultiplier: Float = 1.0f,
    val speedMultiplier: Float = 1.0f
) {
    CUTE("CUTE", "Soft, sweet, cheerful & charming", "Aoede", 1.25f, 1.05f),
    WARM("WARM", "Gentle, comforting & reassuring", "Autonoe", 1.05f, 0.95f),
    CALM("CALM", "Serene, peaceful & soft-spoken", "Leda", 0.95f, 0.90f),
    PLAYFUL("PLAYFUL", "Bouncy, teasing & energetic", "Aoede", 1.20f, 1.10f),
    ROMANTIC("ROMANTIC ❤️", "Sweet, affectionate & intimate", "Aoede", 1.10f, 0.92f),
    PROFESSIONAL("PROFESSIONAL", "Composed, clear & articulate", "Kore", 1.00f, 1.00f)
}

data class GeminiVoice(
    val name: String,
    val displayName: String,
    val gender: String,
    val description: String,
    val isRecommendedForSana: Boolean = false
) {
    companion object {
        // Official supported voices in Gemini Live & Gemini Native Audio
        val ALL_VOICES = listOf(
            GeminiVoice(
                name = "Aoede",
                displayName = "Aoede (Super Cute Feminine)",
                gender = "Feminine",
                description = "Breezy, clear, sweet & youthful tone. Ideal companion voice for SANA.",
                isRecommendedForSana = true
            ),
            GeminiVoice(
                name = "Kore",
                displayName = "Kore (Natural Feminine)",
                gender = "Feminine",
                description = "Firm, natural, expressive and clear feminine voice.",
                isRecommendedForSana = false
            ),
            GeminiVoice(
                name = "Leda",
                displayName = "Leda (Soft & Gentle)",
                gender = "Feminine",
                description = "Soft, comforting, calm and delicate feminine tone.",
                isRecommendedForSana = false
            ),
            GeminiVoice(
                name = "Autonoe",
                displayName = "Autonoe (Warm & Bright)",
                gender = "Feminine",
                description = "Warm, bright, inviting and friendly conversational delivery.",
                isRecommendedForSana = false
            ),
            GeminiVoice(
                name = "Laomedeia",
                displayName = "Laomedeia (Smooth & Relaxed)",
                gender = "Feminine",
                description = "Smooth, relaxed, melodious and casual feminine tone.",
                isRecommendedForSana = false
            ),
            GeminiVoice(
                name = "Puck",
                displayName = "Puck (Upbeat & Lively)",
                gender = "Masculine/Playful",
                description = "Upbeat, energetic and lively personality voice.",
                isRecommendedForSana = false
            ),
            GeminiVoice(
                name = "Charon",
                displayName = "Charon (Informative & Calm)",
                gender = "Masculine",
                description = "Informative, calm, collected and professional tone.",
                isRecommendedForSana = false
            ),
            GeminiVoice(
                name = "Fenrir",
                displayName = "Fenrir (Passionate & Bold)",
                gender = "Masculine",
                description = "Excitable, bold and dynamic expressive voice.",
                isRecommendedForSana = false
            ),
            GeminiVoice(
                name = "Zephyr",
                displayName = "Zephyr (Calm & Soothing)",
                gender = "Neutral/Gentle",
                description = "Very smooth, mellow and tranquil voice.",
                isRecommendedForSana = false
            )
        )

        fun findByName(name: String): GeminiVoice {
            return ALL_VOICES.find { it.name.equals(name, ignoreCase = true) } ?: ALL_VOICES.first()
        }
    }
}

enum class EmotionTone(val emoji: String, val label: String) {
    HAPPY("😊", "Happy"),
    EXCITED("✨", "Excited"),
    SAD("🥺", "Sad"),
    WORRIED("😟", "Worried"),
    TIRED("🥱", "Tired"),
    STRESSED("😫", "Stressed"),
    FRUSTRATED("😤", "Frustrated"),
    ANGRY("😡", "Angry"),
    CONFUSED("🤔", "Confused"),
    SERIOUS("🧐", "Serious"),
    CALM("🌿", "Calm"),
    CASUAL("🌸", "Casual"),
    PLAYFUL("🎀", "Playful"),
    ROMANTIC("💖", "Romantic")
}

enum class SanaLanguage(val displayName: String, val code: String, val greetingSample: String) {
    ENGLISH("English", "en", "Hey Boss! ❤️"),
    MALAY("Bahasa Melayu", "ms", "Hai Boss! ❤️ Apa khabar?"),
    URDU("اردو (Urdu)", "ur", "ہیلو باس! ❤️ آپ کیسے ہیں؟"),
    HINDI("हिन्दी (Hindi)", "hi", "नमस्ते बॉस! ❤️ आप कैसे हैं?")
}

enum class AudioPlaybackState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

enum class SenderType {
    USER,
    SANA
}

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val sender: SenderType,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val emotion: String? = null,
    val hasAudio: Boolean = false,
    val actionExecuted: String? = null,
    val imageBase64: String? = null
)

data class DiagnosticsState(
    val micWorking: Boolean? = null,
    val geminiConnected: Boolean? = null,
    val liveAudioConnected: Boolean? = null,
    val voiceGenerationWorking: Boolean? = null,
    val audioPlaybackWorking: Boolean? = null,
    val currentVoice: String = "Aoede",
    val latencyMs: Long? = null,
    val lastErrorMessage: String? = null,
    val isRunningTest: Boolean = false
)

data class ToolCallResult(
    val action: String,
    val target: String,
    val success: Boolean,
    val message: String
)
