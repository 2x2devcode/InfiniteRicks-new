package com.infinitericks.wallet.api;

import java.util.List;

public interface PinProvider {
    String host();

    List<String> pinnedHosts();

    List<String> pinsForHost(String host);
}
