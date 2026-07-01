package com.infinitericks.wallet.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class BiometricVault {
    private static final String PREFS = "rick_biometric_vault";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_IV = "iv";
    private static final String KEY_PAYLOAD = "payload";
    private static final String KEY_ALIAS = "rick_biometric_unlock_v1";

    private final SharedPreferences prefs;

    public BiometricVault(Context context) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        prefs = EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    public boolean isAvailable(Context context) {
        int result = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void enable(String walletPassword) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] ciphertext = cipher.doFinal(walletPassword.getBytes(StandardCharsets.UTF_8));
        prefs.edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(KEY_PAYLOAD, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .apply();
    }

    public void disable() {
        prefs.edit().clear().apply();
    }

    public void authenticate(FragmentActivity activity, Callback callback) {
        if (!isEnabled()) {
            callback.onError("Biometria não configurada.");
            return;
        }
        Executor executor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt prompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                try {
                    Cipher cipher = result.getCryptoObject().getCipher();
                    callback.onSuccess(decryptWithCipher(cipher));
                } catch (Exception e) {
                    callback.onError(e.getMessage());
                }
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                callback.onError(errString.toString());
            }

            @Override
            public void onAuthenticationFailed() {
                callback.onError("Digital não reconhecida.");
            }
        });
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = Base64.decode(prefs.getString(KEY_IV, ""), Base64.NO_WRAP);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            prompt.authenticate(
                    new BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Desbloquear InfiniteRicks")
                            .setSubtitle("Use sua digital cadastrada no aparelho")
                            .setNegativeButtonText("Cancelar")
                            .build(),
                    new BiometricPrompt.CryptoObject(cipher)
            );
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    private String decryptWithCipher(Cipher cipher) throws Exception {
        byte[] ciphertext = Base64.decode(prefs.getString(KEY_PAYLOAD, ""), Base64.NO_WRAP);
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
            } else {
                builder.setUserAuthenticationValidityDurationSeconds(30);
            }
            generator.init(builder.build());
            generator.generateKey();
        }
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    public interface Callback {
        void onSuccess(String walletPassword);

        void onError(String message);
    }
}
