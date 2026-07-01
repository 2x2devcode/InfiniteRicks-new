#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

BIND_HOST="${BIND_HOST:-127.0.0.1}"
API_PORT="${API_PORT:-40002}"
EXPLORER_PORT="${EXPLORER_PORT:-40051}"
export RICK_RPC_HOST="${RICK_RPC_HOST:-127.0.0.1}"
export RICK_RPC_PORT="${RICK_RPC_PORT:-31648}"
export RICK_RPC_USER="${RICK_RPC_USER:-rickrpc}"
export RICK_RPC_PASSWORD="${RICK_RPC_PASSWORD:-rickrpc}"

if [[ "${RICK_RPC_PASSWORD}" == "rickrpc" ]]; then
  echo "AVISO: RICK_RPC_PASSWORD usa o valor padrao 'rickrpc'."
  echo "       Defina a senha real do daemon: export RICK_RPC_PASSWORD='...'"
fi

LIB_DIR="$ROOT_DIR/rick-server/build/install/rick-server/lib"
if [[ ! -d "$LIB_DIR" ]]; then
  echo "Distribuicao nao encontrada. Compilando..."
  ./gradlew :rick-server:installDist -q
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
echo "  RPC:      ${RICK_RPC_HOST}:${RICK_RPC_PORT}"

export BIND_HOST
PORT="$API_PORT" java -cp "$LIB_DIR/*" com.infinitericks.wallet.server.RickServer &
API_PID=$!

EXPLORER_PORT="$EXPLORER_PORT" java -cp "$LIB_DIR/*" com.infinitericks.wallet.server.RickExplorerServer &
EXPLORER_PID=$!

echo "API PID ${API_PID}, Explorer PID ${EXPLORER_PID}"
echo "Teste local: curl -s http://${BIND_HOST}:${API_PORT}/api/status"
echo "Pressione Ctrl+C para encerrar."

wait
