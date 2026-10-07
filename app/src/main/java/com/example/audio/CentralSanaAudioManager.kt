package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class CentralSanaAudioManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _audioVisualizerLevel = MutableStateFlow(0f)
    val audioVisualizerLevel: StateFlow<Float> = _audioVisualizerLevel.asStateFlow()

    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _micError = MutableStateFlow<String?>(null)
    val micError: StateFlow<String?> = _micError.asStateFlow()

    private var audioFocusRequest: AudioFocusRequest? = null
    private var visualizerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private var onAudioFinishedCallback: (() -> Unit)? = null

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    // Real PCM Audio Streaming components for Gemini Live
    private var liveTurnCompletionCallback: (() -> Unit)? = null

    private val pcmPlayer = RealtimePcmPlayer(
        context = context,
        onPlaybackStateChanged = { playing ->
            _isSpeaking.value = playing
            if (!playing) {
                _audioVisualizerLevel.value = 0f
            }
        },
        onAudioLevelChanged = { level ->
            if (_isSpeaking.value) {
                _audioVisualizerLevel.value = level
            }
        },
        onPlaybackCompleted = {
            liveTurnCompletionCallback?.invoke()
        }
    )

    private var pcmRecorder: RealtimePcmRecorder? = null

    init {
        try {
            textToSpeech = TextToSpeech(context) { status ->
                isTtsReady = (status == TextToSpeech.SUCCESS)
                if (isTtsReady) {
                    textToSpeech?.language = Locale.US
                }
            }
        } catch (e: Exception) {
            Log.w("CentralSanaAudioManager", "TTS init exception: ${e.message}")
        }
    }

    // -------------------------------------------------------------
    // Realtime Two-Way Gemini Live Audio Pipeline
    // -------------------------------------------------------------

    /**
     * Starts continuous real microphone streaming for Gemini Live.
     * Captures raw 16kHz PCM audio bytes and transmits them continuously.
     * Detects user speech for client-side barge-in.
     */
    fun startLiveAudioCapture(
        onPcmChunk: (ByteArray) -> Unit,
        onBargeIn: () -> Unit,
        onPlaybackFinished: () -> Unit = {}
    ): Boolean {
        stopSpeaking()
        stopListening()

        liveTurnCompletionCallback = onPlaybackFinished

        pcmRecorder?.stop()
        pcmRecorder = RealtimePcmRecorder(
            onPcmChunk = onPcmChunk,
            onAudioLevelChanged = { level ->
                if (!_isSpeaking.value && _isListening.value) {
                    _audioVisualizerLevel.value = level
                }
            },
            onVoiceActivity = { isUserSpeaking, _ ->
                if (isUserSpeaking && _isSpeaking.value) {
                    Log.d("SanaLive", "Barge-in triggered: user started speaking while SANA was speaking")
                    interruptLiveAudio()
                    onBargeIn()
                }
            }
        )

        val started = pcmRecorder?.start() == true
        _isListening.value = started
        if (!started) {
            _micError.value = "Microphone failed to start"
        } else {
            _micError.value = null
        }
        return started
    }

    /**
     * Enqueues 24kHz raw PCM chunk from Gemini Live to AudioTrack for speaker output.
     */
    fun playLiveAudioChunk(pcmBytes: ByteArray) {
        pcmPlayer.enqueueAudioChunk(pcmBytes)
    }

    /**
     * Signals that Gemini Live finished transmitting audio for the current turn.
     */
    fun notifyLiveTurnComplete() {
        pcmPlayer.onTurnCompleted()
    }

    /**
     * Instantly stops speaker playback when user barges in or cancels.
     */
    fun interruptLiveAudio() {
        pcmPlayer.interrupt()
        _isSpeaking.value = false
        _audioVisualizerLevel.value = 0f
    }

    /**
     * Stops the live conversation completely.
     */
    fun stopLiveConversation() {
        pcmRecorder?.stop()
        pcmRecorder = null
        pcmPlayer.stop()
        _isListening.value = false
        _isSpeaking.value = false
        _audioVisualizerLevel.value = 0f
    }

    fun isLiveCaptureActive(): Boolean = pcmRecorder?.isCapturing() == true

    // -------------------------------------------------------------
    // Fallback & Diagnostics TTS / MediaPlayer
    // -------------------------------------------------------------

    fun speakWithAndroidTTS(
        text: String,
        pitch: Float = 1.0f,
        speechRate: Float = 1.0f,
        onFinished: (() -> Unit)? = null
    ) {
        stopSpeaking()
        requestAudioFocus()
        _isSpeaking.value = true
        startVisualizerSimulation()

        textToSpeech?.setPitch(pitch)
        textToSpeech?.setSpeechRate(speechRate)

        val utteranceId = "sana_tts_${System.currentTimeMillis()}"
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    _isSpeaking.value = false
                    stopVisualizer()
                    abandonAudioFocus()
                    onFinished?.invoke()
                }
            }

            override fun onError(utteranceId: String?) {
                scope.launch {
                    _isSpeaking.value = false
                    stopVisualizer()
                    abandonAudioFocus()
                    onFinished?.invoke()
                }
            }
        })

        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun playGeminiAudio(
        audioBytes: ByteArray,
        mimeType: String,
        onFinished: (() -> Unit)? = null
    ): Boolean {
        stopSpeaking()

        onAudioFinishedCallback = onFinished

        return try {
            requestAudioFocus()

            val soundData = if (mimeType.contains("pcm", ignoreCase = true) || mimeType.contains("l16", ignoreCase = true)) {
                pcmToWav(audioBytes, sampleRate = 24000, channels = 1, bitsPerSample = 16)
            } else {
                audioBytes
            }

            val tempFile = File.createTempFile("sana_speech_", ".wav", context.cacheDir)
            tempFile.deleteOnExit()
            FileOutputStream(tempFile).use { it.write(soundData) }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isSpeaking.value = false
                    stopVisualizer()
                    abandonAudioFocus()
                    tempFile.delete()
                    onAudioFinishedCallback?.invoke()
                    onAudioFinishedCallback = null
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("SanaAudioManager", "MediaPlayer error: what=$what, extra=$extra")
                    _isSpeaking.value = false
                    stopVisualizer()
                    abandonAudioFocus()
                    tempFile.delete()
                    true
                }
                start()
            }

            _isSpeaking.value = true
            startVisualizerSimulation()
            true
        } catch (e: Exception) {
            Log.e("SanaAudioManager", "Failed to play Gemini audio: ${e.message}", e)
            _isSpeaking.value = false
            stopVisualizer()
            abandonAudioFocus()
            false
        }
    }

    fun stopSpeaking() {
        interruptLiveAudio()
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.stop()
                }
                mediaPlayer?.release()
                mediaPlayer = null
            }
        } catch (e: Exception) {
            Log.w("SanaAudioManager", "Error stopping MediaPlayer: ${e.message}")
        }
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        } finally {
            _isSpeaking.value = false
            stopVisualizer()
            abandonAudioFocus()
        }
    }

    // -------------------------------------------------------------
    // Microphone & Speech Recognition (Fallback / Single-turn)
    // -------------------------------------------------------------

    fun startListening(languageCode: String = "en-US", onSpeechResult: (String) -> Unit) {
        stopSpeaking()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _micError.value = "Speech recognition is not available on this device."
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _micError.value = null
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _audioVisualizerLevel.value = norm
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _audioVisualizerLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _audioVisualizerLevel.value = 0f
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that, Boss."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microphone busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                            else -> "Microphone error ($error)"
                        }
                        _micError.value = msg
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _audioVisualizerLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognized = matches[0]
                            _recognizedText.value = recognized
                            onSpeechResult(recognized)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _recognizedText.value = matches[0]
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            _micError.value = e.message ?: "Failed to start microphone"
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w("SanaAudioManager", "Error destroying speech recognizer: ${e.message}")
        } finally {
            _isListening.value = false
            _audioVisualizerLevel.value = 0f
        }
    }

    // -------------------------------------------------------------
    // Audio Focus
    // -------------------------------------------------------------

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    ) {
                        stopSpeaking()
                    }
                }
                .build()

            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    ) {
                        stopSpeaking()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun startVisualizerSimulation() {
        visualizerJob?.cancel()
        visualizerJob = scope.launch {
            while (_isSpeaking.value) {
                val base = (Math.sin(System.currentTimeMillis() / 150.0) * 0.35 + 0.55).toFloat()
                val variation = (Math.random() * 0.25).toFloat()
                _audioVisualizerLevel.value = (base + variation).coerceIn(0.2f, 1f)
                delay(60)
            }
            _audioVisualizerLevel.value = 0f
        }
    }

    private fun stopVisualizer() {
        visualizerJob?.cancel()
        visualizerJob = null
        _audioVisualizerLevel.value = 0f
    }

    private fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = 24000,
        channels: Short = 1,
        bitsPerSample: Short = 16
    ): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = (channels.toInt() and 0xff).toByte()
        header[23] = ((channels.toInt() shr 8) and 0xff).toByte()
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = ((channels * bitsPerSample / 8) and 0xff).toByte()
        header[33] = 0
        header[34] = (bitsPerSample.toInt() and 0xff).toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        return header + pcmData
    }
}
