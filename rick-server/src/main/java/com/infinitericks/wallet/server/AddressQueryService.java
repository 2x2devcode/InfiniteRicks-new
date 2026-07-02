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
    private static final long BALANCE_CACHE_MS = 30_000L;

    private final RpcClient rpcClient;
    private final ChainIndexer indexer;
    private final Map<String, CachedBalance> balanceCache = new ConcurrentHashMap<>();

    AddressQueryService(RpcClient rpcClient, ChainIndexer indexer) {
        this.rpcClient = rpcClient;
        this.indexer = indexer;
    }

    JsonObject balance(String address) throws IOException {
        CachedBalance cached = balanceCache.get(address);
        if (cached != null && cached.expiresAtMs > System.currentTimeMillis()) {
            return balanceResponse(address, cached.satoshis);
        }
        long satoshis = indexer.balanceSatoshis(address, 1, rpcClient);
        balanceCache.put(address, new CachedBalance(satoshis, System.currentTimeMillis() + BALANCE_CACHE_MS));
        return balanceResponse(address, satoshis);
    }

    JsonObject utxos(String address) throws IOException {
        List<ChainIndexer.IndexedUtxo> utxos = indexer.utxosFor(address, 1, rpcClient);
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

    private JsonObject balanceResponse(String address, long satoshis) {
        JsonObject out = new JsonObject();
        out.addProperty("balance", Amount.fromSatoshis(satoshis));
        out.addProperty("address", address);
        out.addProperty("indexedHeight", indexer.indexedHeight());
        out.addProperty("chainTip", indexer.chainTip());
        return out;
    }

    static AddressQueryService create(RpcClient rpcClient) throws IOException {
        ChainIndexer indexer = ChainIndexer.open();
        indexer.startBackgroundSync(rpcClient);
        return new AddressQueryService(rpcClient, indexer);
    }

    private record CachedBalance(long satoshis, long expiresAtMs) {
    }
}
