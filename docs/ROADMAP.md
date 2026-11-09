# Roadmap técnico

## Fase 0 — Fundação (esta entrega)
- [x] Projeto Android Kotlin/Compose
- [x] Identidade visual inicial Retroboy Arcade
- [x] Tela de cadastro/login Supabase Auth REST
- [x] Modo convidado
- [x] Importação local de arquivos
- [x] Catálogo JSON inicial
- [x] Conquistas iniciais
- [x] Esquema SQL com RLS
- [x] Workflow de APK debug

## Fase 1 — Emulação local
- [ ] Selecionar versões e licenças dos núcleos FBNeo e gpSP
- [ ] Compilar bibliotecas nativas ARM64
- [ ] Integrar Libretro frontend / JNI
- [ ] Implementar áudio, vídeo, input e save states
- [ ] Testar ROMs homebrew e arquivos próprios

## Fase 2 — Conta e catálogo
- [ ] Persistir sessão de forma segura com Android Keystore
- [ ] Perfil editável e username único
- [ ] Sincronizar conquistas e configurações
- [ ] DownloadManager com SHA-256 e retomada
- [ ] Atualização de manifestos e artwork

## Fase 3 — Multiplayer
- [ ] Avaliar Netplay FBNeo Android
- [ ] Implementar sinalização P2P
- [ ] Testar STUN e conectividade em redes CGNAT
- [ ] Implementar RFU GBA e validar Pokémon FireRed
- [ ] Adicionar TURN somente se a conexão direta não bastar

## Fase 4 — Qualidade e release
- [ ] Testes em aparelhos ARM64 de entrada/intermediários/avançados
- [ ] Testes de consumo de bateria e memória
- [ ] Testes de reconexão e integridade de saves
- [ ] Assinatura de release e publicação
