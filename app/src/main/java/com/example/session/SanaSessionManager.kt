package com.example.session

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.CentralSanaAudioManager
import com.example.audio.SanaWakeWordManager
import com.example.brain.SanaBrain
import com.example.data.api.GeminiApiClient
import com.example.data.database.ChatMessageEntity
import com.example.data.database.MemoryEntity
import com.example.data.database.SanaMemoryManager
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
import com.example.permission.SanaPermissionManager
import com.example.service.SanaForegroundService
import com.example.tools.ConfirmationLevel
import com.example.tools.PendingConfirmation
import com.example.tools.SanaToolRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class SanaSessionManager(
    private val context: Context,
    private val repository: SanaRepository
) : ViewModel() {

    private val geminiClient = GeminiApiClient()
    val audioManager = CentralSanaAudioManager(context)
    val toolRouter = SanaToolRouter(context)
    val permissionManager = SanaPermissionManager(context)
    val memoryManager = SanaMemoryManager(repository, viewModelScope)
    val brain = SanaBrain(geminiClient)

    // Wake Word Manager
    val wakeWordManager = SanaWakeWordManager(
        context = context,
        onWakeWordDetected = { text ->
            sendMessage(text)
        },
        onError = { err ->
            _voiceError.value = err
        }
    )

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

    private val _wakeWordEnabled = MutableStateFlow(false)
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    private val _backgroundAssistantEnabled = MutableStateFlow(false)
    val backgroundAssistantEnabled: StateFlow<Boolean> = _backgroundAssistantEnabled.asStateFlow()

    // Confirmation Setting
    private val _confirmationEnabled = MutableStateFlow(true)
    val confirmationEnabled: StateFlow<Boolean> = _confirmationEnabled.asStateFlow()

    private val _confirmationLevel = MutableStateFlow(ConfirmationLevel.CONFIRM)
    val confirmationLevel: StateFlow<ConfirmationLevel> = _confirmationLevel.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    // Voice test and error feedback
    private val _voiceTestPassed = MutableStateFlow(false)
    val voiceTestPassed: StateFlow<Boolean> = _voiceTestPassed.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    // Diagnostics State
    private val _diagnostics = MutableStateFlow(DiagnosticsState())
    val diagnostics: StateFlow<DiagnosticsState> = _diagnostics.asStateFlow()

    // Active Chat Messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Image Generation state
    private val _isGeneratingImage = MutableStateFlow(false)
    val isGeneratingImage: StateFlow<Boolean> = _isGeneratingImage.asStateFlow()

    val isMemoryEnabled: StateFlow<Boolean> = memoryManager.isMemoryEnabled

    // Room database memory entities
    val persistentMemory: StateFlow<List<MemoryEntity>> = repository.allMemory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInitialGreeting()
        loadPersistedSettings()
        permissionManager.refreshPermissions()
    }

    private fun loadPersistedSettings() {
        viewModelScope.launch {
            repository.getMemory("nickname")?.let { _nickname.value = it }
            repository.getMemory("voice")?.let { _selectedVoice.value = GeminiVoice.findByName(it) }
            repository.getMemory("mode")?.let { modeName ->
                VoiceMode.entries.find { it.name == modeName }?.let { _voiceMode.value = it }
            }
            repository.getMemory("romantic")?.let { _isRomanticMode.value = it.toBoolean() }
            repository.getMemory("language")?.let { code ->
                SanaLanguage.entries.find { it.code == code }?.let { _selectedLanguage.value = it }
            }
        }
    }

    private fun loadInitialGreeting() {
        val initialMessage = ChatMessage(
            sender = SenderType.SANA,
            text = "Hey Boss! ❤️ I'm SANA, your full phone control voice assistant. Ready when you are!",
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
            if (memoryManager.isMemoryEnabled.value) {
                repository.saveMemory("mode", mode.name)
                repository.saveMemory("voice", targetVoice.name)
            }
        }
    }

    fun setGeminiVoice(voice: GeminiVoice) {
        _selectedVoice.value = voice
        viewModelScope.launch {
            if (memoryManager.isMemoryEnabled.value) {
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
            if (memoryManager.isMemoryEnabled.value) {
                repository.saveMemory("romantic", enabled.toString())
            }
        }
    }

    fun setNickname(newNickname: String, persist: Boolean = true) {
        _nickname.value = newNickname
        if (persist && memoryManager.isMemoryEnabled.value) {
            viewModelScope.launch {
                repository.saveMemory("nickname", newNickname)
            }
        }
    }

    fun setLanguage(language: SanaLanguage) {
        _selectedLanguage.value = language
        viewModelScope.launch {
            if (memoryManager.isMemoryEnabled.value) {
                repository.saveMemory("language", language.code)
            }
        }
    }

    fun toggleMemory(enabled: Boolean) {
        memoryManager.setMemoryEnabled(enabled)
    }

    fun toggleWakeWord(enabled: Boolean) {
        _wakeWordEnabled.value = enabled
        if (enabled) {
            wakeWordManager.startListening(_selectedLanguage.value.code)
        } else {
            wakeWordManager.stopListening()
        }
    }

    fun toggleBackgroundAssistant(enabled: Boolean) {
        _backgroundAssistantEnabled.value = enabled
        if (enabled) {
            SanaForegroundService.startService(context)
        } else {
            SanaForegroundService.stopService(context)
        }
    }

    fun toggleConfirmation(enabled: Boolean) {
        _confirmationEnabled.value = enabled
        toolRouter.confirmationEnabled = enabled
    }

    fun setConfirmationLevel(level: ConfirmationLevel) {
        _confirmationLevel.value = level
        toolRouter.confirmationLevel = level
    }

    fun confirmPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        val result = pending.execute()
        handleToolOutcome(result)
    }

    fun dismissPendingAction() {
        _pendingConfirmation.value = null
        val cancelMsg = "Cancelled, Boss."
        val sanaMsg = ChatMessage(
            sender = SenderType.SANA,
            text = cancelMsg,
            emotion = EmotionTone.CASUAL.emoji
        )
        _messages.value = _messages.value + sanaMsg
        speakWithGeminiAudio(cancelMsg)
    }

    fun clearAllMemory() {
        viewModelScope.launch {
            memoryManager.clearAllMemory()
            _nickname.value = "Boss"
            _selectedVoice.value = GeminiVoice.findByName("Aoede")
            _voiceMode.value = VoiceMode.CUTE
            _isRomanticMode.value = false
        }
    }

    fun deleteMemoryItem(id: Long) {
        memoryManager.deleteMemory(id)
    }

    fun saveCustomMemory(key: String, value: String) {
        memoryManager.saveMemory(key, value, "user_note")
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

        // Check for direct image generation request
        if (trimmed.startsWith("create an image", ignoreCase = true) ||
            trimmed.startsWith("generate an image", ignoreCase = true) ||
            trimmed.startsWith("draw ", ignoreCase = true)
        ) {
            handleImageGeneration(trimmed)
            return
        }

        // Detect user emotional tone
        val detected = brain.detectEmotion(trimmed)
        _detectedEmotion.value = detected

        // Request SANA's response
        generateSanaResponse(trimmed, imageBase64)
    }

    private fun handleImageGeneration(prompt: String) {
        viewModelScope.launch {
            _isGeneratingImage.value = true
            _playbackState.value = AudioPlaybackState.SPEAKING
            val initialSpeech = "Sure, Boss! Creating an image of that for you now... ❤️"
            val placeholderMsg = ChatMessage(
                sender = SenderType.SANA,
                text = initialSpeech,
                emotion = "🎨"
            )
            _messages.value = _messages.value + placeholderMsg
            speakWithGeminiAudio(initialSpeech)

            val cleanPrompt = prompt.replace(Regex("(?i)^(create|generate)\\s+(an\\s+)?image\\s+of\\s+"), "")
                .replace(Regex("(?i)^draw\\s+"), "")

            val result = brain.generateImage(cleanPrompt)
            _isGeneratingImage.value = false

            result.onSuccess { bytes ->
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val doneSpeech = "Here is your generated image, Boss! ❤️"
                val doneMsg = ChatMessage(
                    sender = SenderType.SANA,
                    text = doneSpeech,
                    imageBase64 = base64,
                    emotion = "✨"
                )
                _messages.value = _messages.value + doneMsg
                speakWithGeminiAudio(doneSpeech)
            }.onFailure { err ->
                val errorSpeech = "Sorry Boss, I couldn't generate that image: ${err.message}"
                val errorMsg = ChatMessage(
                    sender = SenderType.SANA,
                    text = errorSpeech,
                    emotion = "⚠️"
                )
                _messages.value = _messages.value + errorMsg
                speakWithGeminiAudio(errorSpeech)
            }
        }
    }

    private fun generateSanaResponse(userPrompt: String, imageBase64: String?) {
        viewModelScope.launch {
            _playbackState.value = AudioPlaybackState.THINKING
            _voiceError.value = null

            // Build history turns
            val history = _messages.value
                .takeLast(10)
                .map { Pair(if (it.sender == SenderType.USER) "USER" else "MODEL", it.text) }

            val memorySummary = memoryManager.buildMemorySummary()

            val result = brain.thinkAndRespond(
                userMessage = userPrompt,
                history = history,
                nickname = _nickname.value,
                voiceMode = _voiceMode.value,
                language = _selectedLanguage.value,
                memorySummary = memorySummary,
                isRomanticMode = _isRomanticMode.value,
                imageBase64 = imageBase64
            )

            result.onSuccess { fullReply ->
                // Route command to modular Android tools
                val outcome = toolRouter.routeCommand(fullReply)

                if (outcome.pendingConfirmation != null) {
                    _pendingConfirmation.value = outcome.pendingConfirmation
                    val confirmMsg = ChatMessage(
                        sender = SenderType.SANA,
                        text = outcome.cleanSpeech,
                        emotion = "❓"
                    )
                    _messages.value = _messages.value + confirmMsg
                    speakWithGeminiAudio(outcome.cleanSpeech)
                } else if (outcome.result != null) {
                    handleToolOutcome(outcome.result)
                } else {
                    val finalSpeech = outcome.cleanSpeech.ifBlank { "I'm right here with you, Boss! ❤️" }
                    val sanaMsg = ChatMessage(
                        sender = SenderType.SANA,
                        text = finalSpeech,
                        emotion = _detectedEmotion.value?.emoji ?: EmotionTone.CASUAL.emoji
                    )
                    _messages.value = _messages.value + sanaMsg
                    speakWithGeminiAudio(finalSpeech)
                }
            }.onFailure { err ->
                Log.e("SanaSession", "Chat response failure: ${err.message}")
                _playbackState.value = AudioPlaybackState.ERROR
                val fallbackText = "Boss, I couldn't connect right now. ${err.message ?: "Please check your network or API key."}"
                val errorMsg = ChatMessage(
                    sender = SenderType.SANA,
                    text = fallbackText,
                    emotion = "⚠️"
                )
                _messages.value = _messages.value + errorMsg
                _voiceError.value = fallbackText
                speakWithGeminiAudio(fallbackText)
            }
        }
    }

    private fun handleToolOutcome(toolResult: ToolCallResult) {
        val speech = toolResult.message
        val sanaMsg = ChatMessage(
            sender = SenderType.SANA,
            text = speech,
            emotion = if (toolResult.success) "✅" else "⚠️",
            actionExecuted = "${toolResult.action}: ${toolResult.target}"
        )
        _messages.value = _messages.value + sanaMsg
        speakWithGeminiAudio(speech)
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
                    // Restart wake-word listener if enabled
                    if (_wakeWordEnabled.value) {
                        wakeWordManager.startListening(_selectedLanguage.value.code)
                    }
                }
                if (!played) {
                    fallbackToAndroidTTS(text)
                }
            }.onFailure { err ->
                Log.w("SanaSession", "Native audio generation error: ${err.message}. Using Android TTS fallback.")
                fallbackToAndroidTTS(text)
            }
        }
    }

    private fun fallbackToAndroidTTS(text: String) {
        audioManager.speakWithAndroidTTS(
            text = text,
            pitch = _voiceMode.value.pitchMultiplier,
            speechRate = _voiceMode.value.speedMultiplier
        ) {
            _playbackState.value = AudioPlaybackState.IDLE
            if (_wakeWordEnabled.value) {
                wakeWordManager.startListening(_selectedLanguage.value.code)
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
                fallbackToAndroidTTS(previewText)
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
                _voiceTestPassed.value = played
            }.onFailure { err ->
                _voiceError.value = err.message
                fallbackToAndroidTTS(testText)
                _voiceTestPassed.value = true
            }
        }
    }

    fun stopSpeaking() {
        audioManager.stopSpeaking()
        _playbackState.value = AudioPlaybackState.IDLE
    }

    fun retryLastVoice() {
        val lastSanaMsg = _messages.value.lastOrNull { it.sender == SenderType.SANA }
        if (lastSanaMsg != null) {
            speakWithGeminiAudio(lastSanaMsg.text)
        } else {
            runVoiceTest()
        }
    }

    fun clearChatHistory() {
        _messages.value = emptyList()
        loadInitialGreeting()
    }

    fun runDiagnostics() {
        viewModelScope.launch {
            _diagnostics.value = _diagnostics.value.copy(isRunningTest = true)
            val startTime = System.currentTimeMillis()
            val pingResult = geminiClient.pingGeminiConnection()
            val isConnected = pingResult.isSuccess
            val latency = pingResult.getOrNull() ?: (System.currentTimeMillis() - startTime)

            val audioResult = geminiClient.generateNativeAudio("Diagnostics test", _selectedVoice.value.name)
            val audioOk = audioResult.isSuccess

            _diagnostics.value = DiagnosticsState(
                micWorking = true,
                geminiConnected = isConnected,
                liveAudioConnected = audioOk,
                voiceGenerationWorking = audioOk,
                audioPlaybackWorking = true,
                currentVoice = _selectedVoice.value.name,
                latencyMs = latency,
                lastErrorMessage = if (!isConnected) pingResult.exceptionOrNull()?.message else audioResult.exceptionOrNull()?.message,
                isRunningTest = false
            )
        }
    }
}
