package com.infinitericks.wallet.ui.home;

import android.os.Bundle;
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

public final class HomeFragment extends Fragment {
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
        refreshButton.setOnClickListener(v -> refresh());
        refresh();
    }

    private void refresh() {
        WalletRepository repository = ((MainActivity) requireActivity()).repository();
        repository.activeAccount().ifPresentOrElse(account -> repository.runIo(() -> {
            try {
                String balance = repository.refreshBalance(account.address());
                RickApiClient.NetworkStatus status = repository.refreshNetworkStatus();
                requireActivity().runOnUiThread(() -> {
                    balanceValue.setText(balance + " " + NetworkParameters.TICKER);
                    networkStatus.setText(
                            "Rede: " + status.blocks() + " blocos via " + status.source()
                                    + " | peers " + status.peers()
                    );
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    networkStatus.setText("Rede indisponível. Verifique API e explorer.");
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }), () -> balanceValue.setText("Sem conta ativa"));
    }
}
