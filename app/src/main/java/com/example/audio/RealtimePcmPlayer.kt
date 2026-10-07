package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Realtime PCM AudioTrack Player
 * - Plays raw 24kHz, 16-bit Mono, Little-Endian PCM audio chunks returned by Gemini Live.
 * - Streams audio directly through the phone speaker with AudioTrack.
 * - Handles AudioFocus, barge-in instant flush, and turn completion.
 */
class RealtimePcmPlayer(
    private val context: Context,
    private val onPlaybackStateChanged: (isPlaying: Boolean) -> Unit,
    private val onAudioLevelChanged: (Float) -> Unit,
    private val onPlaybackCompleted: () -> Unit = {}
) {

    companion object {
        private const val TAG = "SanaLive"
        const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val chunkQueue = LinkedBlockingQueue<ByteArray>()
    private val mutex = Mutex()
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isPlaying = false

    @Volatile
    private var isTurnCompletePending = false

    @Synchronized
    private fun ensureAudioTrack(): Boolean {
        if (audioTrack != null && audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            return true
        }

        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "ERROR: Invalid minBufferSize for AudioTrack")
            return false
        }

        val bufferSize = maxOf(minBufferSize * 4, 24000)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .build()

        val audioFormatConfig = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormatConfig)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "ERROR: AudioTrack failed to initialize")
                audioTrack?.release()
                audioTrack = null
                return false
            }

            // Route to speaker where possible
            try {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
            } catch (e: Throwable) {
                Log.w(TAG, "Could not set speakerphone mode: ${e.message}")
            }

            audioTrack?.play()
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "ERROR creating AudioTrack: ${e.message}")
            return false
        }
    }

    fun start() {
        if (playJob == null || playJob?.isActive == false) {
            playJob = scope.launch {
                playbackLoop()
            }
        }
    }

    fun enqueueAudioChunk(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return
        start()
        chunkQueue.offer(pcmBytes)
    }

    fun onTurnCompleted() {
        isTurnCompletePending = true
    }

    private suspend fun playbackLoop() {
        while (scope.isActive) {
            val chunk = chunkQueue.poll(50, TimeUnit.MILLISECONDS)
            if (chunk != null) {
                if (!isPlaying) {
                    mutex.withLock {
                        requestAudioFocus()
                        ensureAudioTrack()
                        isPlaying = true
                        Log.d(TAG, "PLAYBACK_STARTED")
                        onPlaybackStateChanged(true)
                    }
                }

                // Compute real output level for visualizer
                computeAndEmitLevel(chunk)

                val track = audioTrack
                if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                    var offset = 0
                    while (offset < chunk.size && isPlaying) {
                        val written = track.write(chunk, offset, chunk.size - offset)
                        if (written > 0) {
                            offset += written
                        } else {
                            break
                        }
                    }
                }
            } else {
                // Queue is empty
                if (isPlaying && isTurnCompletePending && chunkQueue.isEmpty()) {
                    mutex.withLock {
                        isTurnCompletePending = false
                        isPlaying = false
                        onAudioLevelChanged(0f)
                        Log.d(TAG, "PLAYBACK_COMPLETED")
                        onPlaybackStateChanged(false)
                        abandonAudioFocus()
                        onPlaybackCompleted()
                    }
                }
            }
        }
    }

    private fun computeAndEmitLevel(chunk: ByteArray) {
        if (chunk.size < 2) return
        var sumSquares = 0.0
        val sampleCount = chunk.size / 2
        for (i in 0 until sampleCount) {
            val low = chunk[i * 2].toInt() and 0xFF
            val high = chunk[i * 2 + 1].toInt()
            val sample = ((high shl 8) or low).toShort()
            sumSquares += (sample * sample).toDouble()
        }
        val rms = (sqrt(sumSquares / sampleCount) / 32768.0).toFloat()
        val normalized = (rms * 4.0f).coerceIn(0.0f, 1.0f)
        onAudioLevelChanged(normalized)
    }

    /**
     * Instantly stops and flushes audio for Barge-in or Stop.
     */
    @Synchronized
    fun interrupt() {
        chunkQueue.clear()
        isTurnCompletePending = false
        val wasPlaying = isPlaying
        isPlaying = false

        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    pause()
                    flush()
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error pausing/flushing AudioTrack: ${e.message}")
        }

        onAudioLevelChanged(0f)
        if (wasPlaying) {
            Log.d(TAG, "PLAYBACK_INTERRUPTED_BARGE_IN")
            onPlaybackStateChanged(false)
            abandonAudioFocus()
        }
    }

    @Synchronized
    fun stop() {
        interrupt()
        playJob?.cancel()
        playJob = null

        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                    release()
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing AudioTrack: ${e.message}")
        }
        audioTrack = null

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Throwable) {}

        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                .build()
                        )
                        .build()
                }
                audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not request audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Throwable) {}
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying
}
