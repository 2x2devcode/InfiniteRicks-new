# Manual de Instalação

## Requisitos

- Ubuntu 22.04+
- JDK 17+
- Android SDK 35 (somente para `:rick-android`)
- InfiniteRicks daemon (`infinitericksd`) para o servidor API

## 1. Clonar e compilar

```bash
git clone https://github.com/2x2devcode/InfiniteRicks-new.git
cd InfiniteRicks-new
./gradlew :rick-core:test :rick-api:build :rick-server:build
```

> **Nota:** `:rick-core:test` funciona sem Android SDK. O modulo `:rick-android` so entra no build quando `local.properties` ou `ANDROID_HOME` apontam para um SDK valido.

## 2. Configurar o daemon InfiniteRicks

Edite `~/.InfiniteRicks/InfiniteRicks.conf`:

```ini
server=1
rpcuser=rickrpc
rpcpassword=<senha-forte>
rpcport=31648
rpcallowip=127.0.0.1
```

Inicie o daemon e aguarde sincronização.

## 3. Subir API e explorer (mesmo servidor)

Compile:

```bash
bash scripts/build-server-services.sh
```

Inicie ambos (JSON apenas, sem interface web):

```bash
export RICK_RPC_PASSWORD=<senha-forte>
bash scripts/run-server-services.sh
```

| Serviço | Porta | Endpoints |
|---|---|---|
| API oficial | `40002` | `/api/*` |
| Explorer fallback | `40051` | `/ext/*` |

Publique atrás de TLS em `https://server.infinitericks.com:40002` e `:40051`.

## 4. Gerar APK

Configure o SDK (uma das opcoes):

```bash
# Opcao A: variavel de ambiente (local.properties e gerado automaticamente)
export ANDROID_HOME=$HOME/Android/Sdk

# Opcao B: script auxiliar
bash scripts/setup-android-sdk.sh

# Opcao C: arquivo manual
cp local.properties.example local.properties
# edite sdk.dir no arquivo
```

Depois compile:

```bash
./gradlew :rick-android:assembleRelease
```

APK: `rick-android/build/outputs/apk/release/rick-android-release.apk`

## 5. Assinatura de release

```bash
bash scripts/generate-release-keystore.sh
```

O script cria `release/infinitericks-wallet.jks` e `keystore.properties` (ambos gitignored). O Gradle lê essas propriedades automaticamente para `assembleRelease`.

Para senhas personalizadas:

```bash
STORE_PASS='sua-senha' KEY_PASS='sua-senha' bash scripts/generate-release-keystore.sh
```

## 6. Publicar explorer fallback

O endpoint `https://server.infinitericks.com:40051/ext/getsummary` deve estar acessível para fallback de rede. O explorer não possui interface web.
