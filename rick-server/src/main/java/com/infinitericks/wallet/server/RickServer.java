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

final class RickApiService {
    private final RpcClient rpcClient;
    private final AddressQueryService addressQuery;

    RickApiService(RpcClient rpcClient, AddressQueryService addressQuery) {
        this.rpcClient = rpcClient;
        this.addressQuery = addressQuery;
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
        return addressQuery.balance(address);
    }

    JsonObject utxos(String address) throws IOException {
        return addressQuery.utxos(address);
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
        addressQuery.invalidate(address);
    }
}

/**
 * JSON-only official API for the wallet app (no web UI).
 * Endpoints under /api/*
 */
public final class RickServer {
    public static void main(String[] args) throws IOException {
        RpcClient rpcClient = RpcClientFactory.fromEnvironment();
        AddressQueryService addressQuery = AddressQueryService.create(rpcClient);
        RickApiService service = new RickApiService(rpcClient, addressQuery);
        int listenPort = Integer.parseInt(env("PORT", String.valueOf(NetworkParameters.OFFICIAL_API_PORT)));
        String bindHost = env("BIND_HOST", NetworkParameters.SERVER_BIND_HOST);

        io.javalin.Javalin app = JavalinSupport.createApp();
        ServerSupport.configureErrors(app);
        app.get("/api/health", ctx -> {
            try {
                rpcClient.call("getblockcount", new JsonArray());
                JsonObject ok = new JsonObject();
                ok.addProperty("api", "ok");
                ok.addProperty("rpc", "ok");
                JsonResponses.write(ctx, ok);
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
