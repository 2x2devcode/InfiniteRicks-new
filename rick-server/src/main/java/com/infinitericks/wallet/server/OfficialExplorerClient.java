package com.infinitericks.wallet.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.infinitericks.wallet.core.wallet.Amount;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only client for the public InfiniteRicks block explorer API.
 * Used when the local chain index is behind or a deep scan has not finished yet.
 */
final class OfficialExplorerClient {
    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final boolean enabled;

    OfficialExplorerClient(String baseUrl, boolean enabled) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    static OfficialExplorerClient fromEnvironment() {
        String baseUrl = env("EXPLORER_FALLBACK_URL", "https://explorer.infinitericks.com");
        boolean enabled = !"false".equalsIgnoreCase(env("EXPLORER_FALLBACK_ENABLED", "true"));
        return new OfficialExplorerClient(baseUrl, enabled);
    }

    boolean enabled() {
        return enabled;
    }

    Long balanceSatoshis(String address) throws IOException {
        if (!enabled) {
            return null;
        }
        String body = get("/ext/getbalance/" + address);
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            JsonObject json = GSON.fromJson(trimmed, JsonObject.class);
            if (json.has("balance")) {
                trimmed = json.get("balance").getAsString();
            } else if (json.has("final_balance")) {
                trimmed = json.get("final_balance").getAsString();
            }
        }
        try {
            return Amount.toSatoshis(trimmed);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
            return null;
        }
    }

    List<String> recentTxids(String address, int limit) throws IOException {
        if (!enabled) {
            return List.of();
        }
        String body = get("/ext/getaddresstxs/" + address + "/0/" + Math.max(1, limit));
        if (body == null || body.isBlank() || !body.trim().startsWith("[")) {
            return List.of();
        }
        JsonArray array = GSON.fromJson(body, JsonArray.class);
        if (array == null) {
            return List.of();
        }
        List<String> txids = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            if (item.has("txid")) {
                txids.add(item.get("txid").getAsString());
            }
        }
        return txids;
    }

    private String get(String path) throws IOException {
        URI uri = URI.create(baseUrl + path);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("explorer HTTP " + response.statusCode() + " for " + uri);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("explorer request interrupted", e);
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
