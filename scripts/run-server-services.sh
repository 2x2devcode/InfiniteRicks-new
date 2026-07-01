#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

API_PORT="${API_PORT:-40002}"
EXPLORER_PORT="${EXPLORER_PORT:-40051}"
export RICK_RPC_HOST="${RICK_RPC_HOST:-127.0.0.1}"
export RICK_RPC_PORT="${RICK_RPC_PORT:-31648}"
export RICK_RPC_USER="${RICK_RPC_USER:-rickrpc}"
export RICK_RPC_PASSWORD="${RICK_RPC_PASSWORD:-rickrpc}"

cleanup() {
  if [[ -n "${API_PID:-}" ]]; then
    kill "$API_PID" 2>/dev/null || true
  fi
  if [[ -n "${EXPLORER_PID:-}" ]]; then
    kill "$EXPLORER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "Iniciando servicos JSON (sem interface web)..."
echo "  API:      :${API_PORT}"
echo "  Explorer: :${EXPLORER_PORT}"
echo "  RPC:      ${RICK_RPC_HOST}:${RICK_RPC_PORT}"

PORT="$API_PORT" ./gradlew :rick-server:runApi --quiet &
API_PID=$!

EXPLORER_PORT="$EXPLORER_PORT" ./gradlew :rick-server:runExplorer --quiet &
EXPLORER_PID=$!

echo "API PID ${API_PID}, Explorer PID ${EXPLORER_PID}"
echo "Pressione Ctrl+C para encerrar."

wait
