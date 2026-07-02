package com.infinitericks.wallet.ui.home;

import android.os.Bundle;
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

    private void refresh(boolean invalidateCache) {
        if (!isAdded()) {
            return;
        }
        WalletRepository repository = ((MainActivity) requireActivity()).repository();
        repository.activeAccount().ifPresentOrElse(account -> {
            postToUi(() -> networkStatus.setText("Sincronizando rede..."));
            repository.runIo(() -> {
                RickApiClient.NetworkStatus status = null;
                try {
                    status = repository.refreshNetworkStatus();
                    RickApiClient.NetworkStatus network = status;
                    postToUi(() -> networkStatus.setText(
                            "Rede: " + network.blocks() + " blocos via " + network.source()
                                    + " | peers " + network.peers()
                    ));
                } catch (Exception e) {
                    Log.e(TAG, "network status failed", e);
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    postToUi(() -> {
                        networkStatus.setText("Rede indisponível. Verifique API e explorer.");
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                postToUi(() -> balanceValue.setText("Carregando saldo..."));
                try {
                    String balance = repository.refreshBalance(account.address(), invalidateCache);
                    postToUi(() -> balanceValue.setText(formatBalance(balance) + " " + NetworkParameters.TICKER));
                } catch (Exception e) {
                    Log.e(TAG, "balance refresh failed", e);
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    postToUi(() -> {
                        balanceValue.setText("-- " + NetworkParameters.TICKER);
                        Toast.makeText(requireContext(), "Saldo: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }, () -> balanceValue.setText("Sem conta ativa"));
    }

    private static String formatBalance(String balance) {
        return new BigDecimal(balance.trim()).setScale(2, RoundingMode.DOWN).toPlainString();
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
