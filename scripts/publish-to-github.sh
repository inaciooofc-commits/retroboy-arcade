#!/usr/bin/env bash
set -euo pipefail

OWNER="inaciooofc-commits"
REPO="retroboy-arcade"
FULL_NAME="$OWNER/$REPO"

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) não encontrado. Instale: https://cli.github.com/"
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "Faça login na conta $OWNER com: gh auth login"
  exit 1
fi

if gh repo view "$FULL_NAME" >/dev/null 2>&1; then
  echo "O repositório $FULL_NAME já existe. Configurando remote e enviando os arquivos."
  git remote remove origin 2>/dev/null || true
  git remote add origin "https://github.com/$FULL_NAME.git"
  git push -u origin HEAD:main
else
  echo "Criando $FULL_NAME como repositório privado e enviando a branch main."
  gh repo create "$FULL_NAME" --private --source=. --remote=origin --push
fi

echo "Publicado: https://github.com/$FULL_NAME"
