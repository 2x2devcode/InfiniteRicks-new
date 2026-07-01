#!/usr/bin/env bash
set -euo pipefail

# shellcheck source=load-rpc-env.sh
source "$(cd "$(dirname "$0")" && pwd)/load-rpc-env.sh"

API_PORT="${API_PORT:-40002}"
EXPLORER_PORT="${EXPLORER_PORT:-40051}"
BIND_HOST="${BIND_HOST:-127.0.0.1}"
CONF="${INFINITE_RICKS_CONF:-${HOME}/.InfiniteRicks/InfiniteRicks.conf}"

rpc_call() {
  local method=$1
  curl -sS --user "${RICK_RPC_USER}:${RICK_RPC_PASSWORD}" \
    --data "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"${method}\",\"params\":[]}" \
    -H 'content-type: application/json' \
    "http://${RICK_RPC_HOST}:${RICK_RPC_PORT}/"
}

echo "=== Diagnostico InfiniteRicks ==="
echo ""

echo "0) Arquivo de configuracao: ${CONF}"
if [[ -f "$CONF" ]]; then
  echo "   rpcuser=$(grep -E '^[[:space:]]*rpcuser=' "$CONF" | tail -1 | cut -d= -f2- | tr -d '\r' || true)"
  if grep -qE '^[[:space:]]*rpcpassword=' "$CONF"; then
    echo "   rpcpassword=(definido no arquivo)"
  else
    echo "   rpcpassword=NAO DEFINIDO"
  fi
else
  echo "   FALHA: arquivo nao encontrado"
fi
echo "   Usando RPC user=${RICK_RPC_USER:-?} host=${RICK_RPC_HOST}:${RICK_RPC_PORT}"
echo ""

echo "1) Porta RPC ${RICK_RPC_HOST}:${RICK_RPC_PORT}"
if ! nc -z "$RICK_RPC_HOST" "$RICK_RPC_PORT" 2>/dev/null; then
  echo "   FALHA: nada escutando. Inicie: infinitericksd -daemon"
else
  echo "   OK: porta aberta"
fi
echo ""

echo "2) JSON-RPC getinfo"
if [[ -z "${RICK_RPC_USER:-}" || -z "${RICK_RPC_PASSWORD:-}" ]]; then
  echo "   FALHA: credenciais RPC ausentes"
else
  RPC_RESULT="$(rpc_call getinfo 2>&1 || true)"
  if echo "$RPC_RESULT" | grep -qi '401 Unauthorized'; then
    echo "   FALHA: 401 Unauthorized — usuario/senha NAO batem com o daemon."
    echo "   Use exatamente rpcuser/rpcpassword de ${CONF}"
    echo "   Depois de alterar o conf: infinitericksd stop ; sleep 2 ; infinitericksd -daemon"
  elif echo "$RPC_RESULT" | grep -q '"result"'; then
    echo "   OK: $(echo "$RPC_RESULT" | head -c 300)"
  elif echo "$RPC_RESULT" | grep -q '"error"'; then
    echo "   FALHA: $RPC_RESULT"
  else
    echo "   Resposta: $RPC_RESULT"
  fi
fi
echo ""

echo "3) API local ${BIND_HOST}:${API_PORT}/api/health"
curl -sS -w "\n   HTTP %{http_code}\n" "http://${BIND_HOST}:${API_PORT}/api/health" 2>/dev/null || echo "   FALHA: API nao esta rodando"
echo ""

echo "4) Explorer local ${BIND_HOST}:${EXPLORER_PORT}/ext/health"
curl -sS -w "\n   HTTP %{http_code}\n" "http://${BIND_HOST}:${EXPLORER_PORT}/ext/health" 2>/dev/null || echo "   FALHA: explorer nao esta rodando"
