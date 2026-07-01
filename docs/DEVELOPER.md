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

1. Obtenha o SHA-256 da chave pública TLS:

```bash
echo | openssl s_client -connect server.infinitericks.com:443 -servername server.infinitericks.com 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -hex
```

2. Gere bytes XOR para `rick-android/src/main/cpp/pin_config.cpp`
3. Recompile o APK

## Adicionar servidor de failover

Em `ApiEndpoints.OFFICIAL_BASE_URLS`, inclua URLs adicionais:

```java
public static final List<String> OFFICIAL_BASE_URLS = List.of(
    "https://server.infinitericks.com",
    "https://server2.infinitericks.com"
);
```

## Endpoints REST esperados

| Método | Path |
|---|---|
| GET | `/api/status` |
| GET | `/api/fee` |
| GET | `/api/address/{addr}/balance` |
| GET | `/api/address/{addr}/utxos` |
| GET | `/api/address/{addr}/txs` |
| POST | `/api/tx/broadcast` |
| POST | `/api/cache/invalidate/{addr}` |

## Serialização de transação

Ordem wire InfiniteRicks:
1. `nVersion` (int32)
2. `nTime` (uint32)
3. `vin[]`
4. `vout[]`
5. `nLockTime` (uint32)

O campo `nTime` é obrigatório — difere do Bitcoin Core moderno.
