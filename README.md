# Retroboy Arcade — 0.4.0-beta

Aplicativo Android de jogos retrô, desenvolvido em Kotlin + Jetpack Compose, com identidade visual arcade neon e frontend nativo Libretro.

## O que já está implementado

- Interface Android com navegação, biblioteca e perfil do jogador.
- Logotipo com fonte personalizada Stranger e tipografia de display.
- Avatares, moldura neon e patente inicial; nome e avatar editáveis.
- ID local do jogador, nível/XP inicial, biblioteca, conquistas e medalhas.
- Importação de jogos GB/GBC/GBA, NES, SNES, Mega Drive/Master System/Game Gear, PlayStation, Atari 2600/Lynx, PC Engine e romsets Arcade/Neo Geo.
- Download de cores oficiais Libretro para FBNeo, gpSP, mGBA, FCEUmm, Snes9x, Genesis Plus GX, PCSX ReARMed, Stella, Handy e Beetle PCE FAST.
- Verificação das bibliotecas nativas antes da instalação.
- Frontend Libretro: `retro_init`, `retro_load_game`, `retro_run`, callbacks de vídeo/áudio/input.
- Renderização em `SurfaceView`, áudio estéreo por `AudioTrack`, controles touch e gamepad Bluetooth/USB.
- Save states por `retro_serialize`/`retro_unserialize` e persistência periódica de SRAM.
- Controles locais nos dois ports Libretro e painel de status com FPS e contador de frames.
- Salas P2P via WebRTC DataChannel; Supabase Realtime faz somente a sinalização SDP/ICE.
- Consulta de conquistas oficiais por jogo via hash da ROM e API RetroAchievements; chave protegida pelo Android Keystore.
- Importação de BIOS próprias para a pasta `system/`.
- Estrutura de acesso developer via Supabase Auth e RLS.

A CI do GitHub compila o APK. **Ainda faltam testes funcionais em dois aparelhos Android reais** para validar cada core, jogo, BIOS, áudio, controles e partidas P2P em redes diferentes.

## Qual core usar

| Sistema | Core | Uso recomendado |
|---|---|---|
| Neo Geo / Arcade | FinalBurn Neo (FBNeo) | Melhor escolha inicial para arcade e Neo Geo |
| Game Boy / Game Boy Color / GBA offline | mGBA | Compatibilidade e precisão para jogar offline |
| Pokémon FireRed — trocas | gpSP | Teste experimental do GBA Wireless Adapter |

**Atenção ao GBA online:** o netplay genérico do RetroArch não é link cable GBA. O FAQ do Libretro cita gpSP como exceção para trocas Pokémon via GBA Wireless Adapter; isso não garante batalhas nem todos os modos de FireRed. <https://docs.libretro.com/guides/netplay-faq/>

**Licença:** FBNeo é não comercial. Se o Retroboy for vendido, monetizado ou usado em atividade comercial, a escolha do core precisa ser revista ou licenciada. gpSP é GPLv2 e mGBA é MPLv2. <https://docs.libretro.com/library/fbneo/> <https://docs.libretro.com/library/gpsp/> <https://docs.libretro.com/library/mgba/>

## Multiplayer P2P

A versão 0.4 implementa WebRTC DataChannel para enviar os estados dos controles diretamente entre dois aparelhos. Supabase Realtime é usado apenas para trocar SDP/ICE; não transporta frames, áudio ou vídeo.

- Abra o mesmo arquivo de jogo e o mesmo core nos dois aparelhos.
- Um jogador cria uma sala e compartilha o código de 8 caracteres; o outro entra com esse código.
- O host é o jogador 1 (port 0) e o convidado é o jogador 2 (port 1).
- STUN público é configurado por padrão. Para redes com CGNAT ou NAT simétrico, configure um TURN próprio usando `RETROBOY_TURN_URL`, `RETROBOY_TURN_USERNAME` e `RETROBOY_TURN_CREDENTIAL`.
- Esta implementação sincroniza estados de botões por canal confiável; não é o protocolo de rollback/frame-lockstep do RetroArch. A latência depende da rede e cada aparelho executa localmente o mesmo jogo.

É necessário configurar `SUPABASE_URL` e `SUPABASE_ANON_KEY` no build para a sinalização.

## Fontes externas, ROMs e BIOS

O Download Center inclui acesso externo ao [Vimm's Lair — The Vault](https://vimm.net/vault/). O link abre o site no navegador; o Retroboy não faz scraping do catálogo, não espelha arquivos e não baixa ROMs de terceiros automaticamente. Após obter um arquivo que você tenha autorização para utilizar, importe-o pela biblioteca.

O app não distribui jogos comerciais nem BIOS protegidas. Importe somente arquivos que você tenha direito de usar.

- Para arcade/Neo Geo, importe romsets ZIP compatíveis com FBNeo. Para consoles, importe o arquivo de jogo extraído, não o ZIP de download.
- BIOS e firmware variam por sistema: Famicom Disk System (`disksys.rom`), Atari Lynx (`lynxboot.img`), PS1 (`scph*.bin`), PC Engine CD (`syscard*.pce`) e GB/GBC/GBA conforme o core.
- Os arquivos de sistema ficam em `filesDir/system/`; saves ficam em `filesDir/saves/`.

## Supabase e conta developer

1. Crie um projeto Supabase dedicado.
2. Execute `supabase/schema.sql` no SQL Editor.
3. Configure os secrets `SUPABASE_URL` e `SUPABASE_ANON_KEY` no GitHub Actions.
4. Instale o APK compilado com esses valores.
5. Cadastre-se no app com o e-mail developer autorizado e uma senha nova e exclusiva, definida diretamente no aparelho.
6. Confirme o e-mail. O proprietário do projeto executa `supabase/grant_developer.sql` para conceder a role developer.

Nunca coloque senha, `service_role` ou secret key no APK, no repositório ou no GitHub Actions. Consulte [Conta developer](docs/DEVELOPER_ACCOUNT.md).

## Configuração opcional de TURN

Para ampliar a compatibilidade P2P em redes restritivas, defina as três propriedades abaixo em `local.properties` ou como variáveis/secrets de build:

```properties
RETROBOY_TURN_URL=turn:seu-servidor:3478
RETROBOY_TURN_USERNAME=usuario
RETROBOY_TURN_CREDENTIAL=senha
```

## Compilar localmente

Requisitos: Android Studio recente, JDK 17, Android SDK Platform 35, NDK e CMake 3.22.1.

```bash
gradle :app:assembleDebug
```

APK esperado: `app/build/outputs/apk/debug/app-debug.apk`.

## Pacote gráfico

As fontes Stranger/NightWork e os recursos principais do perfil (avatares, moldura neon e patente bronze) já estão no build. A navegação e as telas são renderizadas em Compose. A integração do restante do pacote gráfico completo continua no roadmap.

## Próximas etapas

Consulte [Roadmap](docs/ROADMAP.md) e [Arquitetura de emulação](docs/EMULATION_ARCHITECTURE.md).
