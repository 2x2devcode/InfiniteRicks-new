package com.infinitericks.wallet.api;

import com.infinitericks.wallet.core.chain.NetworkParameters;

import java.util.List;

public final class ApiEndpoints {
    public static final String STATUS = "/api/status";
    public static final String FEE = "/api/fee";
    public static final String BROADCAST = "/api/tx/broadcast";
    public static final String ADDRESS_BALANCE = "/api/address/%s/balance";
    public static final String ADDRESS_UTXOS = "/api/address/%s/utxos";
    public static final String ADDRESS_TXS = "/api/address/%s/txs";
    public static final String CACHE_INVALIDATE = "/api/cache/invalidate/%s";

    public static final String EXPLORER_SUMMARY = "/ext/getsummary";
    public static final String EXPLORER_ADDRESS = "/ext/getaddress/%s";

    public static final List<String> OFFICIAL_BASE_URLS = List.of(NetworkParameters.OFFICIAL_API_BASE_URL);

    private ApiEndpoints() {
    }
}
