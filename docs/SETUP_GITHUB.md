# Publicar no GitHub

A integração GitHub disponível nesta sessão permite consultar repositórios e editar arquivos existentes, mas não expõe a operação de criar repositório. Por isso, o projeto inclui `scripts/publish-to-github.sh` para criar e publicar o repositório na conta conectada usando o GitHub CLI.

1. Instale Git e GitHub CLI: https://cli.github.com/
2. Faça login na conta `inaciooofc-commits`: `gh auth login`
3. Na raiz do projeto, execute: `./scripts/publish-to-github.sh`
4. O script cria `inaciooofc-commits/retroboy-arcade` como privado, ou publica nele se já existir.

A escolha de repositório privado é o padrão mais seguro para este projeto pessoal. O catálogo de downloads precisará de uma localização pública própria ou de um mecanismo de distribuição autenticado; não coloque ROMs/BIOS protegidas no repositório.
