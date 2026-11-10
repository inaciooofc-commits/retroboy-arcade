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
    private val arm64 = setOf("arm64-v8a")
    private val arm = setOf("arm64-v8a", "armeabi-v7a")

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
            systems = "Game Boy Advance",
            description = "Alternativa GBA; recurso Wireless Adapter para trocas Pokémon ainda experimental.",
            license = "GPL-2.0",
            netplay = true
        ),
        CoreDefinition(
            id = "mgba",
            title = "mGBA",
            systems = "Game Boy • Game Boy Color • Game Boy Advance",
            description = "Core recomendado para GB, GBC e GBA em partidas locais/offline.",
            license = "MPL-2.0",
            netplay = false
        ),
        CoreDefinition(
            id = "fceumm",
            title = "FCEUmm",
            systems = "Nintendo NES • Famicom",
            description = "Emulação NES/Famicom, incluindo Famicom Disk System com BIOS própria.",
            license = "GPL-2.0",
            netplay = true,
            supportedAbis = arm64
        ),
        CoreDefinition(
            id = "snes9x",
            title = "Snes9x",
            systems = "Super Nintendo • Super Famicom",
            description = "Core principal de SNES, com suporte a saves e netplay upstream.",
            license = "Não comercial",
            netplay = true,
            supportedAbis = arm64
        ),
        CoreDefinition(
            id = "genesis_plus_gx",
            title = "Genesis Plus GX",
            systems = "Mega Drive • Master System • Game Gear • Sega CD",
            description = "Emula a família Sega 8/16-bit e SG-1000; não suporta 32X.",
            license = "Não comercial",
            netplay = true,
            supportedAbis = arm64
        ),
        CoreDefinition(
            id = "pcsx_rearmed",
            title = "PCSX ReARMed",
            systems = "Sony PlayStation • PS1",
            description = "Core otimizado para processadores ARM. BIOS própria melhora a compatibilidade.",
            license = "GPL-2.0",
            netplay = true,
            supportedAbis = arm
        ),
        CoreDefinition(
            id = "stella",
            title = "Stella",
            systems = "Atari 2600",
            description = "Emulador Atari 2600 com suporte a estados e controles clássicos.",
            license = "GPL-2.0",
            netplay = true,
            supportedAbis = arm64
        ),
        CoreDefinition(
            id = "handy",
            title = "Handy",
            systems = "Atari Lynx",
            description = "Emulador Atari Lynx; requer a BIOS lynxboot.img fornecida pelo usuário.",
            license = "zlib",
            netplay = true,
            supportedAbis = arm64
        ),
        CoreDefinition(
            id = "mednafen_pce_fast",
            title = "Beetle PCE FAST",
            systems = "PC Engine • TurboGrafx-16 • PC Engine CD",
            description = "Core rápido para PC Engine; CD exige cue/chd e BIOS compatível.",
            license = "GPL-2.0",
            netplay = true,
            supportedAbis = arm64
        )
    )

    fun find(id: String): CoreDefinition? = all.firstOrNull { it.id == id }

    fun downloadUrl(coreId: String, abi: String): String {
        return "https://buildbot.libretro.com/nightly/android/latest/" +
            abi + "/" + coreId + "_libretro_android.so.zip"
    }
}
