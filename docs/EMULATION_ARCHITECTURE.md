# Arquitetura de emulação — Retroboy Arcade

## Matriz de cores

| Sistema | Core | Motivo | Situação |
|---|---|---|---|
| Neo Geo | FinalBurn Neo (FBNeo) | Forte compatibilidade com Neo Geo e arcade; netplay upstream | Runtime single-player integrado; validar ROMs e BIOS no aparelho |
| Arcade | FinalBurn Neo (FBNeo) | Cobertura ampla de placas clássicas | Runtime single-player integrado; validar romsets no aparelho |
| GB / GBC / GBA offline | mGBA | Precisão, compatibilidade e saves | Runtime single-player integrado; validar jogos e SRAM |
| Pokémon FireRed — trocas | gpSP | Emula GBA Wireless Adapter, exceção documentada para trocas Pokémon | Runtime single-player integrado; recurso sem fio ainda experimental |

## Arquitetura do frontend

O Retroboy usa a API Libretro com um frontend Android próprio em Kotlin/Compose e uma biblioteca JNI/C++.

1. O gerenciador baixa os pacotes oficiais do Buildbot Libretro para ARM64, ARMv7 ou x86_64.
2. O instalador extrai a biblioteca `.so`, verifica a ABI e valida os exports Libretro via `dlopen/dlsym`.
3. O frontend registra callbacks de ambiente, vídeo, áudio e input antes de `retro_init`.
4. O jogo é carregado por caminho absoluto quando o core exige full path ou por bytes quando aceita conteúdo em memória.
5. `retro_run` roda em uma thread dedicada.
6. O vídeo é convertido para RGBA_8888 e enviado a um `SurfaceView`.
7. O áudio estéreo PCM 16-bit é drenado de um buffer nativo para `AudioTrack`.
8. O input touch e gamepad é mapeado para os IDs padrão de joypad Libretro.
9. Save states usam `retro_serialize`/`retro_unserialize`; SRAM é lida por `RETRO_MEMORY_SAVE_RAM` e persistida em `filesDir/saves/`.

A CI compila o frontend. A execução real dos jogos ainda precisa ser validada em aparelhos Android físicos com arquivos de teste legais/homebrew.

## Conteúdo e BIOS

- Extensões aceitas pelo importador: `.gba`, `.gb`, `.gbc`, `.zip` e `.7z`.
- mGBA aceita GB, GBC e GBA; `gba_bios.bin`, `gb_bios.bin` e `gbc_bios.bin` são opcionais. O frontend ativa a opção `mgba_use_bios=ON` para aproveitar a BIOS importada.
- gpSP aceita GBA em `.gba` ou `.bin`; `gba_bios.bin` é opcional.
- FBNeo aceita romsets ZIP/7z. Alguns sistemas precisam de BIOS; para Neo Geo, importe `neogeo.zip`.
- O importador de BIOS copia arquivos do usuário para `filesDir/system/`.
- O Retroboy não distribui ROMs comerciais nem BIOS protegidas.

Para FBNeo, confira os requisitos de romset e BIOS da documentação oficial: <https://docs.libretro.com/library/fbneo/>.

## Pokémon FireRed e GBA

O netplay genérico do RetroArch não é emulação de link cable GBA. O FAQ oficial cita gpSP, que emula o GBA Wireless Adapter, como exceção para **trocas Pokémon**. Isso não garante batalhas sem fio nem todos os modos de FireRed/LeafGreen. O recurso deve permanecer experimental até ser validado em dois aparelhos com o mesmo core, versão e conteúdo.

mGBA é o core padrão para jogar GB/GBC/GBA offline. A documentação informa que mGBA não oferece netplay upstream; gpSP anuncia netplay, mas o uso de Wireless Adapter precisa de teste específico.

## Multiplayer entre casas — melhor caminho

Para Neo Geo/Arcade, a melhor opção é reutilizar/portar o protocolo de netplay do RetroArch/Libretro, não inventar sincronização de frames do zero.

- O netplay RetroArch sincroniza inputs por frame e usa rollback/replay; a experiência ideal exige core determinístico, mesma versão do core, conteúdo idêntico e suporte a serialização.
- O host normalmente precisa aceitar conexões TCP na porta 55435.
- Se o jogador não puder abrir portas ou estiver atrás de NAT/CGNAT, o RetroArch oferece Relay Server público, que encaminha os dois lados sem exigir servidor próprio do jogador.
- O Retroboy ainda precisa integrar esse protocolo. O suporte a netplay do core, sozinho, não ativa partidas online no app.
- Supabase Realtime deve cuidar de contas, presença, salas e convites. Não deve transportar inputs a cada frame, áudio ou vídeo.

Para GBA, não anunciar link cable genérico. gpSP é a opção experimental para testar trocas Pokémon via Wireless Adapter; batalhas não estão garantidas.

## Licenças

- FBNeo: licença não comercial. Não usar em produto ou atividade comercial sem autorização/licença adequada.
- gpSP: GPLv2.
- mGBA: MPLv2.

## Referências técnicas

- Libretro API: https://docs.libretro.com/development/libretro-overview/
- FBNeo: https://docs.libretro.com/library/fbneo/
- gpSP: https://docs.libretro.com/library/gpsp/
- mGBA: https://docs.libretro.com/library/mgba/
- Netplay FAQ: https://docs.libretro.com/guides/netplay-faq/
- Netplay Getting Started / Relay Server: https://docs.libretro.com/guides/netplay-getting-started/
- Netplay rollback internals: https://docs.libretro.com/development/retroarch/netplay/
- Android core buildbot: https://buildbot.libretro.com/nightly/android/latest/
