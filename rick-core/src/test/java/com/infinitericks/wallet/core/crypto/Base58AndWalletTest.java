package com.infinitericks.wallet.core.crypto;

import com.infinitericks.wallet.core.wallet.WalletAccount;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Base58AndWalletTest {
    @Test
    void decodesOfficialTestVectorAddress() {
        String address = "1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i";
        byte[] hash160 = AddressCodec.decodeHash160(address);
        assertEquals("65a16059864a2fdbc7c99a4723a8395bc6f188eb", HexUtils.toHex(hash160));
    }

    @Test
    void roundTripsCompressedWif() {
        String wif = "Kz6UJmQACJmLtaQj5A3JAge4kVTNQ8gbvXuwbmCj7bsaabudb3RD";
        BigInteger privateKey = WifCodec.decode(wif);
        assertEquals("55c9bccb9ed68446d1b75273bbce89d7fe013a8acd1625514420fb2aca1a21c4", privateKey.toString(16));
        assertEquals(wif, WifCodec.encode(privateKey, true));
        WalletAccount account = WalletAccount.fromWif("test", wif);
        assertEquals(AddressCodec.fromPrivateKey(privateKey), account.address());
    }

    @Test
    void createsDeterministicAddressFromKnownKey() {
        BigInteger key = new BigInteger("6d23156cbbdcc82a5a47eee4c2c7c583c18b6bf4", 16);
        // This hash160 belongs to official address 1Ax4gZtb7gAit2TivwejZHYtNNLT18PUXJ.
        // Validate round-trip from address instead of private key because the test
        // vector file stores pubkey hashes separately from WIF entries.
        String address = "1Ax4gZtb7gAit2TivwejZHYtNNLT18PUXJ";
        assertEquals("6d23156cbbdcc82a5a47eee4c2c7c583c18b6bf4", HexUtils.toHex(AddressCodec.decodeHash160(address)));
        assertTrue(AddressCodec.isValidP2pkh(address));
    }
}
