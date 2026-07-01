package com.infinitericks.wallet.ui.wallets;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import com.infinitericks.wallet.core.wallet.WalletAccount;
import com.infinitericks.wallet.data.WalletRepository;
import com.infinitericks.wallet.ui.MainActivity;

public final class WalletsFragment extends Fragment {
    private TextView walletList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wallets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        walletList = view.findViewById(R.id.walletList);
        Button addWalletButton = view.findViewById(R.id.addWalletButton);
        Button exportWifButton = view.findViewById(R.id.exportWifButton);
        WalletRepository repository = ((MainActivity) requireActivity()).repository();
        addWalletButton.setOnClickListener(v -> {
            repository.addAccount("Conta " + (repository.accounts().size() + 1));
            render(repository);
        });
        exportWifButton.setOnClickListener(v -> {
            String wif = repository.exportActiveWif();
            if (wif.isEmpty()) {
                Toast.makeText(requireContext(), "Nenhuma conta ativa", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("rick-wif", wif));
            Toast.makeText(requireContext(), "WIF copiada. Limpe a área de transferência após o backup.", Toast.LENGTH_LONG).show();
        });
        render(repository);
    }

    private void render(WalletRepository repository) {
        StringBuilder builder = new StringBuilder();
        for (WalletAccount account : repository.accounts()) {
            builder.append("• ")
                    .append(account.label())
                    .append(" — ")
                    .append(account.address())
                    .append(repository.activeAccount().map(active -> active.id().equals(account.id()) ? " (ativa)" : "").orElse(""))
                    .append("\n");
        }
        walletList.setText(builder.toString());
    }
}
