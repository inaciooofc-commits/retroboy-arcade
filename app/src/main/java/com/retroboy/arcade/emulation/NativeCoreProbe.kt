package com.retroboy.arcade.emulation

/**
 * Opens a downloaded Libretro core and verifies its public API exports.
 * This is a compatibility probe, not the game execution runtime.
 */
object NativeCoreProbe {
    init {
        System.loadLibrary("retroboy_core_probe")
    }

    external fun probeCore(libraryPath: String): String
}
