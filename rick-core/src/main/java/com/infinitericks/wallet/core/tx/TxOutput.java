package com.infinitericks.wallet.core.tx;

public final class TxOutput {
    private final long amount;
    private final byte[] scriptPubKey;

    public TxOutput(long amount, byte[] scriptPubKey) {
        this.amount = amount;
        this.scriptPubKey = scriptPubKey.clone();
    }

    public long amount() {
        return amount;
    }

    public byte[] scriptPubKey() {
        return scriptPubKey.clone();
    }
}
