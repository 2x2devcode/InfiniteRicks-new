package com.infinitericks.wallet.security;

import com.infinitericks.wallet.api.PinProvider;
import com.infinitericks.wallet.core.chain.NetworkParameters;

import java.util.Arrays;
import java.util.List;

public final class NativePinProvider implements PinProvider {
    static {
        System.loadLibrary("rickpin");
    }

    private native String[] getPinnedHashes();

    @Override
    public String host() {
        return NetworkParameters.OFFICIAL_API_HOST;
    }

    @Override
    public List<String> pinsForHost(String host) {
        if (NetworkParameters.OFFICIAL_API_HOST.equalsIgnoreCase(host)
                || NetworkParameters.EXPLORER_HOST.equalsIgnoreCase(host)) {
            return Arrays.asList(getPinnedHashes());
        }
        return List.of();
    }
}
