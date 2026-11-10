# Arquitetura de emulação — Retroboy Arcade

## Escolha dos motores

| Sistema | Núcleo recomendado | Motivo | Situação |
|---|---|---|---|
| Neo Geo | FinalBurn Neo (FBNeo) | Bom suporte a Neo Geo e arcade; netplay upstream | Runtime single-player integrado; validar BIOS neogeo.zip e ROMs no aparelho |
| Arcade | FinalBurn Neo (FBNeo) | Ampla cobertura de placas clássicas e netplay upstream | Runtime single-player integrado; validar ROMs no aparelho |
| GBA offline | mGBA | Prioridade em precisão e compatibilidade | Runtime single-player integrado; validar ROMs e SRAM no aparelho |
| Pokémon FireRed — trocas/batalhas sem fio | gpSP | O Libretro cita gpSP para emular o GBA Wireless Adapter; modos de FireRed precisam de teste real | Runtime single-player integrado; Wireless Adapter ainda experimental e não testado |

## Por que Libretro

O Libretro separa o núcleo de emulação do frontend. O Retroboy mantém sua própria interface Android em Kotlin/Compose e usa bibliotecas nativas compatíveis com a API Libretro. O frontend fornece callbacks de vídeo, áudio, input e ambiente, ciclo retro_run, carregamento de conteúdo, gerenciamento de save states e SRAM.

O gerenciador baixa os pacotes Android ARM64/ARMv7/x86_64 diretamente do buildbot oficial, extrai a biblioteca e verifica os exports essenciais via dlopen/dlsym. O frontend nativo Libretro já carrega o core e o arquivo, inicializa callbacks de ambiente, vídeo, áudio e input, executa retro_run em uma thread dedicada, renderiza no SurfaceView e envia áudio estéreo para AudioTrack. O host também implementa pause/reset, controles joypad, save states e leitura/gravação de SRAM. A CI compila essa integração; os testes funcionais com jogos reais ainda precisam ocorrer em aparelho Android.

## Pokémon FireRed e multiplayer GBA

A documentação do Libretro informa que o netplay genérico não é emulação de cabo link GBA. A exceção documentada para trocas Pokémon é o gpSP, que emula o GBA Wireless Adapter. Por isso, gpSP é a opção a testar para as funções sem fio de FireRed/LeafGreen; não se deve prometer que todos os modos de cabo link serão suportados.

mGBA é a opção local/offline recomendada para GBA, mas seu core Libretro não anuncia netplay. Uma implementação experimental de link Wi-Fi baseada em mGBA existe, porém não deve ser a base da primeira versão estável.

## Multiplayer entre casas

- FBNeo e gpSP anunciam suporte a netplay no ecossistema RetroArch.
- Os dois aparelhos precisam usar a mesma versão do frontend, o mesmo core e conteúdo idêntico.
- A conexão direta pela internet pode exigir encaminhamento de porta no host; redes CGNAT podem impedir conexão direta.
- Supabase Realtime deve cuidar de login, convites e salas, não transportar os frames do jogo.
- Se a conexão P2P direta falhar, é necessário um relay/TURN ou o relay de netplay compatível. Sem relay, não há garantia de conexão entre quaisquer duas redes domésticas.

## Licenças e distribuição

- FBNeo usa licença não comercial. Adequado apenas ao escopo pessoal/não comercial definido; não vender, monetizar ou incluir em produto comercial sem autorização.
- gpSP: GPL-2.0.
- mGBA: MPL-2.0.
- Não distribuir ROMs comerciais, BIOS protegidas ou arquivos de jogos de terceiros. O usuário importa os arquivos que possui direito de usar.
- As bibliotecas são baixadas do buildbot oficial Libretro; o Retroboy não hospeda ROMs nem binários dos núcleos.

## Fontes técnicas

- Libretro API: https://docs.libretro.com/development/libretro-overview/
- FBNeo: https://docs.libretro.com/library/fbneo/
- gpSP: https://docs.libretro.com/library/gpsp/
- mGBA: https://docs.libretro.com/library/mgba/
- Netplay FAQ: https://docs.libretro.com/guides/netplay-faq/
- Android ARM64 buildbot: https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/


## Escopo real do multiplayer

- O netplay do RetroArch sincroniza instâncias do mesmo core e do mesmo conteúdo; não é um serviço genérico de link cable GBA.
- Para trocas Pokémon, o FAQ do Libretro cita gpSP por emular o GBA Wireless Adapter. A compatibilidade de batalhas sem fio de FireRed precisa ser validada em testes reais; não anunciar como concluída antes disso.
- Para Neo Geo/Arcade, FBNeo anuncia suporte a netplay no ecossistema RetroArch, mas o Retroboy ainda precisa de um frontend/protocolo compatível.
- O Supabase Realtime deve cuidar de contas, convites e sinalização, não transportar o vídeo/áudio nem o estado completo da emulação.
- Para funcionar entre quaisquer duas casas, inclusive redes CGNAT, será necessário um TURN/relay gerenciado ou um relay de netplay compatível. Sem isso, conexão direta não é garantida.


## Implementação atual do frontend

- `retro_set_environment` e callbacks de vídeo/áudio/input são registrados antes de `retro_init`.
- `retro_load_game` recebe caminho absoluto quando o core exige full path e bytes do arquivo quando aceita conteúdo em memória.
- O vídeo é convertido para RGBA_8888 e apresentado em `SurfaceView`.
- O áudio estéreo PCM 16-bit é drenado de um buffer nativo para `AudioTrack`.
- Controles touch e gamepads Android são mapeados para os IDs de joypad Libretro.
- Save states usam `retro_serialize`/`retro_unserialize`; bateria usa `RETRO_MEMORY_SAVE_RAM` e é persistida em `filesDir/saves/`.
- BIOS do usuário ficam em `filesDir/system/`. Para Neo Geo, importe `neogeo.zip`.
- O runtime é single-player nesta etapa. A existência de netplay no core não significa que o frontend Retroboy já implemente a conexão.
