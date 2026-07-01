package com.infinitericks.wallet.core.crypto;

import java.math.BigInteger;
import java.util.Arrays;

public final class Base58 {
    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private Base58() {
    }

    public static String encode(byte[] input) {
        BigInteger value = new BigInteger(1, input);
        StringBuilder encoded = new StringBuilder();
        while (value.signum() > 0) {
            BigInteger[] div = value.divideAndRemainder(BigInteger.valueOf(58));
            encoded.append(ALPHABET.charAt(div[1].intValue()));
            value = div[0];
        }
        for (byte b : input) {
            if (b != 0) {
                break;
            }
            encoded.append('1');
        }
        return encoded.reverse().toString();
    }

    public static byte[] decode(String input) {
        BigInteger value = BigInteger.ZERO;
        for (int i = 0; i < input.length(); i++) {
            int index = ALPHABET.indexOf(input.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("invalid base58 character");
            }
            value = value.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(index));
        }
        byte[] bytes = value.toByteArray();
        if (bytes.length > 0 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        int leadingZeros = 0;
        while (leadingZeros < input.length() && input.charAt(leadingZeros) == '1') {
            leadingZeros++;
        }
        byte[] decoded = new byte[leadingZeros + bytes.length];
        System.arraycopy(bytes, 0, decoded, leadingZeros, bytes.length);
        return decoded;
    }

    public static String encodeCheck(byte version, byte[] payload) {
        byte[] body = new byte[1 + payload.length];
        body[0] = version;
        System.arraycopy(payload, 0, body, 1, payload.length);
        byte[] checksum = Arrays.copyOf(HashUtils.doubleSha256(body), 4);
        byte[] full = new byte[body.length + 4];
        System.arraycopy(body, 0, full, 0, body.length);
        System.arraycopy(checksum, 0, full, body.length, 4);
        return encode(full);
    }

    public static Decoded decodeCheck(String input) {
        byte[] decoded = decode(input);
        if (decoded.length < 5) {
            throw new IllegalArgumentException("invalid base58check payload");
        }
        byte[] body = Arrays.copyOf(decoded, decoded.length - 4);
        byte[] checksum = Arrays.copyOfRange(decoded, decoded.length - 4, decoded.length);
        byte[] expected = Arrays.copyOf(HashUtils.doubleSha256(body), 4);
        if (!Arrays.equals(checksum, expected)) {
            throw new IllegalArgumentException("invalid base58 checksum");
        }
        int version = body[0] & 0xFF;
        byte[] payload = Arrays.copyOfRange(body, 1, body.length);
        return new Decoded(version, payload);
    }

    public record Decoded(int version, byte[] payload) {
    }
}
