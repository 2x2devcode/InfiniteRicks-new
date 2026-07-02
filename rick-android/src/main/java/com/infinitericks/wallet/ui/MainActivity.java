package com.infinitericks.wallet.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.infinitericks.wallet.R;
import com.infinitericks.wallet.RickWalletApp;
import com.infinitericks.wallet.data.WalletRepository;
import com.infinitericks.wallet.security.BiometricVault;
import com.infinitericks.wallet.ui.home.HomeFragment;
import com.infinitericks.wallet.ui.receive.ReceiveFragment;
import com.infinitericks.wallet.ui.send.SendFragment;
import com.infinitericks.wallet.ui.settings.SettingsFragment;
import com.infinitericks.wallet.ui.wallets.WalletsFragment;

public final class MainActivity extends AppCompatActivity {
    private static final String TAG = "RickWallet";
    private static final int MIN_PIN_LENGTH = 6;
    private static final int MAX_PIN_LENGTH = 10;

    private enum AuthMode {
        CREATE,
        UNLOCK,
        RESTORE
    }

    private WalletRepository repository;
    private BiometricVault biometricVault;
    private View authContainer;
    private View walletContainer;
    private EditText passwordInput;
    private EditText wifRestoreInput;
    private TextView authTitle;
    private Button authActionButton;
    private Button authSecondaryButton;
    private Button authRestoreButton;
    private Button authBiometricButton;
    private AuthMode authMode = AuthMode.UNLOCK;
    private boolean unlocked;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RickWalletApp app = (RickWalletApp) getApplication();
        repository = app.walletRepository();
        biometricVault = app.biometricVault();
        authContainer = findViewById(R.id.authContainer);
        walletContainer = findViewById(R.id.walletContainer);
        passwordInput = findViewById(R.id.passwordInput);
        wifRestoreInput = findViewById(R.id.wifRestoreInput);
        authTitle = findViewById(R.id.authTitle);
        authActionButton = findViewById(R.id.authActionButton);
        authSecondaryButton = findViewById(R.id.authSecondaryButton);
        authRestoreButton = findViewById(R.id.authRestoreButton);
        authBiometricButton = findViewById(R.id.authBiometricButton);

        if (repository.hasWallet()) {
            showUnlock();
        } else {
            showCreate();
        }

        authActionButton.setOnClickListener(v -> onAuthPrimary());
        authSecondaryButton.setOnClickListener(v -> onAuthSecondary());
        authRestoreButton.setOnClickListener(v -> showRestore());
        authBiometricButton.setOnClickListener(v -> unlockWithBiometric());

        BottomNavigationView navigation = findViewById(R.id.bottomNavigation);
        navigation.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();
            if (id == R.id.nav_send) {
                fragment = new SendFragment();
            } else if (id == R.id.nav_receive) {
                fragment = new ReceiveFragment();
            } else if (id == R.id.nav_wallets) {
                fragment = new WalletsFragment();
            } else if (id == R.id.nav_settings) {
                fragment = new SettingsFragment();
            } else {
                fragment = new HomeFragment();
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
            return true;
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (unlocked) {
            repository.lock();
            unlocked = false;
            walletContainer.setVisibility(View.GONE);
            authContainer.setVisibility(View.VISIBLE);
            passwordInput.setText("");
            wifRestoreInput.setText("");
            showUnlock();
        }
    }

    public WalletRepository repository() {
        return repository;
    }

    public BiometricVault biometricVault() {
        return biometricVault;
    }

    private void onAuthPrimary() {
        String password = passwordInput.getText().toString();
        if (!isValidPin(password)) {
            toast("Use uma senha numérica de 6 a 10 dígitos.");
            return;
        }
        if (authMode == AuthMode.CREATE) {
            create(password);
        } else if (authMode == AuthMode.RESTORE) {
            restore(password);
        } else {
            unlock(password);
        }
    }

