package com.retroboy.arcade.model

enum class Platform(val label: String) {
    NEOGEO("Neo Geo"),
    ARCADE("Arcade"),
    GBA("Game Boy Advance"),
    NES("Nintendo NES"),
    SNES("Super Nintendo"),
    SEGA("Sega 8/16-bit"),
    PS1("PlayStation"),
    ATARI2600("Atari 2600"),
    ATARI_LYNX("Atari Lynx"),
    PC_ENGINE("PC Engine")
}

data class GameEntry(
    val id: String,
    val title: String,
    val platform: Platform,
    val subtitle: String,
    val accent: Long,
    val localPath: String? = null,
    val sourceUri: String? = null,
    val isDemo: Boolean = false
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val target: Int,
    val points: Int,
    val category: String,
    val progress: Int = 0
) {
    val unlocked: Boolean get() = progress >= target
}

data class CatalogAsset(
    val id: String,
    val title: String,
    val description: String,
    val kind: String,
    val version: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val distribution: String
)
