#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=load-rpc-env.sh
source "$ROOT_DIR/scripts/load-rpc-env.sh"

BIND_HOST="${BIND_HOST:-127.0.0.1}"
API_PORT="${API_PORT:-40002}"
EXPLORER_PORT="${EXPLORER_PORT:-40051}"
LOG_DIR="${LOG_DIR:-${ROOT_DIR}/logs}"
PID_DIR="${PID_DIR:-${ROOT_DIR}/.run}"

stop_port() {
  local port=$1
  local label=$2
  if command -v fuser >/dev/null 2>&1; then
    if fuser -n tcp "${port}" >/dev/null 2>&1; then
      echo "Encerrando ${label} na porta ${port}..."
      fuser -k -n tcp "${port}" >/dev/null 2>&1 || true
      sleep 1
    fi
    return
  fi
  if command -v lsof >/dev/null 2>&1; then
    local pids
    pids="$(lsof -ti tcp:"${port}" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "${pids}" ]]; then
      echo "Encerrando ${label} na porta ${port} (PID ${pids})..."
      kill ${pids} 2>/dev/null || true
      sleep 1
    fi
  fi
}

stop_pid_file() {
  local pid_file=$1
  local label=$2
  if [[ -f "${pid_file}" ]]; then
    local pid
    pid="$(cat "${pid_file}")"
    if kill -0 "${pid}" 2>/dev/null; then
      echo "Encerrando ${label} (PID ${pid})..."
      kill "${pid}" 2>/dev/null || true
      sleep 1
    fi
    rm -f "${pid_file}"
  fi
}

if [[ -z "${RICK_RPC_USER:-}" || -z "${RICK_RPC_PASSWORD:-}" ]]; then
  echo "ERRO: credenciais RPC nao encontradas em ~/.InfiniteRicks/InfiniteRicks.conf"
  exit 1
fi

echo "=== Reiniciando servicos InfiniteRicks ==="
echo ""

stop_pid_file "${PID_DIR}/rick-api.pid" "API"
stop_pid_file "${PID_DIR}/rick-explorer.pid" "Explorer"
stop_port "${API_PORT}" "API"
stop_port "${EXPLORER_PORT}" "Explorer"

echo "Compilando API e explorer..."
"$ROOT_DIR/scripts/build-server-services.sh"

LIB_DIR="${ROOT_DIR}/rick-server/build/install/rick-server/lib"
mkdir -p "${LOG_DIR}" "${PID_DIR}"

export BIND_HOST RICK_RPC_HOST RICK_RPC_PORT RICK_RPC_USER RICK_RPC_PASSWORD

echo "Iniciando API em ${BIND_HOST}:${API_PORT}..."
nohup env PORT="${API_PORT}" java -cp "${LIB_DIR}/*" com.infinitericks.wallet.server.RickServer \
  >"${LOG_DIR}/rick-api.log" 2>&1 &
echo $! > "${PID_DIR}/rick-api.pid"

echo "Iniciando explorer em ${BIND_HOST}:${EXPLORER_PORT}..."
nohup env EXPLORER_PORT="${EXPLORER_PORT}" java -cp "${LIB_DIR}/*" com.infinitericks.wallet.server.RickExplorerServer \
  >"${LOG_DIR}/rick-explorer.log" 2>&1 &
echo $! > "${PID_DIR}/rick-explorer.pid"

sleep 2

echo ""
echo "Testando saude local..."
API_BODY="$(curl -sS "http://${BIND_HOST}:${API_PORT}/api/health" 2>/dev/null || true)"
EXPLORER_BODY="$(curl -sS "http://${BIND_HOST}:${EXPLORER_PORT}/ext/health" 2>/dev/null || true)"

if echo "${API_BODY}" | grep -q '"rpc":"ok"'; then
  echo "  API:      OK"
  BALANCE_START="$(date +%s%3N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1000))')"
  BALANCE_BODY="$(curl -sS --max-time 5 "http://${BIND_HOST}:${API_PORT}/api/address/1AYqgJLpBzhyfejNNMJtyZ4QTcMmi8RU9g/balance" 2>/dev/null || true)"
  BALANCE_END="$(date +%s%3N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1000))')"
  if echo "${BALANCE_BODY}" | grep -q '"address"'; then
    ELAPSED=$((BALANCE_END - BALANCE_START))
    echo "  Balance:  OK (${ELAPSED}ms, local bypass nginx)"
    echo "            ${BALANCE_BODY}"
  else
    echo "  Balance:  FALHA (deve responder em <5s apos este deploy)"
    echo "            ${BALANCE_BODY}"
  fi
else
  echo "  API:      FALHA"
  echo "            ${API_BODY}"
  echo "  Log:      ${LOG_DIR}/rick-api.log"
fi

if echo "${EXPLORER_BODY}" | grep -q '"rpc":"ok"'; then
  echo "  Explorer: OK"
else
  echo "  Explorer: FALHA"
  echo "            ${EXPLORER_BODY}"
  echo "  Log:      ${LOG_DIR}/rick-explorer.log"
fi

echo ""
echo "PIDs: API=$(cat "${PID_DIR}/rick-api.pid"), Explorer=$(cat "${PID_DIR}/rick-explorer.pid")"
echo "Para diagnostico completo: bash scripts/diagnose-server.sh"
