# Retroboy Arcade — 0.2.0-alpha

Aplicativo Android de jogos retrô, com identidade visual arcade neon. O projeto começa com uma base Android nativa em Kotlin + Jetpack Compose.

## Estado desta base (0.1.0-alpha)

Implementado nesta fundação:
- Interface inicial arcade neon e navegação inferior.
- Cadastro e login por e-mail/senha via Supabase Auth REST.
- Modo convidado local.
- Importação de arquivos pelo seletor Android e persistência de URI.
- Catálogo GitHub em JSON (inicialmente vazio).
- Catálogo inicial de conquistas.
- Esquema SQL Supabase com RLS.
- Workflow GitHub Actions para gerar APK debug.

Ainda não implementado nesta versão:
- Execução de ROMs: os núcleos FBNeo e gpSP precisam ser integrados e compilados para Android.
- Multiplayer P2P funcional (Arcade Netplay e GBA RFU).
- Sincronização de progresso e amizades via Supabase.
- Downloads de assets com verificação SHA-256.
- Catálogo real de jogos comerciais. ROMs e BIOS protegidas não são distribuídas.

## Requisitos

- Android Studio recente
- JDK 17
- Android SDK Platform 35
- Gradle 8.9

## Configurar Supabase

Crie um projeto Supabase dedicado e execute `supabase/schema.sql` no SQL Editor. Em `local.properties`, adicione:

```properties
SUPABASE_URL=https://SEU-PROJETO.supabase.co
SUPABASE_ANON_KEY=SUA_CHAVE_ANON_PUBLICA
RETROBOY_CATALOG_URL=https://raw.githubusercontent.com/SEU_USUARIO/retroboy-arcade-catalog/main/catalog.json
```

Apenas a chave anon/publishable pode ser usada no app com RLS corretamente configurado. Nunca use `service_role` no cliente Android.

## Compilar localmente

```bash
gradle :app:assembleDebug
```

APK esperado: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

O workflow `.github/workflows/android-build.yml` compila o APK debug e publica o arquivo como artefato da execução. Configure os secrets `SUPABASE_URL` e `SUPABASE_ANON_KEY` no repositório para habilitar autenticação na build.

## Catálogo de downloads

O arquivo `catalog/catalog.json` é o manifesto inicial. Como o repositório do aplicativo será privado, o catálogo deve ficar em um repositório público separado e ser informado em `RETROBOY_CATALOG_URL`. Publique somente arquivos que possam ser redistribuídos legalmente. Para arquivos de terceiros, prefira links oficiais e hashes SHA-256 verificados.

## Licenças dos motores

A integração de FBNeo, gpSP e demais componentes deve respeitar a licença de cada projeto, incluindo disponibilização do código-fonte correspondente quando exigido. Este repositório não inclui ROMs nem BIOS comerciais.


## Emulação — status honesto da versão 0.2.0-alpha

A tela Downloads agora pode baixar e validar as bibliotecas oficiais FBNeo, gpSP e mGBA para Android ARM64/ARMv7. A validação confirma que a biblioteca abre e exporta as funções principais da API Libretro. Isso ainda não é o runtime que renderiza e executa um jogo; o frontend Libretro (vídeo, áudio, input, save states e ciclo retro_run) continua sendo a próxima etapa.

- Neo Geo/Arcade: FBNeo é o núcleo principal e suporta netplay, mas sua licença é não comercial.
- GBA offline: mGBA é a opção de precisão/compatibilidade.
- Pokémon FireRed online: gpSP é a opção experimental indicada por oferecer netplay e emulação do adaptador sem fio GBA. Os modos de troca/batalha precisam ser validados com duas cópias idênticas do jogo.
- A sincronização de partidas não está ativada nesta versão.

Veja docs/EMULATION_ARCHITECTURE.md, docs/DEVELOPER_ACCOUNT.md e docs/VISUAL_IDENTITY.md.
