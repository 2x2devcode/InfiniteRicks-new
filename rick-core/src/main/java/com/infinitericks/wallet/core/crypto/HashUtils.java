package com.infinitericks.wallet.core.crypto;

import org.bouncycastle.crypto.digests.RIPEMD160Digest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtils {
    private HashUtils() {
    }

    public static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static byte[] doubleSha256(byte[] input) {
        return sha256(sha256(input));
    }

    public static byte[] ripemd160(byte[] input) {
        RIPEMD160Digest digest = new RIPEMD160Digest();
        digest.update(input, 0, input.length);
        byte[] output = new byte[20];
        digest.doFinal(output, 0);
        return output;
    }

    public static byte[] hash160(byte[] input) {
        return ripemd160(sha256(input));
    }

    public static byte[] hashMessage(String magic, String message) {
        byte[] magicBytes = encodeVarString(magic);
        byte[] messageBytes = encodeVarString(message);
        byte[] payload = new byte[magicBytes.length + messageBytes.length];
        System.arraycopy(magicBytes, 0, payload, 0, magicBytes.length);
        System.arraycopy(messageBytes, 0, payload, magicBytes.length, messageBytes.length);
        return doubleSha256(payload);
    }

    private static byte[] encodeVarString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        byte[] length = CompactSize.encode(bytes.length);
        byte[] out = new byte[length.length + bytes.length];
        System.arraycopy(length, 0, out, 0, length.length);
        System.arraycopy(bytes, 0, out, length.length, bytes.length);
        return out;
    }
}
