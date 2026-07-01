package com.infinitericks.wallet.core.wallet;

import com.infinitericks.wallet.core.crypto.AddressCodec;
import com.infinitericks.wallet.core.crypto.Secp256k1;
import com.infinitericks.wallet.core.crypto.WifCodec;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class WalletAccount {
    private final String id;
    private final String label;
    private final String address;
    private final BigInteger privateKey;
    private final String wif;
    private final long createdAt;

    private WalletAccount(String id, String label, String address, BigInteger privateKey, String wif, long createdAt) {
        this.id = id;
        this.label = label;
        this.address = address;
        this.privateKey = privateKey;
        this.wif = wif;
        this.createdAt = createdAt;
    }

    public static WalletAccount createNew(String label) {
        BigInteger privateKey = Secp256k1.generatePrivateKey();
        return fromPrivateKey(label, privateKey);
    }

    public static WalletAccount fromWif(String label, String wif) {
        return fromPrivateKey(label, WifCodec.decode(wif));
    }

    public static WalletAccount fromPrivateKey(String label, BigInteger privateKey) {
        String address = AddressCodec.fromPrivateKey(privateKey);
        return new WalletAccount(
                UUID.randomUUID().toString(),
                label == null ? "" : label.trim(),
                address,
                privateKey,
                WifCodec.encode(privateKey, true),
                Instant.now().getEpochSecond()
        );
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String address() {
        return address;
    }

    public BigInteger privateKey() {
        return privateKey;
    }

    public String wif() {
        return wif;
    }

    public long createdAt() {
        return createdAt;
    }

    public WalletAccount withLabel(String newLabel) {
        return new WalletAccount(id, newLabel, address, privateKey, wif, createdAt);
    }

    public static WalletAccount restored(
            String id,
            String label,
            String address,
            BigInteger privateKey,
            String wif,
            long createdAt
    ) {
        return new WalletAccount(id, label, address, privateKey, wif, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WalletAccount that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
