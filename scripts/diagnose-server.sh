#!/usr/bin/env bash
set -euo pipefail

RPC_HOST="${RICK_RPC_HOST:-127.0.0.1}"
RPC_PORT="${RICK_RPC_PORT:-31648}"
RPC_USER="${RICK_RPC_USER:-rickrpc}"
RPC_PASSWORD="${RICK_RPC_PASSWORD:-rickrpc}"
API_PORT="${API_PORT:-40002}"
EXPLORER_PORT="${EXPLORER_PORT:-40051}"
BIND_HOST="${BIND_HOST:-127.0.0.1}"

rpc_call() {
  local method=$1
  curl -sS --user "${RPC_USER}:${RPC_PASSWORD}" \
    --data "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"${method}\",\"params\":[]}" \
    -H 'content-type: application/json' \
    "http://${RPC_HOST}:${RPC_PORT}/"
}

echo "=== Diagnostico InfiniteRicks ==="
echo ""

echo "1) Porta RPC ${RPC_HOST}:${RPC_PORT}"
if ! nc -z "$RPC_HOST" "$RPC_PORT" 2>/dev/null; then
  echo "   FALHA: nada escutando. Inicie o infinitericksd e confira InfiniteRicks.conf (server=1, rpcport=31648)."
else
  echo "   OK: porta aberta"
fi
echo ""

echo "2) JSON-RPC getinfo"
RPC_RESULT="$(rpc_call getinfo || true)"
if [[ -z "$RPC_RESULT" ]]; then
  echo "   FALHA: sem resposta"
else
  echo "   $RPC_RESULT" | head -c 400
  echo ""
  if echo "$RPC_RESULT" | grep -q '"error"'; then
    echo "   FALHA: RPC retornou erro (usuario/senha errados ou wallet bloqueada)."
    echo "   Confira rpcuser/rpcpassword em ~/.InfiniteRicks/InfiniteRicks.conf"
    echo "   e exporte RICK_RPC_USER / RICK_RPC_PASSWORD antes de run-server-services.sh"
  fi
fi
echo ""

echo "3) API local ${BIND_HOST}:${API_PORT}/api/health"
curl -sS -w "\n   HTTP %{http_code}\n" "http://${BIND_HOST}:${API_PORT}/api/health" || echo "   FALHA: API nao esta rodando"
echo ""

echo "4) Explorer local ${BIND_HOST}:${EXPLORER_PORT}/ext/health"
curl -sS -w "\n   HTTP %{http_code}\n" "http://${BIND_HOST}:${EXPLORER_PORT}/ext/health" || echo "   FALHA: explorer nao esta rodando"
echo ""

echo "Resposta esperada quando tudo OK:"
echo '  {"api":"ok","rpc":"ok"}'
echo '  {"explorer":"ok","rpc":"ok"}'
echo '  {"online":true,"chain":"main","blocks":...}'
