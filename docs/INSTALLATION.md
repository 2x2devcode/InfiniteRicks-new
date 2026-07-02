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

Inicie o daemon e aguarde sincronização. **Após alterar `rpcuser`/`rpcpassword`, reinicie o daemon:**

```bash
infinitericksd stop
sleep 2
infinitericksd -daemon
```

Os scripts `run-server-services.sh` e `diagnose-server.sh` leem automaticamente `rpcuser` e `rpcpassword` desse arquivo.

## 3. Subir API e explorer (mesmo servidor)

Compile:

```bash
bash scripts/build-server-services.sh
```

Inicie ambos (JSON apenas, sem interface web):

```bash
# Atualizar codigo, recompilar e reiniciar em segundo plano (recomendado na VPS)
git pull origin main
bash scripts/restart-server-services.sh
```

Para teste interativo (encerra ao pressionar Ctrl+C):

```bash
bash scripts/run-server-services.sh
```

| Serviço | URL pública | Bind local (VPS) | Endpoints |
|---|---|---|---|
| API oficial | `https://server.infinitericks.com` | `127.0.0.1:40002` | `/api/*` |
| Explorer fallback | `https://serverexplorer.infinitericks.com` | `127.0.0.1:40051` | `/ext/*` |

Exemplo nginx (API):

```nginx
server {
    listen 443 ssl;
    server_name server.infinitericks.com;
    location / {
        proxy_pass http://127.0.0.1:40002;
    }
}
```

Exemplo nginx (explorer):

```nginx
server {
    listen 443 ssl;
    server_name serverexplorer.infinitericks.com;
    location / {
        proxy_pass http://127.0.0.1:40051;
    }
}
```

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

O endpoint `https://serverexplorer.infinitericks.com/ext/getsummary` deve estar acessível para fallback de rede. O explorer não possui interface web.

## 7. Diagnostico de erros

Se `curl` publico retornar `Server Error` ou JSON `{"error":"..."}`:

```bash
bash scripts/diagnose-server.sh
bash scripts/run-server-services.sh
```

`run-server-services.sh` carrega credenciais de `~/.InfiniteRicks/InfiniteRicks.conf` automaticamente.

Causas comuns:

| Sintoma | Causa |
|---|---|
| `RPC HTTP 401` | `RICK_RPC_USER` / `RICK_RPC_PASSWORD` diferentes do daemon |
| `Connection refused` na porta 31648 | `infinitericksd` nao esta rodando ou `server=1` ausente |
| `502` com mensagem RPC | API/explorer rodando, mas RPC inacessivel |
| `Server Error` (texto puro) | Versao antiga sem tratamento JSON — atualize com `git pull` |

Teste local antes do HTTPS:

```bash
curl -s http://127.0.0.1:40002/api/health
curl -s http://127.0.0.1:40051/ext/health
```

