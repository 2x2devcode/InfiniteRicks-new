package com.infinitericks.wallet.core.tx;

public final class TxInput {
    private final OutPoint outPoint;
    private final byte[] scriptSig;
    private final long sequence;

    public TxInput(OutPoint outPoint, byte[] scriptSig, long sequence) {
        this.outPoint = outPoint;
        this.scriptSig = scriptSig == null ? new byte[0] : scriptSig.clone();
        this.sequence = sequence;
    }

    public OutPoint outPoint() {
        return outPoint;
    }

    public byte[] scriptSig() {
        return scriptSig.clone();
    }

    public long sequence() {
        return sequence;
    }
}
