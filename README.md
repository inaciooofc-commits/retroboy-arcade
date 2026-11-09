# Retroboy Arcade

Aplicativo Android de jogos retrô com identidade visual arcade neon.

## Estado inicial

- Interface Android em Kotlin + Jetpack Compose.
- Login e cadastro por e-mail e senha preparados para Supabase Auth.
- Modo convidado local.
- Importação de arquivos pelo seletor Android.
- Catálogo JSON inicial.
- Catálogo de conquistas.
- Esquema SQL Supabase com RLS.
- GitHub Actions para gerar APK debug.

## Ainda em desenvolvimento

A execução real de ROMs depende da integração dos núcleos FBNeo e gpSP. O multiplayer P2P, incluindo Arcade Netplay e GBA RFU para Pokémon, também permanece pendente. Esta versão não distribui ROMs ou BIOS comerciais.

## Requisitos

- JDK 17
- Android SDK Platform 35
- Gradle 8.9

## Compilar

```bash
gradle --no-daemon :app:assembleDebug
```

O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Licenças e conteúdo

Distribua somente jogos, BIOS e assets que você tenha direito de redistribuir. A integração de motores de emulação deve respeitar as licenças de cada projeto.
