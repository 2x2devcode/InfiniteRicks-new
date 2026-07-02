package com.infinitericks.wallet.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.infinitericks.wallet.api.RickApiClient;
import com.infinitericks.wallet.core.crypto.AddressCodec;
import com.infinitericks.wallet.core.tx.TransactionSigner;
import com.infinitericks.wallet.core.wallet.Amount;
import com.infinitericks.wallet.core.wallet.WalletAccount;
import com.infinitericks.wallet.core.wallet.WalletStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WalletRepository {
    private static final String PREFS = "rick_wallet_secure";
    private static final String KEY_WALLET = "wallet_encrypted";
    private static final String KEY_AUTH_HASH = "auth_hash";
    private static final String KEY_AUTH_SALT = "auth_salt";

    private final SharedPreferences securePrefs;
    private final RickApiClient apiClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WalletStore walletStore = new WalletStore();
    private char[] sessionPassword;

    public WalletRepository(Context context, RickApiClient apiClient) {
        this.apiClient = apiClient;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            this.securePrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            throw new IllegalStateException("failed to initialize encrypted preferences", e);
        }
    }

    public boolean hasWallet() {
        return securePrefs.contains(KEY_WALLET);
    }

    public boolean verifyPassword(String password) {
        String salt = securePrefs.getString(KEY_AUTH_SALT, "");
        String expected = securePrefs.getString(KEY_AUTH_HASH, "");
        return PasswordHasher.verify(password, salt, expected);
    }

    public void createWallet(String password) throws Exception {
        WalletStore store = new WalletStore();
        store.addAccount(WalletAccount.createNew("Principal"));
        persist(password, store);
    }

    public void importWif(String password, String label, String wif) throws Exception {
        WalletStore store = walletStore.accounts().isEmpty() ? new WalletStore() : walletStore;
        store.addAccount(WalletAccount.fromWif(label, wif));
        persist(password, store);
    }

    public void importWifToSession(String label, String wif) {
        if (sessionPassword == null) {
            throw new IllegalStateException("wallet locked");
        }
        walletStore.addAccount(WalletAccount.fromWif(label, wif));
        saveWalletSnapshot();
    }

    public List<WalletAccount> accounts() {
        return walletStore.accounts();
    }

    public Optional<WalletAccount> activeAccount() {
        return walletStore.activeAccount();
    }

    public void setActiveAccount(String accountId) {
        walletStore.setActiveAccount(accountId);
        saveWalletSnapshot();
    }

    public void addAccount(String label) {
        walletStore.addAccount(WalletAccount.createNew(label));
        saveWalletSnapshot();
    }

    public void renameAccount(String accountId, String label) {
        walletStore.renameAccount(accountId, label);
        saveWalletSnapshot();
    }

    public String exportActiveWif() {
        return walletStore.activeAccount().map(WalletAccount::wif).orElse("");
    }

    public void runIo(Runnable task) {
        executor.execute(task);
    }

    public RickApiClient api() {
        return apiClient;
    }

    public String refreshBalance(String address, boolean invalidateCache) throws Exception {
        if (invalidateCache) {
            try {
                apiClient.invalidateBalanceCache(address);
            } catch (IOException ignored) {
                // Server may not require explicit invalidation.
            }
        }
        return apiClient.getBalance(address);
    }

    public String refreshBalance(String address) throws Exception {
        return refreshBalance(address, false);
    }

    public RickApiClient.NetworkStatus refreshNetworkStatus() throws Exception {
        return apiClient.getStatus();
    }

    public String send(String destination, String amountText) throws Exception {
        WalletAccount active = walletStore.activeAccount()
                .orElseThrow(() -> new IllegalStateException("no active account"));
        long amount = Amount.toSatoshis(amountText);
        long feePerKb = apiClient.getFeePerKb();
        List<RickApiClient.Utxo> utxos = apiClient.getUtxos(active.address());
        List<TransactionSigner.SpendableUtxo> spendable = new ArrayList<>();
        for (RickApiClient.Utxo utxo : utxos) {
            if (utxo.confirmations() >= 1) {
                spendable.add(new TransactionSigner.SpendableUtxo(
                        utxo.txid(),
                        utxo.vout(),
                        utxo.amountSatoshis(),
                        active.privateKey(),
                        active.address()
                ));
            }
        }
        if (!AddressCodec.isValidP2pkh(destination)) {
            throw new IllegalArgumentException("invalid destination address");
        }
        TransactionSigner.SignedTransaction signed = TransactionSigner.buildAndSign(
                spendable,
                destination,
                active.address(),
                amount,
                feePerKb
        );
        return apiClient.broadcast(signed.rawHex());
    }

    public void lock() {
        if (sessionPassword != null) {
            Arrays.fill(sessionPassword, '\0');
            sessionPassword = null;
        }
        walletStore = new WalletStore();
    }

    public void unlock(String password) throws Exception {
        String encrypted = securePrefs.getString(KEY_WALLET, null);
        if (encrypted == null) {
            throw new IllegalStateException("wallet not initialized");
        }
        walletStore = WalletStore.importEncrypted(password, encrypted);
        sessionPassword = password.toCharArray();
    }

    private void persist(String password, WalletStore store) throws Exception {
        PasswordHasher.HashData hashData = PasswordHasher.hash(password);
        securePrefs.edit()
                .putString(KEY_AUTH_SALT, hashData.salt())
                .putString(KEY_AUTH_HASH, hashData.hash())
                .putString(KEY_WALLET, store.exportEncrypted(password))
                .apply();
        walletStore = store;
        sessionPassword = password.toCharArray();
    }

    private void saveWalletSnapshot() {
        if (sessionPassword == null) {
            return;
        }
        try {
            securePrefs.edit()
                    .putString(KEY_WALLET, walletStore.exportEncrypted(new String(sessionPassword)))
                    .apply();
        } catch (Exception ignored) {
            // Caller can surface persistence errors when needed.
        }
    }
}
