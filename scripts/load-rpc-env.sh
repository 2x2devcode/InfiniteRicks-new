#!/usr/bin/env bash
# Carrega credenciais RPC de ~/.InfiniteRicks/InfiniteRicks.conf.
# O arquivo do daemon tem prioridade sobre variaveis de ambiente antigas.

CONF="${INFINITE_RICKS_CONF:-${HOME}/.InfiniteRicks/InfiniteRicks.conf}"

read_conf_value() {
  local key=$1
  if [[ ! -f "$CONF" ]]; then
    return 1
  fi
  grep -E "^[[:space:]]*${key}=" "$CONF" | tail -1 | sed -E "s/^[[:space:]]*${key}=//" | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
}

export RICK_RPC_HOST="${RICK_RPC_HOST:-127.0.0.1}"

if [[ -f "$CONF" ]]; then
  if port="$(read_conf_value rpcport)"; then
    export RICK_RPC_PORT="$port"
  else
    export RICK_RPC_PORT="${RICK_RPC_PORT:-31648}"
  fi
  if user="$(read_conf_value rpcuser)"; then
    export RICK_RPC_USER="$user"
  fi
  if pass="$(read_conf_value rpcpassword)"; then
    export RICK_RPC_PASSWORD="$pass"
  fi
else
  export RICK_RPC_PORT="${RICK_RPC_PORT:-31648}"
fi

# Override manual apenas se solicitado explicitamente
if [[ "${RICK_RPC_FORCE_ENV:-}" == "1" ]]; then
  :
fi
