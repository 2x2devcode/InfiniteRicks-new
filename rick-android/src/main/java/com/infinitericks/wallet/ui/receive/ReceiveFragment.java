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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.infinitericks.wallet.R;
import com.infinitericks.wallet.data.WalletRepository;
import com.infinitericks.wallet.ui.MainActivity;

public final class ReceiveFragment extends Fragment {
    private TextView addressValue;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_receive, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        addressValue = view.findViewById(R.id.addressValue);
        Button copyAddressButton = view.findViewById(R.id.copyAddressButton);
        EditText restoreWifInput = view.findViewById(R.id.restoreWifInput);
        Button restoreWifButton = view.findViewById(R.id.restoreWifButton);
        WalletRepository repository = ((MainActivity) requireActivity()).repository();

        repository.activeAccount().ifPresent(account -> addressValue.setText(account.address()));
        copyAddressButton.setOnClickListener(v -> repository.activeAccount().ifPresent(account -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("rick-address", account.address()));
            Toast.makeText(requireContext(), "Endereço copiado", Toast.LENGTH_SHORT).show();
        }));
        restoreWifButton.setOnClickListener(v -> Toast.makeText(
                requireContext(),
                "Use a aba Carteiras para importar com a senha da sessão ativa.",
                Toast.LENGTH_LONG
        ).show());
        restoreWifInput.setVisibility(View.GONE);
        restoreWifButton.setVisibility(View.GONE);
    }
}
