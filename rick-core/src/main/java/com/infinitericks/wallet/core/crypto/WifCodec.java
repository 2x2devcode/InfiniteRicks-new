package com.infinitericks.wallet.core.crypto;

import com.infinitericks.wallet.core.chain.NetworkParameters;

import java.math.BigInteger;
import java.util.Arrays;

public final class WifCodec {
    private WifCodec() {
    }

    public static String encode(BigInteger privateKey, boolean compressed) {
        byte[] secret = toFixed(privateKey, 32);
        byte[] payload = compressed ? new byte[33] : new byte[32];
        System.arraycopy(secret, 0, payload, 0, 32);
        if (compressed) {
            payload[32] = NetworkParameters.WIF_COMPRESSED_SUFFIX;
        }
        return Base58.encodeCheck((byte) NetworkParameters.WIF_VERSION, payload);
    }

    public static BigInteger decode(String wif) {
        Base58.Decoded decoded = Base58.decodeCheck(wif.trim());
        if ((decoded.version() & 0xFF) != NetworkParameters.WIF_VERSION) {
            throw new IllegalArgumentException("invalid InfiniteRicks WIF version");
        }
        byte[] payload = decoded.payload();
        boolean compressed = payload.length == 33 && payload[32] == NetworkParameters.WIF_COMPRESSED_SUFFIX;
        if (payload.length != 32 && !compressed) {
            throw new IllegalArgumentException("invalid WIF payload length");
        }
        BigInteger privateKey = new BigInteger(1, Arrays.copyOf(payload, 32));
        if (privateKey.signum() <= 0) {
            throw new IllegalArgumentException("invalid private key");
        }
        return privateKey;
    }

    private static byte[] toFixed(BigInteger value, int size) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > size) {
            bytes = Arrays.copyOfRange(bytes, bytes.length - size, bytes.length);
        }
        byte[] out = new byte[size];
        System.arraycopy(bytes, 0, out, size - bytes.length, bytes.length);
        return out;
    }
}
