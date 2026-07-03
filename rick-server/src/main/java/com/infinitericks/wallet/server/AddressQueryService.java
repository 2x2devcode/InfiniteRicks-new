package com.infinitericks.wallet.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.infinitericks.wallet.core.wallet.Amount;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves balance and UTXOs for arbitrary P2PKH addresses via the chain indexer.
 */
final class AddressQueryService {
    private static final long BALANCE_CACHE_MS = 15_000L;
    private static final long ZERO_SCANNING_CACHE_MS = 4_000L;

    private final RpcClient rpcClient;
    private final ChainIndexer indexer;
    private final OfficialExplorerClient explorerClient;
    private final Map<String, CachedBalance> balanceCache = new ConcurrentHashMap<>();

    AddressQueryService(RpcClient rpcClient, ChainIndexer indexer, OfficialExplorerClient explorerClient) {
        this.rpcClient = rpcClient;
        this.indexer = indexer;
        this.explorerClient = explorerClient;
    }

    JsonObject balance(String address) throws IOException {
        CachedBalance cached = balanceCache.get(address);
        if (cached != null && cached.expiresAtMs > System.currentTimeMillis()) {
            return balanceResponse(address, cached.satoshis, cached.scanning, cached.source);
        }
        ChainIndexer.BalanceResult result = indexer.balanceFast(address, 1, rpcClient);
        long satoshis = result.satoshis();
        boolean scanning = result.scanning();
        String source = "index";
        if (satoshis == 0L) {
            Long explorerBalance = tryExplorerBalance(address);
            if (explorerBalance != null && explorerBalance > 0L) {
                satoshis = explorerBalance;
                scanning = false;
                source = "explorer";
                indexer.scheduleExplorerEnrich(address, explorerClient, rpcClient);
            }
        }
        long cacheMs = satoshis == 0L && scanning ? ZERO_SCANNING_CACHE_MS : BALANCE_CACHE_MS;
        balanceCache.put(address, new CachedBalance(satoshis, scanning, source, System.currentTimeMillis() + cacheMs));
        return balanceResponse(address, satoshis, scanning, source);
    }

    JsonObject utxos(String address) throws IOException {
        List<ChainIndexer.IndexedUtxo> utxos = indexer.utxosFor(address, 1, rpcClient);
        if (utxos.isEmpty()) {
            Long explorerBalance = tryExplorerBalance(address);
            if (explorerBalance != null && explorerBalance > 0L) {
                indexer.scheduleExplorerEnrich(address, explorerClient, rpcClient);
                utxos = indexer.utxosFor(address, 1, rpcClient);
            }
        }
        JsonArray array = new JsonArray();
        for (ChainIndexer.IndexedUtxo utxo : utxos) {
            JsonObject item = new JsonObject();
            item.addProperty("txid", utxo.txid);
            item.addProperty("vout", utxo.vout);
            item.addProperty("amountSatoshis", utxo.amountSatoshis);
            item.addProperty("confirmations", utxo.confirmations);
            array.add(item);
        }
        JsonObject out = new JsonObject();
        out.add("utxos", array);
        return out;
    }

    void invalidate(String address) {
        balanceCache.remove(address);
    }

    private Long tryExplorerBalance(String address) {
        if (!explorerClient.enabled()) {
            return null;
        }
        try {
            return explorerClient.balanceSatoshis(address);
        } catch (IOException error) {
            System.err.println("[address-query] explorer fallback failed for " + address + ": " + error.getMessage());
            return null;
        }
    }

    private JsonObject balanceResponse(String address, long satoshis, boolean scanning, String source) {
        JsonObject out = new JsonObject();
        out.addProperty("balance", Amount.fromSatoshis(satoshis));
        out.addProperty("address", address);
        out.addProperty("indexedHeight", indexer.indexedHeight());
        out.addProperty("chainTip", indexer.chainTip());
        out.addProperty("scanning", scanning);
        out.addProperty("source", source);
        return out;
    }

    static AddressQueryService create(RpcClient rpcClient) throws IOException {
        OfficialExplorerClient explorerClient = OfficialExplorerClient.fromEnvironment();
        ChainIndexer indexer = ChainIndexer.open();
        AddressQueryService service = new AddressQueryService(rpcClient, indexer, explorerClient);
        indexer.setAddressUpdateListener(service::invalidate);
        indexer.startBackgroundSync(rpcClient);
        return service;
    }

    private record CachedBalance(long satoshis, boolean scanning, String source, long expiresAtMs) {
    }
}
