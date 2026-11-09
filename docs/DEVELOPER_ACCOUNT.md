# Conta de desenvolvedor do Retroboy Arcade

A conta do desenvolvedor é uma conta normal do Supabase Auth com uma role privilegiada armazenada no banco. A role não é definida por e-mail no APK e não pode ser concedida pelo próprio usuário.

## Preparação

1. Criar um projeto Supabase exclusivo para Retroboy Arcade.
2. Executar supabase/schema.sql no SQL Editor (ou aplicar a migração equivalente).
3. Configurar os secrets do GitHub Actions:
   - SUPABASE_URL
   - SUPABASE_ANON_KEY (pode conter a chave publishable atual; nunca usar service_role/secret key)
4. Compilar e instalar o app com esses dados.

## Criar a conta

1. Na tela inicial, escolha Criar conta.
2. Use o endereço inaciooofc@gmail.com.
3. Defina uma senha nova e exclusiva diretamente no aparelho; não reutilize a senha enviada na conversa.
4. Confirme o e-mail se a confirmação estiver ativada.
5. Entre no aplicativo com essa conta.

## Liberar o acesso developer

Depois que a conta estiver criada e confirmada, execute supabase/grant_developer.sql no SQL Editor do projeto. O script atribui a role developer ao usuário pelo e-mail, sem armazenar senha.

Saia e entre novamente no app. O Retroboy consulta public.user_roles usando o access token do usuário. A tabela tem RLS: cada pessoa só pode ler sua própria role e nenhum cliente pode criar ou alterar permissões.

## Segurança

- Não colocar senhas em Kotlin, SQL, GitHub, APK ou GitHub Actions.
- Não colocar service_role ou secret key no app. A chave publishable/anon é a única chave permitida no cliente, sempre com RLS.
- A atribuição da role exige acesso de proprietário ao SQL Editor.