    private static boolean isValidPin(String password) {
        if (password.length() < MIN_PIN_LENGTH || password.length() > MAX_PIN_LENGTH) {
            return false;
        }
        for (int i = 0; i < password.length(); i++) {
            if (!Character.isDigit(password.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void onAuthSecondary() {
        if (authMode == AuthMode.UNLOCK) {
            showCreate();
        } else if (authMode == AuthMode.CREATE && repository.hasWallet()) {
            showUnlock();
        } else if (authMode == AuthMode.RESTORE) {
            showUnlock();
        } else {
            showCreate();
        }
    }

    private void unlockWithBiometric() {
        if (!biometricVault.isEnabled()) {
            toast("Ative a biometria em Config após o primeiro desbloqueio.");
            return;
        }
        biometricVault.authenticate(this, new BiometricVault.Callback() {
            @Override
            public void onSuccess(String walletPassword) {
                unlock(walletPassword);
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void create(String password) {
        repository.runIo(() -> {
            try {
                repository.createWallet(password);
                postToUi(this::enterWallet);
            } catch (Exception e) {
                Log.e(TAG, "create wallet failed", e);
                postToUi(() -> toast("Falha ao criar carteira: " + rootMessage(e)));
            }
        });
    }

    private void restore(String password) {
        String wif = wifRestoreInput.getText().toString().trim();
        if (wif.isEmpty()) {
            toast("Informe a WIF da carteira.");
            return;
        }
        repository.runIo(() -> {
            try {
                repository.restoreWalletFromWif(password, wif);
                postToUi(() -> {
                    wifRestoreInput.setText(wif);
                    enterWallet();
                });
            } catch (Exception e) {
                Log.e(TAG, "restore wallet failed", e);
                postToUi(() -> toast("Falha ao restaurar: " + rootMessage(e)));
            }
        });
    }

    private void unlock(String password) {
        if (!repository.verifyPassword(password)) {
            toast("Senha incorreta.");
            return;
        }
        repository.runIo(() -> {
            try {
                repository.unlock(password);
                postToUi(this::enterWallet);
            } catch (Exception e) {
                Log.e(TAG, "unlock wallet failed", e);
                postToUi(() -> toast("Falha ao desbloquear: " + rootMessage(e)));
            }
        });
    }

    private void enterWallet() {
        hideKeyboard();
        unlocked = true;
        authContainer.setVisibility(View.GONE);
        walletContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new HomeFragment())
                .commit();
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) {
            focus = passwordInput;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        passwordInput.clearFocus();
        wifRestoreInput.clearFocus();
    }

    private void showCreate() {
        authMode = AuthMode.CREATE;
        authTitle.setText("Criar carteira InfiniteRicks");
        authActionButton.setText("Criar carteira");
        authSecondaryButton.setText(repository.hasWallet() ? "Já tenho carteira" : "Voltar");
        authRestoreButton.setVisibility(View.VISIBLE);
        authBiometricButton.setVisibility(View.GONE);
        wifRestoreInput.setVisibility(View.GONE);
        passwordInput.setText("");
        wifRestoreInput.setText("");
    }

    private void showUnlock() {
        authMode = AuthMode.UNLOCK;
        authTitle.setText("Desbloquear carteira");
        authActionButton.setText("Entrar");
        authSecondaryButton.setText("Criar nova carteira");
        authRestoreButton.setVisibility(View.VISIBLE);
        authBiometricButton.setVisibility(
                biometricVault.isEnabled() && biometricVault.isAvailable(this) ? View.VISIBLE : View.GONE
        );
        wifRestoreInput.setVisibility(View.GONE);
        passwordInput.setText("");
        wifRestoreInput.setText("");
    }

    private void showRestore() {
        authMode = AuthMode.RESTORE;
        authTitle.setText("Restaurar carteira");
        authActionButton.setText("Restaurar carteira");
        authSecondaryButton.setText("Voltar ao desbloqueio");
        authRestoreButton.setVisibility(View.GONE);
        authBiometricButton.setVisibility(View.GONE);
        wifRestoreInput.setVisibility(View.VISIBLE);
        passwordInput.setText("");
    }

    private void postToUi(Runnable action) {
        if (isFinishing()) {
            return;
        }
        runOnUiThread(() -> {
            if (!isFinishing()) {
                action.run();
            }
        });
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
