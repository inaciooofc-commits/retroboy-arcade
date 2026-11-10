package com.retroboy.arcade.emulation

data class CoreDefinition(
    val id: String,
    val title: String,
    val systems: String,
    val description: String,
    val license: String,
    val netplay: Boolean,
    val supportedAbis: Set<String> = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
)

object CoreCatalog {
    val all = listOf(
        CoreDefinition(
            id = "fbneo",
            title = "FinalBurn Neo",
            systems = "Neo Geo • Arcade",
            description = "Núcleo principal para fliperamas e Neo Geo; inclui suporte a netplay.",
            license = "Não comercial",
            netplay = true
        ),
        CoreDefinition(
            id = "gpsp",
            title = "gpSP",
            systems = "Game Boy • Game Boy Color • Game Boy Advance",
            description = "Core a testar para trocas Pokémon via GBA Wireless Adapter; não é link cable genérico.",
            license = "GPL-2.0",
            netplay = true
        ),
        CoreDefinition(
            id = "mgba",
            title = "mGBA",
            systems = "Game Boy Advance",
            description = "Alternativa de precisão e compatibilidade para partidas locais/offline.",
            license = "MPL-2.0",
            netplay = false
        )
    )

    fun find(id: String): CoreDefinition? = all.firstOrNull { it.id == id }

    fun downloadUrl(coreId: String, abi: String): String {
        return "https://buildbot.libretro.com/nightly/android/latest/" +
            abi + "/" + coreId + "_libretro_android.so.zip"
    }
}
