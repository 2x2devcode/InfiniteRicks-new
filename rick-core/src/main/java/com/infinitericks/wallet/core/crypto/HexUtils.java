package com.infinitericks.wallet.core.crypto;

public final class HexUtils {
    private HexUtils() {
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    public static byte[] fromHex(String hex) {
        if (hex == null || (hex.length() % 2) != 0) {
            throw new IllegalArgumentException("invalid hex");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int offset = i * 2;
            out[i] = (byte) Integer.parseInt(hex.substring(offset, offset + 2), 16);
        }
        return out;
    }

    public static byte[] reverse(byte[] input) {
        byte[] out = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = input[input.length - 1 - i];
        }
        return out;
    }
}
