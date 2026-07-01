package com.infinitericks.wallet.api;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

final class CertificatePin {
    private CertificatePin() {
    }

    static String sha256Hex(byte[] encodedPublicKey) {
        return bytesToHex(sha256(encodedPublicKey));
    }

    /** OkHttp CertificatePinner expects {@code sha256/<base64>} of the SPKI hash. */
    static String okHttpPinFromHex(String hexPin) {
        return "sha256/" + Base64.getEncoder().encodeToString(hexToBytes(hexPin));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String bytesToHex(byte[] digest) {
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format(Locale.US, "%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("invalid hex pin length");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int offset = i * 2;
            out[i] = (byte) Integer.parseInt(hex.substring(offset, offset + 2), 16);
        }
        return out;
    }
}
