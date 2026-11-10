# Retroboy Arcade — 0.3.0-alpha

Aplicativo Android de jogos retrô, desenvolvido em Kotlin + Jetpack Compose, com identidade visual arcade neon e frontend nativo Libretro.

## O que já está implementado

- Interface Android com navegação, biblioteca e perfil do jogador.
- Logotipo com fonte personalizada Stranger e tipografia de display.
- Avatares, moldura neon e patente inicial; nome e avatar editáveis.
- ID local do jogador, nível/XP inicial, biblioteca, conquistas e medalhas.
- Importação de ROMs `.gba`, `.gb`, `.gbc`, `.zip` e `.7z` para armazenamento privado.
- Download de cores oficiais Libretro FBNeo, gpSP e mGBA para ARM64, ARMv7 e x86_64.
- Verificação das bibliotecas nativas antes da instalação.
- Frontend Libretro: `retro_init`, `retro_load_game`, `retro_run`, callbacks de vídeo/áudio/input.
- Renderização em `SurfaceView`, áudio estéreo por `AudioTrack`, controles touch e gamepad Bluetooth/USB.
- Save states por `retro_serialize`/`retro_unserialize` e persistência periódica de SRAM.
- Importação de BIOS próprias para a pasta `system/`.
- Estrutura de acesso developer via Supabase Auth e RLS.

A CI do GitHub compila o APK. **Ainda faltam testes funcionais em aparelho Android real** para validar cada core, jogo, BIOS, áudio, controles e desempenho.

## Qual core usar

| Sistema | Core | Uso recomendado |
|---|---|---|
| Neo Geo / Arcade | FinalBurn Neo (FBNeo) | Melhor escolha inicial para arcade e Neo Geo |
| Game Boy / Game Boy Color / GBA offline | mGBA | Compatibilidade e precisão para jogar offline |
| Pokémon FireRed — trocas | gpSP | Teste experimental do GBA Wireless Adapter |

**Atenção ao GBA online:** o netplay genérico do RetroArch não é link cable GBA. O FAQ do Libretro cita gpSP como exceção para trocas Pokémon via GBA Wireless Adapter; isso não garante batalhas nem todos os modos de FireRed. <https://docs.libretro.com/guides/netplay-faq/>

**Licença:** FBNeo é não comercial. Se o Retroboy for vendido, monetizado ou usado em atividade comercial, a escolha do core precisa ser revista ou licenciada. gpSP é GPLv2 e mGBA é MPLv2. <https://docs.libretro.com/library/fbneo/> <https://docs.libretro.com/library/gpsp/> <https://docs.libretro.com/library/mgba/>

## Multiplayer entre casas — melhor caminho

Para Neo Geo/Arcade, o plano é reutilizar o protocolo de netplay do RetroArch/Libretro, que sincroniza inputs por frame e usa rollback com save states. Os dois jogadores precisam usar a mesma versão do core e o mesmo conteúdo.

- O host normalmente recebe conexões TCP na porta 55435.
- Para não configurar portas no roteador, o RetroArch oferece Relay Server público, que encaminha a conexão dos dois lados.
- O Retroboy ainda precisa integrar esse protocolo; o suporte existente no FBNeo não ativa multiplayer automaticamente no nosso frontend.
- Supabase Realtime será usado para contas, presença, salas e convites, não para transportar frames, áudio ou vídeo.

Referências: <https://docs.libretro.com/development/retroarch/netplay/> e <https://docs.libretro.com/guides/netplay-getting-started/>.

## ROMs e BIOS

O app não distribui jogos comerciais nem BIOS protegidas. Importe somente arquivos que você tenha direito de usar.

- Para arcade/Neo Geo, importe ROMs em `.zip` ou `.7z`. Alguns jogos precisam de BIOS; para Neo Geo, o arquivo usual é `neogeo.zip`.
- Para GBA, `gba_bios.bin` é opcional para mGBA e gpSP.
- Os arquivos de sistema ficam em `filesDir/system/`; saves ficam em `filesDir/saves/`.

## Supabase e conta developer

1. Crie um projeto Supabase dedicado.
2. Execute `supabase/schema.sql` no SQL Editor.
3. Configure os secrets `SUPABASE_URL` e `SUPABASE_ANON_KEY` no GitHub Actions.
4. Instale o APK compilado com esses valores.
5. Cadastre-se no app com o e-mail developer autorizado e uma senha nova e exclusiva, definida diretamente no aparelho.
6. Confirme o e-mail. O trigger do banco atribui a role developer.

Nunca coloque senha, `service_role` ou secret key no APK, no repositório ou no GitHub Actions. Consulte [Conta developer](docs/DEVELOPER_ACCOUNT.md).

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
