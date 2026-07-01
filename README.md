# InfiniteRicks Wallet

Carteira Android não-custodial para a criptomoeda **InfiniteRicks (RICK)**.

## Módulos

| Módulo | Descrição |
|---|---|
| `rick-core` | Parâmetros da rede, criptografia, transações e armazenamento da carteira |
| `rick-api` | Cliente HTTP com TLS, retry, failover e certificate pinning |
| `rick-server` | API REST JSON (`:40002`) e explorer JSON (`:40051`) para `server.infinitericks.com` |
| `rick-android` | Aplicativo Android 15+ |

## Parâmetros da rede (mainnet)

Fonte: [InfiniteRicks oficial](https://github.com/2x2devcode/InfiniteRicks)

- P2PKH version: `0x00`
- WIF version: `0x80` (comprimido)
- `COIN = 100_000_000`
- `MIN_TX_FEE = 10_000` satoshis
- Transações incluem campo `nTime` (extensão Peercoin)
- Assinatura: secp256k1 + DER + `SIGHASH_ALL`
- Mensagem: `InfiniteRicks Signed Message:\n`

## Recursos (v1.1.0)

- Desbloqueio biométrico (impressão digital / face) com Android Keystore
- QR code para receber e escanear endereços ao enviar
- Restauração de carteira via WIF na aba Receber
- Fallback automático para explorer JSON na porta `40051` quando a API (`40002`) falha
- Bloqueio automático da sessão ao sair do app

## Segurança

- Chaves privadas somente no dispositivo
- Carteira criptografada com AES-GCM + PBKDF2 (210k iterações)
- `EncryptedSharedPreferences` no Android
- Certificate pinning via JNI (`rickpin`)
- Sem `usesCleartextTraffic`
- Sem API key embutida no cliente
- Sem masternode / iHostMN

## Build rápido

Testes e servidor **nao exigem** Android SDK:

```bash
./gradlew :rick-core:test
./gradlew :rick-server:build
bash scripts/build-server-services.sh
bash scripts/run-server-services.sh
```

APK Android (exige SDK API 35):

```bash
export ANDROID_HOME=$HOME/Android/Sdk   # ou bash scripts/setup-android-sdk.sh
bash scripts/generate-release-keystore.sh   # primeira vez
./gradlew :rick-android:assembleRelease
```

Se o SDK nao estiver configurado, o modulo `:rick-android` e omitido automaticamente e os comandos acima (exceto `assembleRelease`) continuam funcionando.

## Documentação

- [Arquitetura](docs/ARCHITECTURE.md)
- [Instalação](docs/INSTALLATION.md)
- [Desenvolvedor](docs/DEVELOPER.md)
- [Usuário](docs/USER_MANUAL.md)

## Certificado do app

```
CN=InfiniteRicks Wallet
OU=InfiniteRicks
O=InfiniteRicksCoin
C=BR
RSA 2048
```

## Lacunas conhecidas no repositório da moeda

- Não há REST API nativa (somente JSON-RPC)
- Não há URL oficial de bootstrap no código
- API (`server.infinitericks.com:40002`) e explorer (`:40051`) devem estar publicados em produção
- Ambos os serviços são somente JSON — sem interface web
