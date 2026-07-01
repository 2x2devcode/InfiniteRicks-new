package com.infinitericks.wallet.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.infinitericks.wallet.core.chain.NetworkParameters;
import com.infinitericks.wallet.core.wallet.Amount;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class RpcClient {
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;
    private final URI rpcUri;
    private final String authHeader;
    private final AtomicLong idSequence = new AtomicLong(1);

    RpcClient(String host, int port, String user, String password) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.rpcUri = URI.create(String.format(Locale.US, "http://%s:%d/", host, port));
        String token = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.authHeader = "Basic " + token;
    }

    JsonElement call(String method, JsonArray params) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "1.0");
        request.addProperty("id", idSequence.getAndIncrement());
        request.addProperty("method", method);
        request.add("params", params == null ? new JsonArray() : params);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(rpcUri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("RPC HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
            if (json.has("error") && !json.get("error").isJsonNull()) {
                throw new IOException(json.getAsJsonObject("error").toString());
            }
            return json.get("result");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("RPC interrupted", e);
        }
    }
}

final class RickApiService {
    private final RpcClient rpcClient;
    private final Map<String, Long> balanceCache = new ConcurrentHashMap<>();

    RickApiService(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    JsonObject status() throws IOException {
        JsonObject info = rpcClient.call("getinfo", new JsonArray()).getAsJsonObject();
        JsonObject out = new JsonObject();
        out.addProperty("online", true);
        out.addProperty("chain", "main");
        out.addProperty("blocks", info.get("blocks").getAsLong());
        out.addProperty("headers", info.has("headers") ? info.get("headers").getAsLong() : info.get("blocks").getAsLong());
        out.addProperty("progress", 100.0);
        out.addProperty("peers", info.get("connections").getAsInt());
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

public final class RickServer {
    public static void main(String[] args) {
        String host = env("RICK_RPC_HOST", "127.0.0.1");
        int port = Integer.parseInt(env("RICK_RPC_PORT", String.valueOf(NetworkParameters.RPC_PORT)));
        String user = env("RICK_RPC_USER", "rickrpc");
        String password = env("RICK_RPC_PASSWORD", "rickrpc");
        int listenPort = Integer.parseInt(env("PORT", "8080"));

        RpcClient rpcClient = new RpcClient(host, port, user, password);
        RickApiService service = new RickApiService(rpcClient);

        io.javalin.Javalin app = io.javalin.Javalin.create(config -> config.showJavalinBanner = false);
        app.get("/api/status", ctx -> ctx.json(service.status()));
        app.get("/api/fee", ctx -> ctx.json(service.fee()));
        app.get("/api/address/{address}/balance", ctx -> ctx.json(service.balance(ctx.pathParam("address"))));
        app.get("/api/address/{address}/utxos", ctx -> ctx.json(service.utxos(ctx.pathParam("address"))));
        app.get("/api/address/{address}/txs", ctx -> ctx.json(Map.of("transactions", List.of())));
        app.post("/api/tx/broadcast", ctx -> {
            JsonObject body = new Gson().fromJson(ctx.body(), JsonObject.class);
            ctx.json(service.broadcast(body.get("rawTx").getAsString()));
        });
        app.post("/api/cache/invalidate/{address}", ctx -> {
            service.invalidate(ctx.pathParam("address"));
            ctx.json(Map.of("ok", true));
        });
        app.start(listenPort);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
