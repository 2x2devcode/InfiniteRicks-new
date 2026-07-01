package com.infinitericks.wallet.ui.receive;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.infinitericks.wallet.R;
import com.infinitericks.wallet.data.WalletRepository;
import com.infinitericks.wallet.ui.MainActivity;
import com.infinitericks.wallet.ui.QrHelper;

public final class ReceiveFragment extends Fragment {
    private TextView addressValue;
    private ImageView qrImage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_receive, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        addressValue = view.findViewById(R.id.addressValue);
        qrImage = view.findViewById(R.id.qrImage);
        Button copyAddressButton = view.findViewById(R.id.copyAddressButton);
        EditText restoreWifInput = view.findViewById(R.id.restoreWifInput);
        Button restoreWifButton = view.findViewById(R.id.restoreWifButton);
        WalletRepository repository = ((MainActivity) requireActivity()).repository();

        repository.activeAccount().ifPresent(account -> {
            addressValue.setText(account.address());
            qrImage.setImageBitmap(QrHelper.createQr(QrHelper.paymentUri(account.address()), 520));
        });

        copyAddressButton.setOnClickListener(v -> repository.activeAccount().ifPresent(account -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("rick-address", account.address()));
            Toast.makeText(requireContext(), "Endereço copiado", Toast.LENGTH_SHORT).show();
        }));

        restoreWifButton.setOnClickListener(v -> {
            String wif = restoreWifInput.getText().toString().trim();
            if (wif.isEmpty()) {
                Toast.makeText(requireContext(), "Informe a WIF de backup.", Toast.LENGTH_SHORT).show();
                return;
            }
            repository.runIo(() -> {
                try {
                    repository.importWifToSession("Restaurada", wif);
                    requireActivity().runOnUiThread(() -> {
                        restoreWifInput.setText("");
                        Toast.makeText(requireContext(), "WIF restaurada com sucesso.", Toast.LENGTH_LONG).show();
                        repository.activeAccount().ifPresent(account -> {
                            addressValue.setText(account.address());
                            qrImage.setImageBitmap(QrHelper.createQr(QrHelper.paymentUri(account.address()), 520));
                        });
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        });
    }
}
