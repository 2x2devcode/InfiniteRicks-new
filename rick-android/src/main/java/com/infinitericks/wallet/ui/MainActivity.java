package com.infinitericks.wallet.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
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
import com.infinitericks.wallet.ui.home.HomeFragment;
import com.infinitericks.wallet.ui.receive.ReceiveFragment;
import com.infinitericks.wallet.ui.send.SendFragment;
import com.infinitericks.wallet.ui.settings.SettingsFragment;
import com.infinitericks.wallet.ui.wallets.WalletsFragment;

public final class MainActivity extends AppCompatActivity {
    private WalletRepository repository;
    private View authContainer;
    private View walletContainer;
    private EditText passwordInput;
    private TextView authTitle;
    private Button authActionButton;
    private Button authSecondaryButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        repository = ((RickWalletApp) getApplication()).walletRepository();
        authContainer = findViewById(R.id.authContainer);
        walletContainer = findViewById(R.id.walletContainer);
        passwordInput = findViewById(R.id.passwordInput);
        authTitle = findViewById(R.id.authTitle);
        authActionButton = findViewById(R.id.authActionButton);
        authSecondaryButton = findViewById(R.id.authSecondaryButton);

        if (repository.hasWallet()) {
            showUnlock();
        } else {
            showCreate();
        }

        authActionButton.setOnClickListener(v -> onAuthPrimary());
        authSecondaryButton.setOnClickListener(v -> onAuthSecondary());

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

    public WalletRepository repository() {
        return repository;
    }

    private void onAuthPrimary() {
        String password = passwordInput.getText().toString();
        if (TextUtils.isEmpty(password) || password.length() < 8) {
            toast("Use uma senha com pelo menos 8 caracteres.");
            return;
        }
        if (repository.hasWallet()) {
            unlock(password);
        } else {
            create(password);
        }
    }

    private void onAuthSecondary() {
        if (repository.hasWallet()) {
            showCreate();
        } else {
            showUnlock();
        }
    }

    private void create(String password) {
        repository.runIo(() -> {
            try {
                repository.createWallet(password);
                runOnUiThread(this::enterWallet);
            } catch (Exception e) {
                runOnUiThread(() -> toast("Falha ao criar carteira: " + e.getMessage()));
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
                runOnUiThread(this::enterWallet);
            } catch (Exception e) {
                runOnUiThread(() -> toast("Falha ao desbloquear: " + e.getMessage()));
            }
        });
    }

    private void enterWallet() {
        authContainer.setVisibility(View.GONE);
        walletContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new HomeFragment())
                .commit();
    }

    private void showCreate() {
        authTitle.setText("Criar carteira InfiniteRicks");
        authActionButton.setText("Criar carteira");
        authSecondaryButton.setText("Já tenho carteira");
    }

    private void showUnlock() {
        authTitle.setText("Desbloquear carteira");
        authActionButton.setText("Entrar");
        authSecondaryButton.setText("Criar nova carteira");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
