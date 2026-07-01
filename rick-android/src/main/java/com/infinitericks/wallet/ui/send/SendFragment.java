package com.infinitericks.wallet.ui.send;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.infinitericks.wallet.R;
import com.infinitericks.wallet.data.WalletRepository;
import com.infinitericks.wallet.ui.MainActivity;
import com.infinitericks.wallet.ui.QrHelper;

import java.util.Collections;

public final class SendFragment extends Fragment {
    private EditText destinationInput;
    private EditText amountInput;
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startQrScanner();
                } else {
                    Toast.makeText(requireContext(), "Permissão da câmera negada.", Toast.LENGTH_LONG).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_send, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        destinationInput = view.findViewById(R.id.destinationInput);
        amountInput = view.findViewById(R.id.amountInput);
        Button sendButton = view.findViewById(R.id.sendButton);
        Button scanQrButton = view.findViewById(R.id.scanQrButton);
        WalletRepository repository = ((MainActivity) requireActivity()).repository();

        scanQrButton.setOnClickListener(v -> requestCameraAndScan());
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            destinationInput.setText(QrHelper.parsePayment(result.getContents()));
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void requestCameraAndScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startQrScanner();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startQrScanner() {
        IntentIntegrator integrator = IntentIntegrator.forSupportFragment(this);
        integrator.setDesiredBarcodeFormats(Collections.singletonList(IntentIntegrator.QR_CODE));
        integrator.setPrompt("Aponte para o QR Code InfiniteRicks");
        integrator.setBeepEnabled(false);
        integrator.initiateScan();
    }
}
