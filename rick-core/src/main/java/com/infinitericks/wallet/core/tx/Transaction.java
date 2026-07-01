package com.infinitericks.wallet.core.tx;

import com.infinitericks.wallet.core.chain.NetworkParameters;
import com.infinitericks.wallet.core.crypto.CompactSize;
import com.infinitericks.wallet.core.crypto.HashUtils;
import com.infinitericks.wallet.core.crypto.HexUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Transaction {
    private final int version;
    private final long time;
    private final List<TxInput> inputs;
    private final List<TxOutput> outputs;
    private final long lockTime;

    public Transaction(int version, long time, List<TxInput> inputs, List<TxOutput> outputs, long lockTime) {
        this.version = version;
        this.time = time;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.lockTime = lockTime;
    }

    public int version() {
        return version;
    }

    public long time() {
        return time;
    }

    public List<TxInput> inputs() {
        return inputs;
    }

    public List<TxOutput> outputs() {
        return outputs;
    }

    public long lockTime() {
        return lockTime;
    }

    public String txid() {
        return HexUtils.toHex(HexUtils.reverse(HashUtils.doubleSha256(serialize())));
    }

    public byte[] serialize() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeInt32(out, version);
            writeUint32(out, time);
            CompactSize.write(out, inputs.size());
            for (TxInput input : inputs) {
                out.write(HexUtils.reverse(HexUtils.fromHex(input.outPoint().txid())));
                writeUint32(out, input.outPoint().index());
                writeVarBytes(out, input.scriptSig());
                writeUint32(out, input.sequence());
            }
            CompactSize.write(out, outputs.size());
            for (TxOutput output : outputs) {
                writeInt64(out, output.amount());
                writeVarBytes(out, output.scriptPubKey());
            }
            writeUint32(out, lockTime);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("failed to serialize transaction", e);
        }
    }

    public static Transaction copyWithInputScript(Transaction source, int inputIndex, byte[] scriptCode) {
        List<TxInput> inputs = new ArrayList<>();
        for (int i = 0; i < source.inputs.size(); i++) {
            TxInput current = source.inputs.get(i);
            byte[] script = i == inputIndex ? scriptCode : new byte[0];
            inputs.add(new TxInput(current.outPoint(), script, current.sequence()));
        }
        return new Transaction(source.version, source.time, inputs, source.outputs, source.lockTime);
    }

    public static long estimateFee(long feePerKb, int inputCount, int outputCount) {
        long bytes = 10 + 4 + (inputCount * 148L) + (outputCount * 34L);
        return Math.max((Math.max(feePerKb, NetworkParameters.DEFAULT_FEE_PER_KB) * bytes + 999) / 1000,
                NetworkParameters.MIN_TX_FEE);
    }

    private static void writeVarBytes(ByteArrayOutputStream out, byte[] data) throws IOException {
        CompactSize.write(out, data.length);
        out.write(data);
    }

    private static void writeInt32(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private static void writeUint32(ByteArrayOutputStream out, long value) throws IOException {
        out.write((int) (value & 0xFF));
        out.write((int) ((value >> 8) & 0xFF));
        out.write((int) ((value >> 16) & 0xFF));
        out.write((int) ((value >> 24) & 0xFF));
    }

    private static void writeInt64(ByteArrayOutputStream out, long value) throws IOException {
        for (int i = 0; i < 8; i++) {
            out.write((int) ((value >> (8 * i)) & 0xFF));
        }
    }
}
