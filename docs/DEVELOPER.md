# Manual do Desenvolvedor

## Alterar parâmetros da moeda

Edite `rick-core/.../chain/NetworkParameters.java`.

Todos os módulos dependem desta classe — não duplique constantes.

## Testes

```bash
./gradlew :rick-core:test
```

Nao e necessario Android SDK para os testes do `rick-core`. O modulo `:rick-android` so e incluido quando `ANDROID_HOME` ou `local.properties` apontam para um SDK valido; caso contrario, `./gradlew :rick-core:test` roda normalmente.

Testes cobrem:
- vetores Base58 do repositório oficial
- round-trip WIF comprimida
- montagem de transação assinada

## Atualizar certificate pin

Pins atuais (mar/2026):

| Host | SPKI SHA-256 (hex, em `pin_config.cpp`) | OkHttp pin (base64) |
|---|---|---|
| `server.infinitericks.com` | `9b4f332fd9687204011bc49c837eead6c0836e2bd34bb7c783a9571856bb95ff` | `m08zL9locgQBG8Scg37q1sCDbivTS7fHg6lXGFa7lf8=` |
| `serverexplorer.infinitericks.com` | `dabe0a18c44990c5cc23ae8499a3cd4d2d0829aa75be1549cab02520503af83a` | `2r4KGMRJkMXMI66EmaPNTS0IKap1vhVJyrAlIFA6+Do=` |

O JNI armazena o hash em **hex**; `CertificatePin.okHttpPinFromHex()` converte para o formato `sha256/<base64>` exigido pelo OkHttp.

1. Obtenha o SHA-256 da chave pública TLS:

```bash
echo | openssl s_client -connect server.infinitericks.com:443 -servername server.infinitericks.com 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -hex
```

2. Gere bytes XOR para `rick-android/src/main/cpp/pin_config.cpp` (`getApiPinnedHashes` / `getExplorerPinnedHashes`)
3. Recompile o APK

## Depurar o app Android (adb logcat)

Linux / macOS:

```bash
adb logcat -s RickWallet:E OkHttp:W AndroidRuntime:E
adb logcat *:E | grep -iE 'RickWallet|infinitericks|certificate|pin mismatch|SSL'
```

Windows (PowerShell / CMD):

```bat
adb logcat -s RickWallet:E OkHttp:W AndroidRuntime:E
adb logcat *:E | findstr /I "RickWallet infinitericks certificate pin SSL"
```

Erros comuns no log:

| Mensagem | Causa |
|---|---|
| `certificate pin mismatch` | Certificado TLS mudou — atualize `pin_config.cpp` |
| `HTTP 4xx/5xx` | API/explorer retornando erro |
| `Unable to resolve host` | DNS ou URL errada no APK antigo |

## Adicionar servidor de failover

Em `ApiEndpoints.OFFICIAL_BASE_URLS`, inclua URLs adicionais:

```java
public static final List<String> OFFICIAL_BASE_URLS = List.of(
    NetworkParameters.OFFICIAL_API_BASE_URL,
    "https://server2.infinitericks.com"
);
```

Explorer fallback: `NetworkParameters.EXPLORER_BASE_URL` (`serverexplorer.infinitericks.com`)

## Indexador de saldo (servidor)

A API nao usa mais `getreceivedbyaddress` / `listunspent` do daemon (esses RPCs so enxergam enderecos da carteira do no). O `rick-server` mantem um indexador on-chain em `~/.rick-wallet-index` (ou `INDEX_DIR`).

| Variavel | Padrao | Descricao |
|---|---|---|
| `INDEX_DIR` | `~/.rick-wallet-index` | Pasta do indice persistido |
| `INDEX_START_HEIGHT` | `0` | Bloco inicial da sincronizacao completa |
| `INDEX_FAST_LOOKBACK_WINDOWS` | `30,60,120` | Varredura rapida na API (segundos) |
| `INDEX_FAST_BUDGET_MS` | `6000` | Tempo maximo da consulta rapida |
| `INDEX_LOOKBACK_WINDOWS` | `200,500,1000,2000` | Varredura profunda em segundo plano |
| `INDEX_QUERY_BUDGET_MS` | `60000` | Tempo maximo da varredura profunda |
| `RPC_TIMEOUT_SECONDS` | `8` | Timeout por chamada RPC |

Apos atualizar o servidor na VPS:

```bash
bash scripts/run-server-services.sh
curl -s https://server.infinitericks.com/api/address/1AYqgJLpBzhyfejNNMJtyZ4QTcMmi8RU9g/balance
```

A primeira consulta pode levar alguns minutos enquanto o indice varre os blocos recentes; a sincronizacao completa continua em segundo plano.

## Endpoints REST esperados

### API oficial (`https://server.infinitericks.com` -> `127.0.0.1:40002`)

| Método | Path |
|---|---|
| GET | `/api/status` |
| GET | `/api/fee` |
| GET | `/api/address/{addr}/balance` |
| GET | `/api/address/{addr}/utxos` |
| GET | `/api/address/{addr}/txs` |
| POST | `/api/tx/broadcast` |
| POST | `/api/cache/invalidate/{addr}` |

### Explorer JSON (`https://serverexplorer.infinitericks.com` -> `127.0.0.1:40051`, sem interface web)

| Método | Path |
|---|---|
| GET | `/ext/getsummary` |
| GET | `/ext/getaddress/{addr}` |

## Serialização de transação

Ordem wire InfiniteRicks:
1. `nVersion` (int32)
2. `nTime` (uint32)
3. `vin[]`
4. `vout[]`
5. `nLockTime` (uint32)

O campo `nTime` é obrigatório — difere do Bitcoin Core moderno.
