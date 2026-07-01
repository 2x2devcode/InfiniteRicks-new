#!/usr/bin/env bash
# Carrega RICK_RPC_USER / RICK_RPC_PASSWORD de ~/.InfiniteRicks/InfiniteRicks.conf
# quando as variaveis de ambiente nao estiverem definidas.

CONF="${INFINITE_RICKS_CONF:-${HOME}/.InfiniteRicks/InfiniteRicks.conf}"

read_conf_value() {
  local key=$1
  if [[ ! -f "$CONF" ]]; then
    return 1
  fi
  grep -E "^[[:space:]]*${key}=" "$CONF" | tail -1 | sed -E "s/^[[:space:]]*${key}=//" | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
}

if [[ -z "${RICK_RPC_HOST:-}" ]]; then
  export RICK_RPC_HOST="127.0.0.1"
fi

if [[ -z "${RICK_RPC_PORT:-}" ]]; then
  if port="$(read_conf_value rpcport)"; then
    export RICK_RPC_PORT="$port"
  else
    export RICK_RPC_PORT="31648"
  fi
fi

if [[ -z "${RICK_RPC_USER:-}" ]]; then
  if user="$(read_conf_value rpcuser)"; then
    export RICK_RPC_USER="$user"
  fi
fi

if [[ -z "${RICK_RPC_PASSWORD:-}" ]]; then
  if pass="$(read_conf_value rpcpassword)"; then
    export RICK_RPC_PASSWORD="$pass"
  fi
fi
