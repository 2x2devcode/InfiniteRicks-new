#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=load-rpc-env.sh
source "$ROOT_DIR/scripts/load-rpc-env.sh"

BIND_HOST="${BIND_HOST:-127.0.0.1}"
API_PORT="${API_PORT:-40002}"
EXPLORER_PORT="${EXPLORER_PORT:-40051}"

if [[ -z "${RICK_RPC_USER:-}" || -z "${RICK_RPC_PASSWORD:-}" ]]; then
  echo "ERRO: credenciais RPC nao encontradas."
  echo "Defina RICK_RPC_USER e RICK_RPC_PASSWORD ou configure ~/.InfiniteRicks/InfiniteRicks.conf"
  exit 1
fi

LIB_DIR="$ROOT_DIR/rick-server/build/install/rick-server/lib"
if [[ ! -d "$LIB_DIR" ]]; then
  echo "Distribuicao nao encontrada. Compilando..."
  "$ROOT_DIR/scripts/build-server-services.sh"
fi

cleanup() {
  if [[ -n "${API_PID:-}" ]]; then
    kill "$API_PID" 2>/dev/null || true
  fi
  if [[ -n "${EXPLORER_PID:-}" ]]; then
    kill "$EXPLORER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "Iniciando servicos JSON em ${BIND_HOST} (sem interface web)..."
echo "  API:      ${BIND_HOST}:${API_PORT}  -> https://server.infinitericks.com"
echo "  Explorer: ${BIND_HOST}:${EXPLORER_PORT}  -> https://serverexplorer.infinitericks.com"
echo "  RPC:      ${RICK_RPC_HOST}:${RICK_RPC_PORT} (user=${RICK_RPC_USER})"

export BIND_HOST RICK_RPC_HOST RICK_RPC_PORT RICK_RPC_USER RICK_RPC_PASSWORD
PORT="$API_PORT" java -cp "$LIB_DIR/*" com.infinitericks.wallet.server.RickServer &
API_PID=$!

EXPLORER_PORT="$EXPLORER_PORT" java -cp "$LIB_DIR/*" com.infinitericks.wallet.server.RickExplorerServer &
EXPLORER_PID=$!

echo "API PID ${API_PID}, Explorer PID ${EXPLORER_PID}"
echo "Teste: curl -s http://${BIND_HOST}:${API_PORT}/api/health"
echo "Pressione Ctrl+C para encerrar."

wait
