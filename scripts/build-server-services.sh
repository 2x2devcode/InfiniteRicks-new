#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "Compilando API oficial (porta ${API_PORT:-40002}) e explorer (porta ${EXPLORER_PORT:-40051})..."
./gradlew :rick-server:build

echo ""
echo "Build concluido."
echo "  API:      RickServer      -> porta ${API_PORT:-40002}  (/api/*)"
echo "  Explorer: RickExplorerServer -> porta ${EXPLORER_PORT:-40051}  (/ext/*)"
echo "  Sem interface web — somente JSON para o aplicativo."
echo ""
echo "Para iniciar ambos no mesmo servidor:"
echo "  bash scripts/run-server-services.sh"
