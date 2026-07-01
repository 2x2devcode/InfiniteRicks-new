package com.infinitericks.wallet.core.crypto;

import com.infinitericks.wallet.core.chain.NetworkParameters;

import java.math.BigInteger;
import java.util.Arrays;

public final class AddressCodec {
    private AddressCodec() {
    }

    public static String fromPublicKey(byte[] compressedPublicKey) {
        byte[] hash160 = HashUtils.hash160(compressedPublicKey);
        return Base58.encodeCheck((byte) NetworkParameters.PUBKEY_ADDRESS_VERSION, hash160);
    }

    public static byte[] decodeHash160(String address) {
        Base58.Decoded decoded = Base58.decodeCheck(address);
        if ((decoded.version() & 0xFF) != NetworkParameters.PUBKEY_ADDRESS_VERSION) {
            throw new IllegalArgumentException("invalid InfiniteRicks P2PKH address");
        }
        if (decoded.payload().length != 20) {
            throw new IllegalArgumentException("invalid address hash length");
        }
        return decoded.payload();
    }

    public static boolean isValidP2pkh(String address) {
        try {
            decodeHash160(address);
            return NetworkParameters.isMainnetP2pkhAddress(address);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static byte[] p2pkhScript(byte[] hash160) {
        byte[] script = new byte[25];
        script[0] = (byte) 0x76;
        script[1] = (byte) 0xA9;
        script[2] = 0x14;
        System.arraycopy(hash160, 0, script, 3, 20);
        script[23] = (byte) 0x88;
        script[24] = (byte) 0xAC;
        return script;
    }

    public static String fromPrivateKey(BigInteger privateKey) {
        return fromPublicKey(Secp256k1.compressedPublicKey(privateKey));
    }
}
