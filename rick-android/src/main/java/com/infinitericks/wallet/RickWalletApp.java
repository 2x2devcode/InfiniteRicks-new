package com.infinitericks.wallet;

import android.app.Application;

import com.infinitericks.wallet.api.ApiEndpoints;
import com.infinitericks.wallet.api.RickApiClient;
import com.infinitericks.wallet.api.RickHttpClientFactory;
import com.infinitericks.wallet.data.WalletRepository;
import com.infinitericks.wallet.security.NativePinProvider;

public final class RickWalletApp extends Application {
    private WalletRepository walletRepository;
    private RickApiClient apiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        NativePinProvider pinProvider = new NativePinProvider();
        apiClient = new RickApiClient(
                ApiEndpoints.OFFICIAL_BASE_URLS,
                RickHttpClientFactory.create(pinProvider, "InfiniteRicks-Wallet/1.0.0")
        );
        walletRepository = new WalletRepository(this, apiClient);
    }

    public WalletRepository walletRepository() {
        return walletRepository;
    }

    public RickApiClient apiClient() {
        return apiClient;
    }
}
