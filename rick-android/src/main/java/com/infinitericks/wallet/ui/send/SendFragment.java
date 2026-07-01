package com.infinitericks.wallet.ui.send;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.infinitericks.wallet.R;
import com.infinitericks.wallet.data.WalletRepository;
import com.infinitericks.wallet.ui.MainActivity;

public final class SendFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_send, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        EditText destinationInput = view.findViewById(R.id.destinationInput);
        EditText amountInput = view.findViewById(R.id.amountInput);
        Button sendButton = view.findViewById(R.id.sendButton);
        WalletRepository repository = ((MainActivity) requireActivity()).repository();
        sendButton.setOnClickListener(v -> repository.runIo(() -> {
            try {
                String txid = repository.send(
                        destinationInput.getText().toString().trim(),
                        amountInput.getText().toString().trim()
                );
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Enviado: " + txid, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }));
    }
}
