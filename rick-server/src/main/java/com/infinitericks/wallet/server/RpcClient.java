package com.infinitericks.wallet.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
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
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw new IOException("RPC unauthorized (401): rpcuser/rpcpassword invalid — check ~/.InfiniteRicks/InfiniteRicks.conf and restart infinitericksd");
            }
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
