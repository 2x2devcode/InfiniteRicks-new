package com.infinitericks.wallet.security;

import com.infinitericks.wallet.api.PinProvider;
import com.infinitericks.wallet.core.chain.NetworkParameters;

import java.util.Arrays;
import java.util.List;

public final class NativePinProvider implements PinProvider {
    static {
        System.loadLibrary("rickpin");
    }

    private native String[] getApiPinnedHashes();
    private native String[] getExplorerPinnedHashes();

    @Override
    public String host() {
        return NetworkParameters.OFFICIAL_API_HOST;
    }

    @Override
    public List<String> pinnedHosts() {
        return List.of(NetworkParameters.OFFICIAL_API_HOST, NetworkParameters.EXPLORER_HOST);
    }

    @Override
    public List<String> pinsForHost(String host) {
        if (NetworkParameters.OFFICIAL_API_HOST.equalsIgnoreCase(host)) {
            return Arrays.asList(getApiPinnedHashes());
        }
        if (NetworkParameters.EXPLORER_HOST.equalsIgnoreCase(host)) {
            return Arrays.asList(getExplorerPinnedHashes());
        }
        return List.of();
    }
}
