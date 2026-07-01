# Arquitetura — InfiniteRicks Wallet

## Visão geral

```mermaid
flowchart TB
    subgraph Android
        UI[rick-android UI]
        Repo[WalletRepository]
        Pin[NativePinProvider JNI]
    end

    subgraph Libraries
        API[rick-api]
        Core[rick-core]
    end

    subgraph Remote
        Server["server.infinitericks.com:40002"]
        Explorer["server.infinitericks.com:40051"]
    end

  UI --> Repo
  Repo --> Core
  Repo --> API
  API --> Pin
  API --> Server
  API --> Explorer
  Server --> Daemon[InfiniteRicks JSON-RPC]
```

## Módulos

### `rick-core`
Biblioteca Java pura com:
- `NetworkParameters` — parâmetros alteráveis da moeda
- `crypto` — Base58, secp256k1, endereços, WIF
- `tx` — serialização Peercoin (`nTime`), assinatura, fee
- `wallet` — contas com label, backup criptografado

### `rick-api`
Cliente HTTP com:
- OkHttp + pinning customizado
- Retry exponencial
- Failover preparado para múltiplos `baseUrls`
- Sem chave de API no APK

### `rick-server`
Dois processos JSON (sem interface web) no mesmo servidor:

| Classe | Porta | Rotas |
|---|---|---|
| `RickServer` | 40002 | `/api/status`, `/api/address/...`, `/api/tx/broadcast` |
| `RickExplorerServer` | 40051 | `/ext/getsummary`, `/ext/getaddress/{addr}` |

Scripts: `scripts/build-server-services.sh`, `scripts/run-server-services.sh`

### `rick-android`
- Activity única + fragments
- Bottom navigation: Início, Enviar, Receber, Carteiras, Config
- Senha local + armazenamento criptografado
- Pin da chave pública TLS oculto em `rickpin` (JNI + XOR)

## Fluxos

### Criação da carteira
1. Usuário define senha (mín. 8 caracteres)
2. PBKDF2 gera hash de autenticação local
3. `WalletStore` gera conta `Principal`
4. JSON da carteira é criptografado com AES-GCM
5. Blob salvo em `EncryptedSharedPreferences`

### Envio
1. Busca UTXOs na API oficial
2. Seleciona inputs confirmados
3. Assina localmente com `TransactionSigner`
4. Envia `rawTx` via `POST /api/tx/broadcast`

### Sincronização
1. `GET /api/status` a cada refresh manual
2. `GET /api/address/{addr}/balance`
3. Fallback opcional para explorer em falha da API

## Estrutura de pastas

```
rick-core/src/main/java/com/infinitericks/wallet/core/
  chain/
  crypto/
  tx/
  wallet/
rick-api/src/main/java/com/infinitericks/wallet/api/
rick-server/src/main/java/com/infinitericks/wallet/server/
rick-android/src/main/java/com/infinitericks/wallet/
  data/
  security/
  ui/
docs/
```

## Correções em relação ao modelo Lunarium

| Problema Lunarium | Solução RICK |
|---|---|
| Monólito de 6.700 linhas | Módulos Gradle separados |
| API key XOR no APK | Sem API key no cliente |
| `usesCleartextTraffic=true` | Desabilitado |
| Pin em Java estático | JNI `rickpin` |
| Masternode / iHostMN | Removido |
| Senha fraca (6 chars) | Mínimo 8 caracteres |
| PBKDF2 120k | PBKDF2 210k + AES-GCM |
