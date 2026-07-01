package com.infinitericks.wallet.core.wallet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WalletStore {
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final List<WalletAccount> accounts;
    private String activeAccountId;

    public WalletStore() {
        this.accounts = new ArrayList<>();
    }

    public WalletStore(List<WalletAccount> accounts, String activeAccountId) {
        this.accounts = new ArrayList<>(accounts);
        this.activeAccountId = activeAccountId;
    }

    public List<WalletAccount> accounts() {
        return Collections.unmodifiableList(accounts);
    }

    public Optional<WalletAccount> activeAccount() {
        return accounts.stream().filter(a -> a.id().equals(activeAccountId)).findFirst();
    }

    public void addAccount(WalletAccount account) {
        accounts.add(account);
        activeAccountId = account.id();
    }

    public void setActiveAccount(String accountId) {
        if (accounts.stream().noneMatch(a -> a.id().equals(accountId))) {
            throw new IllegalArgumentException("unknown account");
        }
        activeAccountId = accountId;
    }

    public void renameAccount(String accountId, String label) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).id().equals(accountId)) {
                accounts.set(i, accounts.get(i).withLabel(label));
                return;
            }
        }
        throw new IllegalArgumentException("unknown account");
    }

    public void removeAccount(String accountId) {
        accounts.removeIf(a -> a.id().equals(accountId));
        if (activeAccountId != null && activeAccountId.equals(accountId)) {
            activeAccountId = accounts.isEmpty() ? null : accounts.get(0).id();
        }
    }

    public String exportEncrypted(String password) throws Exception {
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] iv = randomBytes(IV_BYTES);
        SecretKeySpec key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plaintext = GSON.toJson(toPayload()).getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = cipher.doFinal(plaintext);
        EncryptedEnvelope envelope = new EncryptedEnvelope();
        envelope.version = 1;
        envelope.salt = Base64.getEncoder().encodeToString(salt);
        envelope.iv = Base64.getEncoder().encodeToString(iv);
        envelope.data = Base64.getEncoder().encodeToString(ciphertext);
        return GSON.toJson(envelope);
    }

    public static WalletStore importEncrypted(String password, String encryptedJson) throws Exception {
        EncryptedEnvelope envelope = GSON.fromJson(encryptedJson, EncryptedEnvelope.class);
        if (envelope.version != 1) {
            throw new IllegalArgumentException("unsupported wallet version");
        }
        byte[] salt = Base64.getDecoder().decode(envelope.salt);
        byte[] iv = Base64.getDecoder().decode(envelope.iv);
        byte[] ciphertext = Base64.getDecoder().decode(envelope.data);
        SecretKeySpec key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);
        WalletPayload payload = GSON.fromJson(new String(plaintext, StandardCharsets.UTF_8), WalletPayload.class);
        List<WalletAccount> accounts = new ArrayList<>();
        for (AccountRecord record : payload.accounts) {
            accounts.add(reconstructAccount(record));
        }
        return new WalletStore(accounts, payload.activeAccountId);
    }

    private static WalletAccount reconstructAccount(AccountRecord record) {
        WalletAccount account = WalletAccount.fromPrivateKey(record.label, new java.math.BigInteger(record.privateKeyHex, 16));
        return WalletAccount.restored(
                record.id == null || record.id.isBlank() ? account.id() : record.id,
                record.label == null ? account.label() : record.label,
                record.address == null || record.address.isBlank() ? account.address() : record.address,
                account.privateKey(),
                record.wif == null || record.wif.isBlank() ? account.wif() : record.wif,
                record.createdAt > 0 ? record.createdAt : account.createdAt()
        );
    }

    private WalletPayload toPayload() {
        List<AccountRecord> records = new ArrayList<>();
        for (WalletAccount account : accounts) {
            AccountRecord record = new AccountRecord();
            record.id = account.id();
            record.label = account.label();
            record.address = account.address();
            record.privateKeyHex = account.privateKey().toString(16);
            record.wif = account.wif();
            record.createdAt = account.createdAt();
            records.add(record);
        }
        WalletPayload payload = new WalletPayload();
        payload.version = 1;
        payload.activeAccountId = activeAccountId;
        payload.accounts = records;
        return payload;
    }

    private static SecretKeySpec deriveKey(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static final class EncryptedEnvelope {
        int version;
        String salt;
        String iv;
        String data;
    }

    private static final class WalletPayload {
        int version;
        String activeAccountId;
        List<AccountRecord> accounts;
    }

    private static final class AccountRecord {
        String id;
        String label;
        String address;
        @SerializedName("privateKeyHex")
        String privateKeyHex;
        String wif;
        long createdAt;
    }
}
