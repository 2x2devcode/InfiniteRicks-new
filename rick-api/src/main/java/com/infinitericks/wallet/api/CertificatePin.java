package com.infinitericks.wallet.api;

import java.security.MessageDigest;
import java.util.Locale;

final class CertificatePin {
    private CertificatePin() {
    }

    static String sha256Hex(byte[] encodedPublicKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(encodedPublicKey);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.US, "%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
