package com.retroboy.arcade.emulation

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.IOException

/**
 * Owns one Libretro game session. Call create/construct off the UI thread.
 * Button ids follow RETRO_DEVICE_ID_JOYPAD_* from libretro.h.
 */
class LibretroGameSession(
    corePath: String,
    contentPath: String,
    systemDirectory: String,
    saveDirectory: String,
    surface: Surface
) : Closeable {
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioTrack: AudioTrack? = null
    private var audioJob: Job? = null
    private var inputMask: Int = 0
    @Volatile private var closed = false

    init {
        val result = NativeLibretroRuntime.nativeCreate(
            corePath, contentPath, systemDirectory, saveDirectory, surface
        )
        if (!result.startsWith("OK:")) {
            NativeLibretroRuntime.nativeDestroy()
            throw IOException(result)
        }
        startAudio()
    }

    private fun startAudio() {
        val sampleRate = NativeLibretroRuntime.nativeGetSampleRate().coerceIn(8000, 96000)
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) return

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBytes * 2, sampleRate * 8 / 5))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return
        }

        track.play()
        audioTrack = track
        audioJob = audioScope.launch {
            val samples = ShortArray(4096)
            while (isActive && !closed) {
                val count = NativeLibretroRuntime.nativeReadAudio(samples)
                if (count > 0) {
                    val written = track.write(samples, 0, count, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) delay(5)
                } else {
                    delay(3)
                }
            }
        }
    }

    @Synchronized
    fun setButton(buttonId: Int, pressed: Boolean) {
        if (closed || buttonId !in 0..15) return
        val bit = 1 shl buttonId
        inputMask = if (pressed) inputMask or bit else inputMask and bit.inv()
        NativeLibretroRuntime.nativeSetInputMask(inputMask)
    }

    fun pause(paused: Boolean) {
        if (!closed) NativeLibretroRuntime.nativePause(paused)
    }

    fun reset() {
        if (!closed) NativeLibretroRuntime.nativeReset()
    }

    fun saveState(): ByteArray? {
        if (closed) return null
        return NativeLibretroRuntime.nativeSaveState()
    }

    fun loadState(state: ByteArray): Boolean {
        if (closed) return false
        return NativeLibretroRuntime.nativeLoadState(state)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        inputMask = 0
        NativeLibretroRuntime.nativeSetInputMask(0)
        audioJob?.cancel()
        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        runCatching { audioTrack?.stop() }
        audioTrack?.release()
        audioTrack = null
        NativeLibretroRuntime.nativeDestroy()
        audioScope.cancel()
    }
}
