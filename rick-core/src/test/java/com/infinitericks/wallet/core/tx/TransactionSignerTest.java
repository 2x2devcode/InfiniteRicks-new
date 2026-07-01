package com.infinitericks.wallet.core.tx;

import com.infinitericks.wallet.core.wallet.Amount;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TransactionSignerTest {
    @Test
    void parsesAmounts() {
        assertEquals(100_000_000L, Amount.toSatoshis("1"));
        assertEquals(10_000L, Amount.toSatoshis("0.0001"));
        assertEquals("1.00000000", Amount.fromSatoshis(100_000_000L));
    }

    @Test
    void buildsSignedTransactionStructure() {
        BigInteger key = new BigInteger("55c9bccb9ed68446d1b75273bbce89d7fe013a8acd1625514420fb2aca1a21c4", 16);
        String address = "1Ax4gZtb7gAit2TivwejZHYtNNLT18PUXJ";
        TransactionSigner.SpendableUtxo utxo = new TransactionSigner.SpendableUtxo(
                "c36d963aeb2301532d6a7777aa656e86cd2195a41b1bb0ce687b939b50b2a552",
                0,
                200_000_000L,
                key,
                address
        );
        TransactionSigner.SignedTransaction signed = TransactionSigner.buildAndSign(
                List.of(utxo),
                address,
                address,
                100_000_000L,
                10_000L
        );
        assertEquals(64, signed.txid().length());
        assertFalse(signed.rawHex().isEmpty());
    }
}
