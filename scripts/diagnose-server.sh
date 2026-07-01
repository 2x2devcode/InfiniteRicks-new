#!/usr/bin/env bash
set -euo pipefail

# shellcheck source=load-rpc-env.sh
source "$(cd "$(dirname "$0")" && pwd)/load-rpc-env.sh"

API_PORT="${API_PORT:-40002}"
EXPLORER_PORT="${EXPLORER_PORT:-40051}"
BIND_HOST="${BIND_HOST:-127.0.0.1}"
CONF="${INFINITE_RICKS_CONF:-${HOME}/.InfiniteRicks/InfiniteRicks.conf}"

read_conf_user() {
  grep -E '^[[:space:]]*rpcuser=' "$CONF" 2>/dev/null | tail -1 | sed -E 's/^[[:space:]]*rpcuser=//' | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
}

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
  CONF_USER="$(read_conf_user)"
  echo "   rpcuser no conf: ${CONF_USER}"
  if grep -qE '^[[:space:]]*rpcpassword=' "$CONF"; then
    echo "   rpcpassword no conf: (definido)"
  else
    echo "   rpcpassword no conf: NAO DEFINIDO"
  fi
else
  echo "   FALHA: arquivo nao encontrado"
  CONF_USER=""
fi
echo "   Credencial em uso: user=${RICK_RPC_USER:-?} host=${RICK_RPC_HOST}:${RICK_RPC_PORT}"
if [[ -n "${CONF_USER}" && "${RICK_RPC_USER:-}" != "${CONF_USER}" ]]; then
  echo "   AVISO: usuario em uso difere do conf (remova export RICK_RPC_USER antigo do shell)"
fi
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
  echo "   FALHA: credenciais RPC ausentes no conf"
else
  RPC_RESULT="$(rpc_call getinfo 2>&1 || true)"
  if echo "$RPC_RESULT" | grep -qi '401 Unauthorized'; then
    echo "   FALHA: 401 Unauthorized"
    echo "   Confirme que rpcuser no conf comeca com o valor exato (ex: userZDNNJMGs27t6Mq2)"
    echo "   Reinicie o daemon apos editar o conf:"
    echo "     infinitericksd stop ; sleep 2 ; infinitericksd -daemon"
  elif echo "$RPC_RESULT" | grep -q '"result"'; then
    echo "   OK: $(echo "$RPC_RESULT" | head -c 300)"
  elif echo "$RPC_RESULT" | grep -q '"error"'; then
    echo "   FALHA: $RPC_RESULT"
  else
    echo "   Resposta: $RPC_RESULT"
  fi
fi
echo ""

check_api() {
  local label=$1
  local url=$2
  echo "$label"
  local body
  body="$(curl -sS "$url" 2>/dev/null || true)"
  if echo "$body" | grep -q 'object mapper configured'; then
    echo "   FALHA: build antigo sem Jackson — execute:"
    echo "     git pull origin main && bash scripts/build-server-services.sh"
    echo "     bash scripts/run-server-services.sh"
  elif echo "$body" | grep -q '"rpc":"ok"'; then
    echo "   OK: $body"
  elif echo "$body" | grep -q '"error"'; then
    echo "   RPC indisponivel (esperado ate passo 2 OK): $body"
  else
    echo "   $body"
  fi
  echo ""
}

check_api "3) API local ${BIND_HOST}:${API_PORT}/api/health" "http://${BIND_HOST}:${API_PORT}/api/health"
check_api "4) Explorer local ${BIND_HOST}:${EXPLORER_PORT}/ext/health" "http://${BIND_HOST}:${EXPLORER_PORT}/ext/health"
