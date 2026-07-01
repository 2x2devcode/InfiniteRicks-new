package com.infinitericks.wallet.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static HashData hash(String password) throws Exception {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String hash = derive(password, salt);
        return new HashData(Base64.getEncoder().encodeToString(salt), hash);
    }

    public static boolean verify(String password, String saltBase64, String expectedHash) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            return derive(password, salt).equals(expectedHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static String derive(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = factory.generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(key));
    }

    public record HashData(String salt, String hash) {
    }
}
