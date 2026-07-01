package com.infinitericks.wallet.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.infinitericks.wallet.R;
import com.infinitericks.wallet.core.chain.NetworkParameters;

public final class SettingsFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView settingsInfo = view.findViewById(R.id.settingsInfo);
        settingsInfo.setText(
                "InfiniteRicks Wallet 1.0.0\n\n" +
                        "API: " + NetworkParameters.OFFICIAL_API_BASE_URL + "\n" +
                        "Explorer: " + NetworkParameters.EXPLORER_BASE_URL + "\n\n" +
                        "Carteira não-custodial.\n" +
                        "Chaves privadas permanecem no dispositivo.\n" +
                        "TLS com certificate pinning.\n" +
                        "Sem tráfego HTTP em texto claro.\n" +
                        "Sem masternode.\n\n" +
                        "Desenvolvedor: InfiniteRicksCoin, BR"
        );
    }
}
