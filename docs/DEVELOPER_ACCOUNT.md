# Conta de desenvolvedor — Retroboy Arcade

A conta developer é uma conta comum do Supabase Auth com uma role privilegiada no banco. O e-mail autorizado é `inaciooofc@gmail.com`.

## Ativação

1. Criar um projeto Supabase exclusivo para Retroboy Arcade.
2. Aplicar `supabase/schema.sql` no SQL Editor.
3. Configurar os secrets do GitHub Actions:
   - `SUPABASE_URL`
   - `SUPABASE_ANON_KEY` (somente chave publishable/anon; nunca service_role ou secret key).
4. Compilar e instalar o APK com essas configurações.
5. Na tela de cadastro, criar a conta usando `inaciooofc@gmail.com` e uma senha nova e exclusiva, digitada diretamente no aparelho.
6. Confirmar o endereço de e-mail e entrar no app.
7. O trigger do banco concede automaticamente `developer` a esse endereço. Para uma conta que já existia antes da migração, executar `supabase/grant_developer.sql` uma vez.

## Segurança

- A senha enviada em conversa não deve ser reutilizada; troque-a por uma senha exclusiva.
- Nenhuma senha é armazenada em Kotlin, SQL, GitHub ou no APK.
- A role é concedida pelo banco, não por uma comparação de e-mail feita no cliente.
- A tabela `user_roles` permite somente leitura da própria linha e não permite que clientes concedam ou alterem papéis.
