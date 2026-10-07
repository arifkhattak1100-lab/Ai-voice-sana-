package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Realtime PCM Microphone Recorder
 * - Captures continuous 16kHz, 16-bit Mono, Little-Endian PCM audio from the real device microphone.
 * - Uses VOICE_COMMUNICATION to enable hardware echo cancellation (AEC) and noise suppression.
 * - Computes real RMS audio level for UI visualizer and voice activity detection.
 * - Streams actual microphone audio bytes to Gemini Live.
 */
class RealtimePcmRecorder(
    private val onPcmChunk: (ByteArray) -> Unit,
    private val onAudioLevelChanged: (Float) -> Unit,
    private val onVoiceActivity: (isSpeaking: Boolean, rms: Float) -> Unit = { _, _ -> }
) {

    companion object {
        private const val TAG = "SanaLive"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SAMPLES = 1024 // 1024 samples = 2048 bytes (128ms of audio at 16kHz)
        private const val SPEECH_RMS_THRESHOLD = 0.045f
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isRecording = false

    private var totalBytesCaptured: Long = 0
    private var chunkCounter: Long = 0

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        if (isRecording) {
            Log.d(TAG, "MIC_ALREADY_RECORDING")
            return true
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "ERROR: Invalid buffer size for AudioRecord")
            return false
        }

        val bufferSize = maxOf(minBufferSize * 2, CHUNK_SAMPLES * 2 * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "ERROR: AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                            enabled = true
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Could not enable AcousticEchoCanceler: ${e.message}")
                    }
                }
                if (NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                            enabled = true
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Could not enable NoiseSuppressor: ${e.message}")
                    }
                }
            }

            audioRecord?.startRecording()
            isRecording = true
            totalBytesCaptured = 0
            chunkCounter = 0

            Log.d(TAG, "MIC_STARTED: 16000Hz PCM 16-bit Mono (EchoCanceler=${echoCanceler != null}, NoiseSuppressor=${noiseSuppressor != null})")

            recordJob = scope.launch {
                readAudioLoop()
            }
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "ERROR starting microphone: ${e.message}")
            stop()
            return false
        }
    }

    private fun readAudioLoop() {
        val audioBuffer = ShortArray(CHUNK_SAMPLES)
        val byteBuffer = ByteArray(CHUNK_SAMPLES * 2)

        while (isRecording && scope.isActive) {
            val record = audioRecord ?: break
            val readCount = record.read(audioBuffer, 0, CHUNK_SAMPLES)

            if (readCount > 0) {
                var sumSquares = 0.0
                for (i in 0 until readCount) {
                    val sample = audioBuffer[i]
                    sumSquares += (sample * sample).toDouble()

                    // Convert short to little-endian bytes
                    byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                    byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                }

                val rms = (sqrt(sumSquares / readCount) / 32768.0).toFloat()
                val normalizedRms = (rms * 4.5f).coerceIn(0.0f, 1.0f)
                onAudioLevelChanged(normalizedRms)

                val bytesRead = readCount * 2
                totalBytesCaptured += bytesRead
                chunkCounter++

                if (chunkCounter % 50 == 0L) {
                    Log.d(TAG, "AUDIO_BYTES_CAPTURED: $totalBytesCaptured bytes total")
                }

                val isUserSpeaking = rms > SPEECH_RMS_THRESHOLD
                onVoiceActivity(isUserSpeaking, normalizedRms)

                val chunkToSend = if (bytesRead == byteBuffer.size) {
                    byteBuffer.copyOf()
                } else {
                    byteBuffer.copyOf(bytesRead)
                }

                onPcmChunk(chunkToSend)
            } else if (readCount < 0) {
                Log.w(TAG, "AudioRecord read error code: $readCount")
                break
            }
        }
    }

    @Synchronized
    fun stop() {
        isRecording = false
        recordJob?.cancel()
        recordJob = null

        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null

        try {
            echoCanceler?.release()
        } catch (_: Throwable) {}
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Throwable) {}
        noiseSuppressor = null

        onAudioLevelChanged(0f)
        onVoiceActivity(false, 0f)
        Log.d(TAG, "MIC_STOPPED: total captured = $totalBytesCaptured bytes")
    }

    fun isCapturing(): Boolean = isRecording
}
