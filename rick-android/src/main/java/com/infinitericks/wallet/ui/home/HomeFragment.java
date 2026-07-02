package com.infinitericks.wallet.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.infinitericks.wallet.R;
import com.infinitericks.wallet.api.RickApiClient;
import com.infinitericks.wallet.core.chain.NetworkParameters;
import com.infinitericks.wallet.data.WalletRepository;
import com.infinitericks.wallet.ui.MainActivity;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class HomeFragment extends Fragment {
    private static final String TAG = "RickWallet";
    private static final long BALANCE_TIMEOUT_SECONDS = 20L;
    private static final long BALANCE_POLL_MS = 8_000L;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = () -> refresh(false);

    private TextView balanceValue;
    private TextView networkStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        balanceValue = view.findViewById(R.id.balanceValue);
        networkStatus = view.findViewById(R.id.networkStatus);
        Button refreshButton = view.findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(v -> refresh(true));
        refresh(false);
    }

    @Override
    public void onDestroyView() {
        pollHandler.removeCallbacks(pollRunnable);
        super.onDestroyView();
    }

    private void refresh(boolean invalidateCache) {
        if (!isAdded()) {
            return;
        }
        pollHandler.removeCallbacks(pollRunnable);
        WalletRepository repository = ((MainActivity) requireActivity()).repository();
        repository.activeAccount().ifPresentOrElse(account -> {
            postToUi(() -> {
                networkStatus.setText("Sincronizando rede...");
                balanceValue.setText("Carregando saldo...");
            });
            repository.runIo(() -> {
                try {
                    RickApiClient.NetworkStatus network = repository.refreshNetworkStatus();
                    postToUi(() -> networkStatus.setText(
                            "Rede: " + network.blocks() + " blocos via " + network.source()
                                    + " | peers " + network.peers()
                    ));
                } catch (Exception e) {
                    Log.e(TAG, "network status failed", e);
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    postToUi(() -> {
                        networkStatus.setText("Rede indisponível. Verifique API e explorer.");
                        balanceValue.setText("-- " + NetworkParameters.TICKER);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                try {
                    RickApiClient.BalanceResponse balance = repository.refreshBalanceResponse(
                            account.address(),
                            invalidateCache
                    );
                    boolean shouldPoll = balance.scanning() || isZeroBalance(balance.balance());
                    postToUi(() -> {
                        balanceValue.setText(formatBalance(balance.balance()) + " " + NetworkParameters.TICKER);
                        if (balance.scanning()) {
                            networkStatus.setText("Indexando saldo na rede...");
                        }
                        if (shouldPoll) {
                            pollHandler.postDelayed(pollRunnable, BALANCE_POLL_MS);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "balance refresh failed", e);
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    postToUi(() -> {
                        balanceValue.setText("-- " + NetworkParameters.TICKER);
                        Toast.makeText(requireContext(), "Saldo: " + message, Toast.LENGTH_LONG).show();
                        pollHandler.postDelayed(pollRunnable, BALANCE_POLL_MS);
                    });
                }
            });
        }, () -> balanceValue.setText("Sem conta ativa"));
    }

    private static boolean isZeroBalance(String balance) {
        try {
            return new BigDecimal(balance.trim()).compareTo(BigDecimal.ZERO) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String formatBalance(String balance) {
        if (balance == null || balance.isBlank()) {
            return "0.00";
        }
        try {
            return new BigDecimal(balance.trim()).setScale(2, RoundingMode.DOWN).toPlainString();
        } catch (NumberFormatException e) {
            return balance.trim();
        }
    }

    private void postToUi(Runnable action) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            if (isAdded()) {
                action.run();
            }
        });
    }
}
