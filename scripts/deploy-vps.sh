#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== Deploy InfiniteRicks API na VPS ==="
echo ""

if [[ ! -d .git ]]; then
  echo "ERRO: execute este script na raiz do clone git (InfiniteRicks-new)."
  exit 1
fi

echo "1) Atualizando codigo..."
git fetch origin main
git pull origin main
echo "   Commit: $(git rev-parse --short HEAD) $(git log -1 --pretty=%s)"
echo ""

echo "2) Reiniciando API e explorer..."
bash scripts/restart-server-services.sh
echo ""

echo "3) Teste local (ignora nginx) — deve ser JSON em <3s:"
TEST_ADDRESS="1AYqgJLpBzhyfejNNMJtyZ4QTcMmi8RU9g"
BIND_HOST="${BIND_HOST:-127.0.0.1}"
API_PORT="${API_PORT:-40002}"

START="$(date +%s%3N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1000))')"
BODY="$(curl -sS --max-time 3 "http://${BIND_HOST}:${API_PORT}/api/address/${TEST_ADDRESS}/balance" 2>/dev/null || true)"
END="$(date +%s%3N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1000))')"
ELAPSED=$((END - START))

if echo "$BODY" | grep -q '"address"'; then
  echo "   OK (${ELAPSED}ms): $BODY"
  if echo "$BODY" | grep -q '"balance":"0'; then
    echo "   AVISO: saldo zero — aguarde enrich do explorer ou verifique EXPLORER_FALLBACK_ENABLED"
  fi
else
  echo "   FALHA (${ELAPSED}ms): $BODY"
  echo "   Log: ${LOG_DIR:-${ROOT_DIR}/logs}/rick-api.log"
  tail -30 "${LOG_DIR:-${ROOT_DIR}/logs}/rick-api.log" 2>/dev/null || true
  exit 1
fi

echo ""
echo "4) Se o passo 3 passou, teste via nginx:"
echo "   time curl -s https://server.infinitericks.com/api/address/${TEST_ADDRESS}/balance"
echo ""
echo "   Nginx atual (30s timeout) e suficiente — a API deve responder em <2s."
echo "   504 apos 30s = API ainda nao atualizada ou processo errado na porta 40002."
