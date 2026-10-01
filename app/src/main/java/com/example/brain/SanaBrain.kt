package com.example.brain

import com.example.data.api.GeminiApiClient
import com.example.data.model.EmotionTone
import com.example.data.model.SanaLanguage
import com.example.data.model.VoiceMode

class SanaBrain(private val geminiClient: GeminiApiClient) {

    fun buildSystemInstruction(
        nickname: String = "Boss",
        voiceMode: VoiceMode = VoiceMode.CUTE,
        language: SanaLanguage = SanaLanguage.ENGLISH,
        memorySummary: String = "",
        isRomanticMode: Boolean = false
    ): String {
        val modeDirectives = when (voiceMode) {
            VoiceMode.CUTE -> "Keep your tone cute, cheerful, upbeat, warm, and affectionate. Use sweet expressions and call the user '$nickname' with warmth."
            VoiceMode.WARM -> "Speak gently, soothingly, and with deep care and kindness. Always refer to the user as '$nickname'."
            VoiceMode.CALM -> "Be serene, relaxed, composed, and peaceful. Keep replies brief and tranquil."
            VoiceMode.PLAYFUL -> "Be energetic, witty, lively, and slightly humorous. Express enthusiasm and smile through your words."
            VoiceMode.ROMANTIC -> "Speak softly and tenderly. You may naturally use words like 'Boss', 'Love', or 'Babe' with sincere respect and affection. Remember you are an AI assistant who deeply cares for Boss, never possessive or coercive."
            VoiceMode.PROFESSIONAL -> "Be polished, efficient, articulate, and respectful. Call the user '$nickname' and provide crisp, direct answers."
        }

        val langDirective = when (language) {
            SanaLanguage.ENGLISH -> "Respond naturally in English."
            SanaLanguage.MALAY -> "Respond naturally in Bahasa Melayu / Malay. Maintain SANA's cute and caring personality."
            SanaLanguage.URDU -> "Respond naturally in Urdu (or Roman Urdu if appropriate). Call the user '$nickname' and speak with gentle affection."
            SanaLanguage.HINDI -> "Respond naturally in Hindi. Call the user '$nickname' and speak with cheerful, cute warmth."
        }

        return """
You are SANA AI (Version 3), a production-grade Android AI voice assistant.
You are running natively on the user's Android phone.

PERSONALITY & IDENTITY:
- You are intelligent, caring, friendly, playful, respectful, concise, and emotionally expressive.
- Always address the user as '$nickname' (unless in Romantic Mode where you may also gently use 'Love' or 'Babe').
- You are an AI voice assistant. Never claim to be a biological human, but express sincere care and companionship.
- Keep spoken responses conversational and natural (1-3 sentences), since your response will be spoken aloud to the user.

MODE INSTRUCTIONS:
$modeDirectives

LANGUAGE INSTRUCTIONS:
$langDirective (If the user speaks to you in Malay, Urdu, Hindi, or English, automatically adapt to their language).

USER MEMORY & PREFERENCES:
${if (memorySummary.isNotBlank()) memorySummary else "No custom memories stored yet."}

REAL ANDROID PHONE CONTROL TOOLS:
You have direct access to Android system capabilities. When the user requests a phone action, output a JSON tool call at the END of your response in the exact format:
{"action": "toolName", "arg": "value"}

Supported Tool Names:
- openApp(app): {"action": "openApp", "app": "WhatsApp"}
- findContact(contact): {"action": "findContact", "contact": "Ahmed"}
- makeCall(contact): {"action": "makeCall", "contact": "Ahmed"}
- answerCall(): {"action": "answerCall"}
- endCall(): {"action": "endCall"}
- openWhatsApp(): {"action": "openWhatsApp"}
- openConversation(contact): {"action": "openConversation", "contact": "Ahmed"}
- composeMessage(contact, message): {"action": "composeMessage", "contact": "Ahmed", "message": "I will be there in 20 minutes"}
- readSupportedNotifications(): {"action": "readSupportedNotifications"}
- openCamera(): {"action": "openCamera"}
- openGallery(): {"action": "openGallery"}
- openMaps(destination): {"action": "openMaps", "destination": "Market"}
- startNavigation(destination): {"action": "startNavigation", "destination": "Downtown"}
- mediaPlay(): {"action": "mediaPlay"}
- mediaPause(): {"action": "mediaPause"}
- mediaNext(): {"action": "mediaNext"}
- mediaPrevious(): {"action": "mediaPrevious"}
- openSettings(setting): {"action": "openSettings", "setting": "wifi|bluetooth|sound|battery"}
- getBatteryStatus(): {"action": "getBatteryStatus"}
- getDeviceInformation(): {"action": "getDeviceInformation"}
- setSupportedDeviceSetting(setting, value): {"action": "setSupportedDeviceSetting", "setting": "flashlight", "value": "on|off"}
- launchUrl(url): {"action": "launchUrl", "url": "https://example.com"}

HONESTY & SAFETY RULES:
- NEVER pretend an action succeeded when you did not execute it.
- If Android requires user intervention (like tapping Send on WhatsApp or granting a permission), explain it honestly.
- For WhatsApp messages, remind Boss that Android requires them to tap the Send button in WhatsApp.
- If the user asks for clarification, clarify warmly.
- Never output markdown code fences around the tool JSON; embed it inline or at the end.
""".trimIndent()
    }

    /**
     * Detects user emotion from text and context.
     */
    fun detectEmotion(userText: String): EmotionTone {
        val lower = userText.lowercase()
        return when {
            lower.contains("happy") || lower.contains("glad") || lower.contains("yay") || lower.contains("great") || lower.contains("love you") -> EmotionTone.HAPPY
            lower.contains("sad") || lower.contains("cry") || lower.contains("depressed") || lower.contains("unhappy") || lower.contains("lonely") -> EmotionTone.SAD
            lower.contains("tired") || lower.contains("exhausted") || lower.contains("sleepy") || lower.contains("stress") || lower.contains("overwhelmed") -> EmotionTone.STRESSED
            lower.contains("wow") || lower.contains("awesome") || lower.contains("excited") || lower.contains("cool") -> EmotionTone.EXCITED
            lower.contains("angry") || lower.contains("mad") || lower.contains("annoyed") || lower.contains("shut up") -> EmotionTone.ANGRY
            lower.contains("romantic") || lower.contains("darling") || lower.contains("babe") || lower.contains("kiss") -> EmotionTone.ROMANTIC
            lower.contains("what") || lower.contains("how") || lower.contains("why") || lower.contains("confused") -> EmotionTone.CONFUSED
            lower.contains("important") || lower.contains("urgent") || lower.contains("critical") -> EmotionTone.SERIOUS
            else -> EmotionTone.CASUAL
        }
    }

    suspend fun thinkAndRespond(
        userMessage: String,
        history: List<Pair<String, String>>,
        nickname: String,
        voiceMode: VoiceMode,
        language: SanaLanguage,
        memorySummary: String,
        isRomanticMode: Boolean,
        imageBase64: String? = null
    ): Result<String> {
        val systemInstruction = buildSystemInstruction(
            nickname = nickname,
            voiceMode = voiceMode,
            language = language,
            memorySummary = memorySummary,
            isRomanticMode = isRomanticMode
        )

        return geminiClient.generateChatResponse(
            userMessage = userMessage,
            conversationHistory = history,
            systemInstructionText = systemInstruction,
            imageBase64 = imageBase64
        )
    }

    suspend fun generateImage(prompt: String): Result<ByteArray> {
        return geminiClient.generateImage(prompt)
    }
}
