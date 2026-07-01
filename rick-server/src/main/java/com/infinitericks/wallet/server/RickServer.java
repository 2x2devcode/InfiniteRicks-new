package com.infinitericks.wallet.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.infinitericks.wallet.core.chain.NetworkParameters;
import com.infinitericks.wallet.core.wallet.Amount;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class RickApiService {
    private final RpcClient rpcClient;
    private final Map<String, Long> balanceCache = new ConcurrentHashMap<>();

    RickApiService(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    JsonObject status() throws IOException {
        JsonObject info = RpcNodeInfo.getInfo(rpcClient);
        JsonObject out = new JsonObject();
        out.addProperty("online", true);
        out.addProperty("chain", "main");
        out.addProperty("blocks", RpcNodeInfo.blocks(info));
        out.addProperty("headers", RpcNodeInfo.headers(info));
        out.addProperty("progress", 100.0);
        out.addProperty("peers", RpcNodeInfo.connections(info));
        return out;
    }

    JsonObject balance(String address) throws IOException {
        if (balanceCache.containsKey(address)) {
            return cachedBalance(address, balanceCache.get(address));
        }
        JsonArray params = new JsonArray();
        params.add(address);
        params.add(1);
        double balance = rpcClient.call("getreceivedbyaddress", params).getAsDouble();
        long satoshis = Amount.toSatoshis(String.format(Locale.US, "%.8f", balance));
        balanceCache.put(address, satoshis);
        return cachedBalance(address, satoshis);
    }

    JsonObject utxos(String address) throws IOException {
        JsonArray params = new JsonArray();
        params.add(1);
        params.add(9999999);
        params.add(address);
        JsonArray unspent = rpcClient.call("listunspent", params).getAsJsonArray();
        JsonArray utxos = new JsonArray();
        for (JsonElement element : unspent) {
            JsonObject item = element.getAsJsonObject();
            JsonObject utxo = new JsonObject();
            utxo.addProperty("txid", item.get("txid").getAsString());
            utxo.addProperty("vout", item.get("vout").getAsInt());
            long satoshis = Amount.toSatoshis(String.format(Locale.US, "%.8f", item.get("amount").getAsDouble()));
            utxo.addProperty("amountSatoshis", satoshis);
            utxo.addProperty("confirmations", item.get("confirmations").getAsInt());
            utxos.add(utxo);
        }
        JsonObject out = new JsonObject();
        out.add("utxos", utxos);
        return out;
    }

    JsonObject fee() {
        JsonObject out = new JsonObject();
        out.addProperty("feePerKbSatoshis", NetworkParameters.DEFAULT_FEE_PER_KB);
        return out;
    }

    JsonObject broadcast(String rawTx) throws IOException {
        JsonArray params = new JsonArray();
        params.add(rawTx);
        String txid = rpcClient.call("sendrawtransaction", params).getAsString();
        JsonObject out = new JsonObject();
        out.addProperty("txid", txid);
        return out;
    }

    void invalidate(String address) {
        balanceCache.remove(address);
    }

    private JsonObject cachedBalance(String address, long satoshis) {
        JsonObject out = new JsonObject();
        out.addProperty("balance", Amount.fromSatoshis(satoshis));
        out.addProperty("address", address);
        return out;
    }
}

/**
 * JSON-only official API for the wallet app (no web UI).
 * Endpoints under /api/*
 */
public final class RickServer {
    public static void main(String[] args) {
        RpcClient rpcClient = RpcClientFactory.fromEnvironment();
        RickApiService service = new RickApiService(rpcClient);
        int listenPort = Integer.parseInt(env("PORT", String.valueOf(NetworkParameters.OFFICIAL_API_PORT)));
        String bindHost = env("BIND_HOST", NetworkParameters.SERVER_BIND_HOST);

        io.javalin.Javalin app = io.javalin.Javalin.create(config -> config.showJavalinBanner = false);
        ServerSupport.configureErrors(app);
        app.get("/api/health", ctx -> {
            try {
                rpcClient.call("getblockcount", new JsonArray());
                JsonResponses.write(ctx, java.util.Map.of("api", "ok", "rpc", "ok"));
            } catch (IOException error) {
                JsonResponses.error(ctx, 502, error.getMessage());
            }
        });
        app.get("/api/status", ServerSupport.rpc(service::status));
        app.get("/api/fee", ServerSupport.rpc(service::fee));
        app.get("/api/address/{address}/balance", ctx -> JsonResponses.write(ctx, service.balance(ctx.pathParam("address"))));
        app.get("/api/address/{address}/utxos", ctx -> JsonResponses.write(ctx, service.utxos(ctx.pathParam("address"))));
        app.get("/api/address/{address}/txs", ctx -> JsonResponses.write(ctx, Map.of("transactions", List.of())));
        app.post("/api/tx/broadcast", ctx -> {
            JsonObject body = new Gson().fromJson(ctx.body(), JsonObject.class);
            JsonResponses.write(ctx, service.broadcast(body.get("rawTx").getAsString()));
        });
        app.post("/api/cache/invalidate/{address}", ctx -> {
            service.invalidate(ctx.pathParam("address"));
            JsonResponses.write(ctx, Map.of("ok", true));
        });
        app.error(404, JsonResponses::notFound);
        app.start(bindHost, listenPort);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
