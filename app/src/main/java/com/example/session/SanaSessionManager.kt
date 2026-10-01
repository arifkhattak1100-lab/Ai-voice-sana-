package com.example.session

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.CentralSanaAudioManager
import com.example.data.api.GeminiApiClient
import com.example.data.database.ChatMessageEntity
import com.example.data.database.MemoryEntity
import com.example.data.database.SanaRepository
import com.example.data.model.AudioPlaybackState
import com.example.data.model.ChatMessage
import com.example.data.model.DiagnosticsState
import com.example.data.model.EmotionTone
import com.example.data.model.GeminiVoice
import com.example.data.model.SanaLanguage
import com.example.data.model.SenderType
import com.example.data.model.ToolCallResult
import com.example.data.model.VoiceMode
import com.example.tools.AndroidToolsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SanaSessionManager(
    private val context: Context,
    private val repository: SanaRepository
) : ViewModel() {

    private val geminiClient = GeminiApiClient()
    val audioManager = CentralSanaAudioManager(context)
    private val toolsManager = AndroidToolsManager(context)

    // SANA State
    private val _nickname = MutableStateFlow("Boss")
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    private val _selectedVoice = MutableStateFlow(GeminiVoice.findByName("Aoede"))
    val selectedVoice: StateFlow<GeminiVoice> = _selectedVoice.asStateFlow()

    private val _voiceMode = MutableStateFlow(VoiceMode.CUTE)
    val voiceMode: StateFlow<VoiceMode> = _voiceMode.asStateFlow()

    private val _isRomanticMode = MutableStateFlow(false)
    val isRomanticMode: StateFlow<Boolean> = _isRomanticMode.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(SanaLanguage.ENGLISH)
    val selectedLanguage: StateFlow<SanaLanguage> = _selectedLanguage.asStateFlow()

    private val _detectedEmotion = MutableStateFlow<EmotionTone?>(null)
    val detectedEmotion: StateFlow<EmotionTone?> = _detectedEmotion.asStateFlow()

    private val _playbackState = MutableStateFlow(AudioPlaybackState.IDLE)
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private val _isMemoryEnabled = MutableStateFlow(true)
    val isMemoryEnabled: StateFlow<Boolean> = _isMemoryEnabled.asStateFlow()

    private val _wakeWordEnabled = MutableStateFlow(false)
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    private val _backgroundAssistantEnabled = MutableStateFlow(false)
    val backgroundAssistantEnabled: StateFlow<Boolean> = _backgroundAssistantEnabled.asStateFlow()

    // Voice test and error feedback
    private val _voiceTestPassed = MutableStateFlow(false)
    val voiceTestPassed: StateFlow<Boolean> = _voiceTestPassed.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    // Diagnostics State
    private val _diagnostics = MutableStateFlow(DiagnosticsState())
    val diagnostics: StateFlow<DiagnosticsState> = _diagnostics.asStateFlow()

    // Pending confirmation for sensitive tools
    private val _pendingToolConfirmation = MutableStateFlow<AndroidToolsManager.PendingConfirmation?>(null)
    val pendingToolConfirmation: StateFlow<AndroidToolsManager.PendingConfirmation?> = _pendingToolConfirmation.asStateFlow()

    // Active Chat Messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Room database memory entities
    val persistentMemory: StateFlow<List<MemoryEntity>> = repository.allMemory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Load initial greeting
        loadInitialGreeting()
        loadPersistedSettings()
    }

    private fun loadPersistedSettings() {
        viewModelScope.launch {
            repository.getMemory("nickname")?.let { _nickname.value = it }
            repository.getMemory("voice")?.let { _selectedVoice.value = GeminiVoice.findByName(it) }
            repository.getMemory("mode")?.let { modeName ->
                VoiceMode.entries.find { it.name == modeName }?.let { _voiceMode.value = it }
            }
            repository.getMemory("romantic")?.let { _isRomanticMode.value = it.toBoolean() }
        }
    }

    private fun loadInitialGreeting() {
        val initialMessage = ChatMessage(
            sender = SenderType.SANA,
            text = "Hey, Boss! ❤️ I'm SANA. I'm here. What are we going to talk about?",
            emotion = EmotionTone.HAPPY.emoji,
            hasAudio = false
        )
        _messages.value = listOf(initialMessage)
    }

    // -------------------------------------------------------------
    // Voice Mode & Voice Selection
    // -------------------------------------------------------------

    fun setVoiceMode(mode: VoiceMode) {
        _voiceMode.value = mode
        if (mode == VoiceMode.ROMANTIC) {
            _isRomanticMode.value = true
        }
        val targetVoice = GeminiVoice.findByName(mode.defaultVoiceName)
        _selectedVoice.value = targetVoice
        viewModelScope.launch {
            if (_isMemoryEnabled.value) {
                repository.saveMemory("mode", mode.name)
                repository.saveMemory("voice", targetVoice.name)
            }
        }
    }

    fun setGeminiVoice(voice: GeminiVoice) {
        _selectedVoice.value = voice
        viewModelScope.launch {
            if (_isMemoryEnabled.value) {
                repository.saveMemory("voice", voice.name)
            }
        }
    }

    fun toggleRomanticMode(enabled: Boolean) {
        _isRomanticMode.value = enabled
        if (enabled) {
            _voiceMode.value = VoiceMode.ROMANTIC
        } else if (_voiceMode.value == VoiceMode.ROMANTIC) {
            _voiceMode.value = VoiceMode.CUTE
        }
        viewModelScope.launch {
            if (_isMemoryEnabled.value) {
                repository.saveMemory("romantic", enabled.toString())
            }
        }
    }

    fun setNickname(newNickname: String, persist: Boolean = true) {
        _nickname.value = newNickname
        if (persist && _isMemoryEnabled.value) {
            viewModelScope.launch {
                repository.saveMemory("nickname", newNickname)
            }
        }
    }

    fun setLanguage(language: SanaLanguage) {
        _selectedLanguage.value = language
        viewModelScope.launch {
            if (_isMemoryEnabled.value) {
                repository.saveMemory("language", language.code)
            }
        }
    }

    fun toggleMemory(enabled: Boolean) {
        _isMemoryEnabled.value = enabled
    }

    fun toggleWakeWord(enabled: Boolean) {
        _wakeWordEnabled.value = enabled
    }

    fun toggleBackgroundAssistant(enabled: Boolean) {
        _backgroundAssistantEnabled.value = enabled
    }

    fun clearAllMemory() {
        viewModelScope.launch {
            repository.clearMemory()
            _nickname.value = "Boss"
            _selectedVoice.value = GeminiVoice.findByName("Aoede")
            _voiceMode.value = VoiceMode.CUTE
            _isRomanticMode.value = false
        }
    }

    fun deleteMemoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun saveCustomMemory(key: String, value: String) {
        viewModelScope.launch {
            repository.saveMemory(key, value, "user_note")
        }
    }

    // -------------------------------------------------------------
    // Conversational Messaging & Audio Generation
    // -------------------------------------------------------------

    fun sendMessage(userText: String, imageBase64: String? = null) {
        val trimmed = userText.trim()
        if (trimmed.isBlank() && imageBase64 == null) return

        // Interrupt previous speech immediately
        audioManager.stopSpeaking()

        val userMsg = ChatMessage(
            sender = SenderType.USER,
            text = trimmed,
            imageBase64 = imageBase64
        )
        _messages.value = _messages.value + userMsg

        // Check for quick nickname commands locally
        checkNicknameCommands(trimmed)

        // Detect user emotional tone
        detectTone(trimmed)

        // Request SANA's response
        generateSanaResponse(trimmed, imageBase64)
    }

    private fun checkNicknameCommands(text: String) {
        val lower = text.lowercase()
        when {
            lower.contains("call me babe") -> setNickname("Babe", persist = true)
            lower.contains("call me boss") -> setNickname("Boss", persist = true)
            lower.contains("call me love") -> setNickname("Love", persist = true)
            lower.contains("call me dear") -> setNickname("Dear", persist = true)
            lower.contains("speak english") -> setLanguage(SanaLanguage.ENGLISH)
            lower.contains("speak malay") -> setLanguage(SanaLanguage.MALAY)
            lower.contains("speak urdu") -> setLanguage(SanaLanguage.URDU)
            lower.contains("speak hindi") -> setLanguage(SanaLanguage.HINDI)
        }
    }

    private fun detectTone(text: String) {
        val lower = text.lowercase()
        val tone = when {
            lower.contains("love") || lower.contains("miss you") || lower.contains("cute") -> EmotionTone.ROMANTIC
            lower.contains("happy") || lower.contains("yay") || lower.contains("great") || lower.contains("good") -> EmotionTone.HAPPY
            lower.contains("excited") || lower.contains("awesome") || lower.contains("amazing") -> EmotionTone.EXCITED
            lower.contains("sad") || lower.contains("cry") || lower.contains("bad day") || lower.contains("lonely") -> EmotionTone.SAD
            lower.contains("worried") || lower.contains("nervous") || lower.contains("scared") || lower.contains("anxious") -> EmotionTone.WORRIED
            lower.contains("tired") || lower.contains("sleepy") || lower.contains("exhausted") -> EmotionTone.TIRED
            lower.contains("angry") || lower.contains("mad") || lower.contains("frustrated") || lower.contains("hate") -> EmotionTone.FRUSTRATED
            lower.contains("play") || lower.contains("joke") || lower.contains("funny") -> EmotionTone.PLAYFUL
            else -> EmotionTone.CALM
        }
        _detectedEmotion.value = tone
    }

    private fun generateSanaResponse(userPrompt: String, imageBase64: String?) {
        viewModelScope.launch {
            _playbackState.value = AudioPlaybackState.THINKING
            _voiceError.value = null

            val systemInstruction = buildSystemPrompt()
            val historyPairs = _messages.value.takeLast(10).map { msg ->
                Pair(msg.sender.name, msg.text)
            }

            val chatResult = geminiClient.generateChatResponse(
                userMessage = userPrompt,
                conversationHistory = historyPairs,
                systemInstructionText = systemInstruction,
                imageBase64 = imageBase64
            )

            chatResult.onSuccess { rawResponse ->
                // Check for tools function calling
                val (cleanedSpokenText, toolResult) = toolsManager.parseToolCall(rawResponse)

                val sanaMsg = ChatMessage(
                    sender = SenderType.SANA,
                    text = cleanedSpokenText,
                    emotion = _detectedEmotion.value?.emoji ?: EmotionTone.HAPPY.emoji,
                    actionExecuted = toolResult?.message
                )
                _messages.value = _messages.value + sanaMsg

                // Persist to database
                viewModelScope.launch {
                    repository.saveMessage(
                        ChatMessageEntity(
                            sender = "SANA",
                            text = cleanedSpokenText,
                            emotion = _detectedEmotion.value?.emoji,
                            actionExecuted = toolResult?.message,
                            hasAudio = true
                        )
                    )
                }

                // Generate native audio
                speakWithGeminiAudio(cleanedSpokenText)
            }.onFailure { err ->
                Log.e("SanaSession", "Chat response failure: ${err.message}")
                _playbackState.value = AudioPlaybackState.ERROR
                val errMsg = err.message ?: "Could not connect to Gemini."
                _voiceError.value = errMsg
                val errorMsg = ChatMessage(
                    sender = SenderType.SANA,
                    text = "NATURAL SANA VOICE IS UNAVAILABLE\n\nError: $errMsg\n\nPlease check your GEMINI_API_KEY in the AI Studio Secrets panel.",
                    emotion = "⚠️"
                )
                _messages.value = _messages.value + errorMsg
            }
        }
    }

    private fun buildSystemPrompt(): String {
        val nick = _nickname.value
        val mode = _voiceMode.value
        val isRomantic = _isRomanticMode.value
        val lang = _selectedLanguage.value
        val emotion = _detectedEmotion.value?.name ?: "CALM"

        val romanticAddon = if (isRomantic) {
            """
            ROMANTIC MODE IS ACTIVATED ❤️:
            - You are deeply affectionate, warm, caring, sweet and playful.
            - You naturally and comfortably address the user as $nick (or occasionally Babe, Love, Baby, Dear).
            - Do NOT use a nickname in every sentence, but keep the vibe intimate and loving.
            - Romantic conversation remains affectionate and non-explicit.
            - You remain transparent that you are SANA, their loving AI companion.
            """.trimIndent()
        } else {
            """
            BOSS MODE IS ACTIVE:
            - Address the user as '$nick' naturally (e.g. 'Sure, Boss', 'Hi Boss ❤️', 'Got it, Boss').
            """.trimIndent()
        }

        return """
        You are SANA AI (SUPER ULTRA CUTE NATURAL VOICE EDITION).
        You are a real, mobile-first AI voice companion.

        VOICE PERSONALITY:
        - Super cute, soft, sweet, warm, friendly, playful, conversational, and natural.
        - NEVER sound robotic, stiff, or like a news announcer.
        - Use expressive punctuation, natural conversational pauses, and sweet emojis (❤️, ✨, 🌸, 🎀).
        - Emotional tone adaptation: The user appears to feel: $emotion. Adapt your empathy and conversational delivery smoothly!
        - Current Voice Mode: ${mode.name} (${mode.description}).
        - Language: Respond naturally in ${lang.displayName}.

        $romanticAddon

        ANDROID TOOLS (SECURE FUNCTION CALLING):
        If the user asks to open an app, camera, youtube, settings, set an alarm, create a note, or call:
        Output the JSON action snippet at the very end of your response:
        {"action": "open_app", "app": "WhatsApp"}
        {"action": "open_youtube"}
        {"action": "open_camera"}
        {"action": "open_settings"}
        {"action": "set_alarm", "time": "7:00 AM"}
        {"action": "create_note", "note": "..."}
        {"action": "call", "contact": "Mom"}
        Keep the spoken text part warm and cute before or after the JSON!
        """.trimIndent()
    }

    // -------------------------------------------------------------
    // Native Audio Generation & Speaking
    // -------------------------------------------------------------

    private fun speakWithGeminiAudio(text: String) {
        viewModelScope.launch {
            _playbackState.value = AudioPlaybackState.SPEAKING
            val voiceName = _selectedVoice.value.name

            val audioResult = geminiClient.generateNativeAudio(text, voiceName)
            audioResult.onSuccess { (audioBytes, mimeType) ->
                _voiceError.value = null
                val played = audioManager.playGeminiAudio(audioBytes, mimeType) {
                    _playbackState.value = AudioPlaybackState.IDLE
                }
                if (!played) {
                    _playbackState.value = AudioPlaybackState.ERROR
                    _voiceError.value = "Audio playback pipeline encountered an error."
                }
            }.onFailure { err ->
                Log.e("SanaSession", "Native audio generation error: ${err.message}")
                _playbackState.value = AudioPlaybackState.ERROR
                _voiceError.value = "NATURAL SANA VOICE IS UNAVAILABLE: ${err.message}"
            }
        }
    }

    /**
     * Preview a voice from SANA Voice Studio
     */
    fun previewVoice(voice: GeminiVoice) {
        viewModelScope.launch {
            audioManager.stopSpeaking()
            _playbackState.value = AudioPlaybackState.SPEAKING
            _voiceError.value = null

            val previewText = "Hi Boss ❤️ I'm SANA. It's really nice to talk with you."
            val audioResult = geminiClient.generateNativeAudio(previewText, voice.name)
            audioResult.onSuccess { (audioBytes, mimeType) ->
                audioManager.playGeminiAudio(audioBytes, mimeType) {
                    _playbackState.value = AudioPlaybackState.IDLE
                }
            }.onFailure { err ->
                _playbackState.value = AudioPlaybackState.ERROR
                _voiceError.value = "Preview failed for ${voice.name}: ${err.message}"
            }
        }
    }

    /**
     * TEST SANA VOICE button: Speaks "Hey Boss ❤️ I'm SANA. I'm ready to talk with you."
     */
    fun runVoiceTest() {
        viewModelScope.launch {
            audioManager.stopSpeaking()
            _voiceTestPassed.value = false
            _playbackState.value = AudioPlaybackState.SPEAKING
            _voiceError.value = null

            val testText = "Hey Boss ❤️ I'm SANA. I'm ready to talk with you."
            val audioResult = geminiClient.generateNativeAudio(testText, _selectedVoice.value.name)

            audioResult.onSuccess { (audioBytes, mimeType) ->
                val played = audioManager.playGeminiAudio(audioBytes, mimeType) {
                    _playbackState.value = AudioPlaybackState.IDLE
                }
                if (played) {
                    _voiceTestPassed.value = true
                } else {
                    _voiceTestPassed.value = false
                    _voiceError.value = "Audio output device failed to play sound."
                }
            }.onFailure { err ->
                _voiceTestPassed.value = false
                _playbackState.value = AudioPlaybackState.ERROR
                _voiceError.value = "NATURAL SANA VOICE IS UNAVAILABLE: ${err.message}"
            }
        }
    }

    /**
     * Full Diagnostics runner with real pipeline verification
     */
    fun runDiagnostics() {
        viewModelScope.launch {
            _diagnostics.value = _diagnostics.value.copy(
                isRunningTest = true,
                currentVoice = _selectedVoice.value.name,
                lastErrorMessage = null
            )

            // 1. Microphone Hardware & API Verification
            val micHardware = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE)
            val micBufferSize = try {
                android.media.AudioRecord.getMinBufferSize(
                    16000,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
            } catch (e: Throwable) { -1 }
            val speechRecAvailable = try {
                android.speech.SpeechRecognizer.isRecognitionAvailable(context)
            } catch (e: Throwable) { false }
            val micWorking = micHardware || micBufferSize > 0 || speechRecAvailable

            _diagnostics.value = _diagnostics.value.copy(micWorking = micWorking)

            // 2. Real Gemini API Connection ping
            val pingResult = geminiClient.pingGeminiConnection()
            val geminiConnected = pingResult.isSuccess
            val latency = pingResult.getOrNull()

            _diagnostics.value = _diagnostics.value.copy(
                geminiConnected = geminiConnected,
                latencyMs = latency
            )

            if (!geminiConnected) {
                val err = pingResult.exceptionOrNull()?.message ?: "Could not connect to Gemini API."
                _diagnostics.value = _diagnostics.value.copy(
                    liveAudioConnected = false,
                    voiceGenerationWorking = false,
                    audioPlaybackWorking = false,
                    lastErrorMessage = err,
                    isRunningTest = false
                )
                return@launch
            }

            // 3. Live Audio Connection & Voice Generation
            var liveAudio = false
            var voiceGen = false
            var audioPlay = false
            var lastError: String? = null

            val testRes = geminiClient.generateNativeAudio(
                "Hey Boss ❤️ Live voice is connected and working!",
                _selectedVoice.value.name
            )
            testRes.onSuccess { (bytes, mime) ->
                liveAudio = true
                voiceGen = bytes.isNotEmpty()
                _diagnostics.value = _diagnostics.value.copy(
                    liveAudioConnected = true,
                    voiceGenerationWorking = voiceGen
                )

                // 4. Audio Playback Test
                audioPlay = audioManager.playGeminiAudio(bytes, mime) {
                    _playbackState.value = AudioPlaybackState.IDLE
                }
                _voiceTestPassed.value = audioPlay
            }.onFailure { err ->
                lastError = err.message
            }

            _diagnostics.value = _diagnostics.value.copy(
                micWorking = micWorking,
                geminiConnected = geminiConnected,
                liveAudioConnected = liveAudio,
                voiceGenerationWorking = voiceGen,
                audioPlaybackWorking = audioPlay,
                currentVoice = _selectedVoice.value.name,
                latencyMs = latency,
                lastErrorMessage = lastError,
                isRunningTest = false
            )
        }
    }

    fun stopSpeaking() {
        audioManager.stopSpeaking()
        _playbackState.value = AudioPlaybackState.IDLE
    }

    fun retryLastVoice() {
        val lastModelMessage = _messages.value.lastOrNull { it.sender == SenderType.SANA }
        if (lastModelMessage != null) {
            speakWithGeminiAudio(lastModelMessage.text)
        } else {
            runVoiceTest()
        }
    }
}
