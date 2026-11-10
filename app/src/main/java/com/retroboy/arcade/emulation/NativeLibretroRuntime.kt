package com.retroboy.arcade.emulation

import android.view.Surface

internal object NativeLibretroRuntime {
    init {
        System.loadLibrary("retroboy_core_probe")
    }

    external fun nativeCreate(
        corePath: String,
        contentPath: String,
        systemDirectory: String,
        saveDirectory: String,
        surface: Surface
    ): String

    external fun nativeSetInputMask(mask: Int)
    external fun nativeSetRemoteInputMask(mask: Int)
    external fun nativeSetNetworkPlayerPort(port: Int)
    external fun nativeGetFps(): Double
    external fun nativeGetFrameCount(): Long
    external fun nativePause(paused: Boolean)
    external fun nativeGetSampleRate(): Int
    external fun nativeReadAudio(destination: ShortArray): Int
    external fun nativeSaveState(): ByteArray?
    external fun nativeLoadState(state: ByteArray): Boolean
    external fun nativeGetSaveRam(): ByteArray?
    external fun nativeSetSaveRam(saveData: ByteArray): Boolean
    external fun nativeReset()
    external fun nativeDestroy()
}
