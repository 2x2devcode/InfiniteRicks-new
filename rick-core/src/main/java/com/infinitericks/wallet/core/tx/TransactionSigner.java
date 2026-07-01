package com.infinitericks.wallet.core.tx;

import com.infinitericks.wallet.core.chain.NetworkParameters;
import com.infinitericks.wallet.core.crypto.AddressCodec;
import com.infinitericks.wallet.core.crypto.HashUtils;
import com.infinitericks.wallet.core.crypto.HexUtils;
import com.infinitericks.wallet.core.crypto.Secp256k1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class TransactionSigner {
    private static final int SIGHASH_ALL = 1;

    private TransactionSigner() {
    }

    public static byte[] signatureHash(Transaction transaction, int inputIndex, byte[] scriptCode) {
        Transaction copy = Transaction.copyWithInputScript(transaction, inputIndex, scriptCode);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(copy.serialize());
            out.write(SIGHASH_ALL);
            out.write(0);
            out.write(0);
            out.write(0);
            return HashUtils.doubleSha256(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("failed to hash transaction", e);
        }
    }

    public static String sign(Transaction unsignedTx, List<SigningInput> signingInputs) {
        List<TxInput> signedInputs = new ArrayList<>();
        for (int i = 0; i < unsignedTx.inputs().size(); i++) {
            SigningInput signingInput = signingInputs.get(i);
            byte[] scriptCode = AddressCodec.p2pkhScript(AddressCodec.decodeHash160(signingInput.fromAddress()));
            byte[] hash = signatureHash(unsignedTx, i, scriptCode);
            byte[] der = Secp256k1.signDer(signingInput.privateKey(), hash);
            byte[] scriptSig = buildScriptSig(der, Secp256k1.compressedPublicKey(signingInput.privateKey()));
            TxInput original = unsignedTx.inputs().get(i);
            signedInputs.add(new TxInput(original.outPoint(), scriptSig, original.sequence()));
        }
        Transaction signed = new Transaction(
                unsignedTx.version(),
                unsignedTx.time(),
                signedInputs,
                unsignedTx.outputs(),
                unsignedTx.lockTime()
        );
        return HexUtils.toHex(signed.serialize());
    }

    public static Transaction buildUnsigned(
            List<SpendableUtxo> utxos,
            String destinationAddress,
            String changeAddress,
            long sendAmount,
            long feePerKb
    ) {
        if (sendAmount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        long totalInput = 0;
        List<TxInput> inputs = new ArrayList<>();
        List<SigningInput> signing = new ArrayList<>();
        for (SpendableUtxo utxo : utxos) {
            totalInput += utxo.amount();
            inputs.add(new TxInput(new OutPoint(utxo.txid(), utxo.vout()), new byte[0], 0xFFFF_FFFFL));
            signing.add(new SigningInput(utxo.privateKey(), utxo.address()));
            long fee = Transaction.estimateFee(feePerKb, inputs.size(), 2);
            if (totalInput >= sendAmount + fee) {
                break;
            }
        }
        long fee = Transaction.estimateFee(feePerKb, inputs.size(), 2);
        if (totalInput < sendAmount + fee) {
            throw new IllegalArgumentException("insufficient balance");
        }
        long change = totalInput - sendAmount - fee;
        List<TxOutput> outputs = new ArrayList<>();
        outputs.add(new TxOutput(sendAmount, AddressCodec.p2pkhScript(AddressCodec.decodeHash160(destinationAddress))));
        if (change > 0) {
            outputs.add(new TxOutput(change, AddressCodec.p2pkhScript(AddressCodec.decodeHash160(changeAddress))));
        }
        Transaction unsigned = new Transaction(
                NetworkParameters.TX_VERSION,
                Instant.now().getEpochSecond(),
                inputs,
                outputs,
                0
        );
        return unsigned;
    }

    public static SignedTransaction buildAndSign(
            List<SpendableUtxo> utxos,
            String destinationAddress,
            String changeAddress,
            long sendAmount,
            long feePerKb
    ) {
        Transaction unsigned = buildUnsigned(utxos, destinationAddress, changeAddress, sendAmount, feePerKb);
        List<SigningInput> signingInputs = new ArrayList<>();
        for (SpendableUtxo utxo : utxos.subList(0, unsigned.inputs().size())) {
            signingInputs.add(new SigningInput(utxo.privateKey(), utxo.address()));
        }
        String rawHex = sign(unsigned, signingInputs);
        return new SignedTransaction(unsigned.txid(), rawHex);
    }

    private static byte[] buildScriptSig(byte[] derSignature, byte[] publicKey) {
        byte[] sigWithHashType = new byte[derSignature.length + 1];
        System.arraycopy(derSignature, 0, sigWithHashType, 0, derSignature.length);
        sigWithHashType[derSignature.length] = (byte) SIGHASH_ALL;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeVarBytes(out, sigWithHashType);
            writeVarBytes(out, publicKey);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("failed to build scriptSig", e);
        }
    }

    private static void writeVarBytes(ByteArrayOutputStream out, byte[] data) throws IOException {
        if (data.length >= 0xFD) {
            throw new IllegalArgumentException("varint too large for this wallet implementation");
        }
        out.write(data.length);
        out.write(data);
    }

    public record SigningInput(BigInteger privateKey, String fromAddress) {
    }

    public record SpendableUtxo(String txid, int vout, long amount, BigInteger privateKey, String address) {
    }

    public record SignedTransaction(String txid, String rawHex) {
    }
}
