# Roadmap — Retroboy Arcade

## 0.3.0-alpha — base Android e runtime single-player

- [x] Interface Compose, navegação e perfil do jogador.
- [x] Fonte personalizada, avatares, moldura neon e patente inicial.
- [x] Nome editável, avatar e ID local.
- [x] Estrutura de autenticação Supabase e role developer com RLS.
- [x] Importação privada de ROMs GB/GBC/GBA e arcade ZIP/7z.
- [x] Importação de BIOS próprias.
- [x] Download e verificação dos cores FBNeo, gpSP e mGBA.
- [x] Host Libretro JNI/C++ com `retro_run`.
- [x] Vídeo em SurfaceView e áudio estéreo via AudioTrack.
- [x] Controles touch e gamepad Bluetooth/USB.
- [x] Save states e persistência periódica de SRAM.
- [x] Build Android em GitHub Actions.

## 0.3.1 — validação em aparelhos reais

- [ ] Testar FBNeo com romsets legais/homebrew e BIOS Neo Geo do usuário.
- [ ] Testar mGBA com GB, GBC e GBA, saves e save states.
- [ ] Testar gpSP com FireRed; validar somente trocas Wireless Adapter antes de ampliar a promessa.
- [ ] Testar áudio, latência de input, rotação, proporção de tela e desempenho em aparelhos de entrada/intermediários.
- [ ] Melhorar feedback de download e instalação dos cores.
- [ ] Validar recuperação de saves após fechar/forçar encerramento.

## 0.4 — multiplayer

- [ ] Integrar/portar protocolo de netplay RetroArch para FBNeo.
- [ ] Testar conexão direta e Relay Server público sem servidor próprio do jogador.
- [ ] Supabase Realtime para contas, presença, salas e convites.
- [ ] Validar gpSP Wireless Adapter para trocas Pokémon entre dois aparelhos.
- [ ] Não anunciar link cable genérico ou batalhas FireRed sem testes específicos.

## Backend e social

- [ ] Criar projeto Supabase dedicado e aplicar schema.
- [ ] Configurar secrets de build.
- [ ] Criar conta developer pelo fluxo de cadastro do app e validar role.
- [ ] Sincronizar perfil, estatísticas, conquistas e amigos.

## Identidade visual

- [x] Fonte do título e tipografia de display.
- [x] Recursos de avatar, moldura e patente do perfil.
- [ ] Integrar o restante do pacote gráfico completo: telas, barras, ícones, navegação e componentes.
