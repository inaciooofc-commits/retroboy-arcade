# Arquitetura de emulação — Retroboy Arcade

## Matriz de sistemas e núcleos

| Sistema | Core | ABI Android | Situação |
|---|---|---|---|
| Neo Geo / Arcade | FinalBurn Neo (FBNeo) | ARM64, ARMv7, x86_64 | Runtime single-player integrado; validar romsets no aparelho |
| GB / GBC / GBA | mGBA | ARM64, ARMv7, x86_64 | Runtime single-player integrado; validar jogos e SRAM |
| GBA / Wireless Adapter experimental | gpSP | ARM64, ARMv7, x86_64 | Trocas Pokémon ainda precisam de teste em dois aparelhos |
| NES / Famicom | FCEUmm | ARM64 | Core adicionado ao catálogo; validar jogos e BIOS FDS |
| SNES / Super Famicom | Snes9x | ARM64 | Core adicionado ao catálogo; licença não comercial |
| Mega Drive / Master System / Game Gear / Sega CD | Genesis Plus GX | ARM64 | Core adicionado ao catálogo; não suporta 32X |
| PlayStation 1 | PCSX ReARMed | ARM64, ARMv7 | Core otimizado para ARM; BIOS própria recomendada |
| Atari 2600 | Stella | ARM64 | Core adicionado ao catálogo |
| Atari Lynx | Handy | ARM64 | Requer `lynxboot.img` |
| PC Engine / TurboGrafx-16 / CD | Beetle PCE FAST | ARM64 | Requer BIOS para jogos CD |

## Frontend e instalação

O Retroboy usa a API Libretro com um frontend Android em Kotlin/Compose e uma biblioteca JNI/C++.

1. O gerenciador baixa pacotes oficiais do Buildbot Libretro para a ABI compatível com o aparelho.
2. O instalador extrai a biblioteca `.so`, verifica a ABI e valida os exports via `dlopen/dlsym`.
3. O frontend registra callbacks de ambiente, vídeo, áudio e input antes de `retro_init`.
4. O jogo é carregado por caminho absoluto quando o core exige full path ou por bytes quando aceita conteúdo em memória.
5. `retro_run` roda em thread dedicada.
6. O vídeo é convertido para RGBA_8888 e enviado a um `SurfaceView`.
7. O áudio PCM estéreo é drenado para `AudioTrack`.
8. Controles touch e gamepad são mapeados para o joypad Libretro.
9. Save states usam `retro_serialize`/`retro_unserialize`; SRAM é persistida em `filesDir/saves/`.

A CI compila o frontend. A execução real dos jogos ainda precisa ser validada em aparelhos Android físicos com arquivos legais/homebrew.

## Formatos de importação

- GB/GBC/GBA: `.gb`, `.gbc`, `.gba`.
- NES/Famicom: `.nes`, `.fds`, `.unif`, `.unf`.
- SNES: `.sfc`, `.smc`, `.swc`, `.fig`, `.bs`, `.st`.
- Sega 8/16-bit: `.md`, `.mdx`, `.smd`, `.gen`, `.sms`, `.gg`, `.sg`.
- PlayStation: `.pbp`, `.cue`, `.ccd`, `.chd`, `.iso`, `.img`, `.toc`, `.m3u`.
- Atari 2600: `.a26`.
- Atari Lynx: `.lnx`.
- PC Engine: `.pce`; CD usa cue/chd com BIOS compatível.
- Arcade/Neo Geo: romsets ZIP compatíveis com FBNeo.

Arquivos de CD em formato cue/bin precisam manter todos os arquivos referenciados juntos na mesma pasta. No importador atual, priorize arquivos de imagem autocontidos (`.chd`, `.pbp`, `.iso`) para PlayStation; para coleções cue/bin, extraia e mantenha os arquivos relacionados juntos antes da importação. Arquivos `.bin` isolados são identificados como GBA, então não use essa extensão isoladamente para outros sistemas.

## BIOS e firmware

- mGBA: `gba_bios.bin`, `gb_bios.bin`, `gbc_bios.bin` opcionais.
- FCEUmm/FDS: `disksys.rom` quando o jogo exigir Famicom Disk System.
- PCSX ReARMed: BIOS PS1 como `scph1001.bin`, `scph5501.bin` ou compatível; HLE é fallback com compatibilidade inferior.
- Handy: `lynxboot.img` obrigatório.
- Beetle PCE FAST: `syscard3.pce` para Super CD-ROM2; outras System Cards podem ser necessárias por jogo.
- FBNeo/Neo Geo: importe a BIOS própria do romset, normalmente `neogeo.zip`.

O importador de BIOS copia arquivos fornecidos pelo usuário para `filesDir/system/`. Não distribuímos ROMs comerciais nem BIOS protegidas.

## Vimm's Lair como fonte externa

O Download Center abre [Vimm's Lair — The Vault](https://vimm.net/vault/) no navegador. Não há integração por API oficial implementada nem scraping/download automático. O fluxo suportado é: abrir o catálogo externo, obter apenas conteúdo autorizado pelo usuário, importar o arquivo local e iniciar com o core correspondente.

## Limitações de vídeo e próximos núcleos

O frontend atual apresenta vídeo por callback de software em `SurfaceView`. Cores que exigem renderização gráfica acelerada pelo frontend (por exemplo, alguns caminhos de N64 e Dreamcast) ficam fora desta expansão até implementarmos contexto EGL/OpenGL/Vulkan e callbacks `RETRO_ENVIRONMENT_SET_HW_RENDER`.

## Licenças

- FBNeo: não comercial; precisa de revisão/licença antes de uso comercial.
- Snes9x e Genesis Plus GX: não comercial.
- gpSP, FCEUmm, PCSX ReARMed, Stella e Beetle PCE FAST: GPL (versões indicadas no catálogo).
- mGBA: MPL-2.0.
- Handy: zlib.

## Referências técnicas

- Libretro core list: https://docs.libretro.com/guides/core-list/
- Android core downloads: https://docs.libretro.com/guides/download-cores/
- FCEUmm: https://docs.libretro.com/library/fceumm/
- Snes9x: https://docs.libretro.com/library/snes9x/
- Genesis Plus GX: https://docs.libretro.com/library/genesis_plus_gx/
- PCSX ReARMed: https://docs.libretro.com/library/pcsx_rearmed/
- Stella: https://docs.libretro.com/library/stella/
- Handy: https://docs.libretro.com/library/handy/
- Beetle PCE FAST: https://docs.libretro.com/library/beetle_pce_fast/
