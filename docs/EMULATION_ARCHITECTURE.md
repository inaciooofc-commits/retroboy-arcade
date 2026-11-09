# Arquitetura de emulação — Retroboy Arcade

## Escolha dos motores

| Sistema | Núcleo recomendado | Motivo | Situação |
|---|---|---|---|
| Neo Geo | FinalBurn Neo (FBNeo) | Bom suporte a Neo Geo e arcade; netplay disponível | Download/probe no 0.2; runtime pendente |
| Arcade | FinalBurn Neo (FBNeo) | Ampla cobertura de placas clássicas e netplay | Download/probe no 0.2; runtime pendente |
| GBA offline | mGBA | Prioridade em precisão e compatibilidade | Download/probe no 0.2; runtime pendente |
| Pokémon FireRed online | gpSP | O core oferece netplay e emulação do adaptador sem fio GBA | Download/probe no 0.2; teste de jogo pendente |

## Por que Libretro

O Libretro separa o núcleo de emulação do frontend. O Retroboy mantém sua própria interface Android em Kotlin/Compose e usa bibliotecas nativas compatíveis com a API Libretro. O frontend precisa fornecer callbacks de vídeo, áudio, input, ambiente, ciclo de execução, carregamento de conteúdo e gerenciamento de saves.

Nesta versão, o gerenciador baixa os pacotes Android ARM64/ARMv7 diretamente do buildbot oficial, extrai a biblioteca e verifica os exports essenciais via dlopen/dlsym. Isso valida a biblioteca, mas ainda não executa ROMs. A próxima etapa é implementar o host Libretro nativo com EGL/Surface, áudio, controles e retro_run.

## Pokémon FireRed e multiplayer GBA

A documentação do Libretro informa que o netplay genérico não é emulação de cabo link GBA. A exceção documentada para trocas Pokémon é o gpSP, que emula o GBA Wireless Adapter. Por isso, gpSP é a opção a testar para as funções sem fio de FireRed/LeafGreen; não se deve prometer que todos os modos de cabo link serão suportados.

mGBA é a opção local/offline de maior precisão para GBA, mas seu core Libretro não anuncia netplay. Uma implementação experimental de link Wi-Fi baseada em mGBA existe, porém não deve ser a base da primeira versão estável.

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
