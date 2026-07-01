package com.infinitericks.wallet.ui.settings;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.infinitericks.wallet.R;
import com.infinitericks.wallet.core.chain.NetworkParameters;
import com.infinitericks.wallet.data.WalletRepository;
import com.infinitericks.wallet.security.BiometricVault;
import com.infinitericks.wallet.ui.MainActivity;

public final class SettingsFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView settingsInfo = view.findViewById(R.id.settingsInfo);
        Button enableBiometricButton = view.findViewById(R.id.enableBiometricButton);
        Button disableBiometricButton = view.findViewById(R.id.disableBiometricButton);
        MainActivity activity = (MainActivity) requireActivity();
        BiometricVault biometricVault = activity.biometricVault();
        WalletRepository repository = activity.repository();

        settingsInfo.setText(
                "InfiniteRicks Wallet 1.1.0\n\n" +
                        "API: " + NetworkParameters.OFFICIAL_API_BASE_URL + "\n" +
                        "Explorer: " + NetworkParameters.EXPLORER_BASE_URL + "\n\n" +
                        "Carteira não-custodial.\n" +
                        "Chaves privadas permanecem no dispositivo.\n" +
                        "TLS com certificate pinning.\n" +
                        "Sem tráfego HTTP em texto claro.\n" +
                        "Sem masternode.\n\n" +
                        "Desenvolvedor: InfiniteRicksCoin, BR"
        );

        enableBiometricButton.setOnClickListener(v -> {
            if (!biometricVault.isAvailable(requireContext())) {
                Toast.makeText(requireContext(), "Biometria indisponível neste aparelho.", Toast.LENGTH_LONG).show();
                return;
            }
            EditText passwordInput = new EditText(requireContext());
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordInput.setHint("Senha da carteira");
            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(48, 16, 48, 0);
            container.addView(passwordInput);
            new AlertDialog.Builder(requireContext())
                    .setTitle("Ativar biometria")
                    .setMessage("Informe sua senha para proteger o desbloqueio biométrico.")
                    .setView(container)
                    .setPositiveButton("Ativar", (dialog, which) -> repository.runIo(() -> {
                        String password = passwordInput.getText().toString();
                        try {
                            if (!repository.verifyPassword(password)) {
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(), "Senha incorreta.", Toast.LENGTH_LONG).show());
                                return;
                            }
                            biometricVault.enable(password);
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(), "Biometria ativada.", Toast.LENGTH_LONG).show());
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }))
                    .setNegativeButton("Cancelar", null)
                    .show();
        });
        disableBiometricButton.setOnClickListener(v -> {
            biometricVault.disable();
            Toast.makeText(requireContext(), "Biometria desativada.", Toast.LENGTH_SHORT).show();
        });
    }
}
