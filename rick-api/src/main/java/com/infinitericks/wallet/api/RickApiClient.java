package com.infinitericks.wallet.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.infinitericks.wallet.core.chain.NetworkParameters;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RickApiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static final int MAX_RETRIES = 3;

    private final List<String> baseUrls;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public RickApiClient(List<String> baseUrls, OkHttpClient httpClient) {
        this.baseUrls = List.copyOf(baseUrls);
        this.httpClient = httpClient;
        this.gson = GSON;
    }

    public NetworkStatus getStatus() throws IOException {
        try {
            JsonObject json = getJson(ApiEndpoints.STATUS, true);
            return new NetworkStatus(
                    json.get("online").getAsBoolean(),
                    json.get("chain").getAsString(),
                    json.get("blocks").getAsLong(),
                    json.get("headers").getAsLong(),
                    json.get("progress").getAsDouble(),
                    json.get("peers").getAsInt(),
                    "official-api"
            );
        } catch (IOException officialError) {
            ExplorerSummary explorer = getExplorerSummary();
            return new NetworkStatus(
                    true,
                    "main",
                    explorer.blockCount(),
                    explorer.blockCount(),
                    100.0,
                    explorer.connections(),
                    "explorer-fallback"
            );
        }
    }

    public String getBalance(String address) throws IOException {
        try {
            return getJson(String.format(Locale.US, ApiEndpoints.ADDRESS_BALANCE, address), true)
                    .get("balance")
                    .getAsString();
        } catch (IOException officialError) {
            return getExplorerBalance(address);
        }
    }

    public List<Utxo> getUtxos(String address) throws IOException {
        JsonArray array = getJson(String.format(Locale.US, ApiEndpoints.ADDRESS_UTXOS, address), true)
                .getAsJsonArray("utxos");
        List<Utxo> utxos = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            utxos.add(new Utxo(
                    item.get("txid").getAsString(),
                    item.get("vout").getAsInt(),
                    item.get("amountSatoshis").getAsLong(),
                    item.has("confirmations") ? item.get("confirmations").getAsInt() : 0
            ));
        }
        return utxos;
    }

    public long getFeePerKb() throws IOException {
        try {
            return getJson(ApiEndpoints.FEE, true).get("feePerKbSatoshis").getAsLong();
        } catch (IOException ignored) {
            return NetworkParameters.DEFAULT_FEE_PER_KB;
        }
    }

    public String broadcast(String rawHex) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("rawTx", rawHex);
        JsonObject response = postJson(ApiEndpoints.BROADCAST, body, true);
        return response.get("txid").getAsString();
    }

    public ExplorerSummary getExplorerSummary() throws IOException {
        return fetchExplorerSummary();
    }

    public String getExplorerBalance(String address) throws IOException {
        Request request = new Request.Builder()
                .url(NetworkParameters.EXPLORER_BASE_URL + String.format(Locale.US, ApiEndpoints.EXPLORER_ADDRESS, address))
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("explorer HTTP " + response.code());
            }
            JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
            if (json.has("balance")) {
                return json.get("balance").getAsString();
            }
            if (json.has("final_balance")) {
                return json.get("final_balance").getAsString();
            }
            if (json.has("total_received")) {
                return json.get("total_received").getAsString();
            }
            throw new IOException("explorer response missing balance");
        }
    }

    private ExplorerSummary fetchExplorerSummary() throws IOException {
        Request request = new Request.Builder()
                .url(NetworkParameters.EXPLORER_BASE_URL + ApiEndpoints.EXPLORER_SUMMARY)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("explorer HTTP " + response.code());
            }
            JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
            return new ExplorerSummary(
                    json.has("blockcount") ? json.get("blockcount").getAsLong() : 0,
                    json.has("supply") ? json.get("supply").getAsString() : "0",
                    json.has("connections") ? json.get("connections").getAsInt() : 0
            );
        }
    }

    private JsonObject getJson(String path, boolean officialOnly) throws IOException {
        IOException last = null;
        List<String> urls = officialOnly ? baseUrls : List.of(NetworkParameters.EXPLORER_BASE_URL);
        for (String baseUrl : urls) {
            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                Request request = new Request.Builder().url(baseUrl + path).get().build();
                try (Response response = httpClient.newCall(request).execute()) {
                    String body = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP " + response.code() + " for " + path + ": " + body);
                    }
                    JsonObject json = gson.fromJson(body, JsonObject.class);
                    if (json.has("error")) {
                        throw new IOException(json.get("error").getAsString());
                    }
                    return json;
                } catch (IOException e) {
                    last = e;
                    sleepBackoff(attempt);
                }
            }
        }
        throw last == null ? new IOException("request failed") : last;
    }

    private JsonObject postJson(String path, JsonObject payload, boolean officialOnly) throws IOException {
        IOException last = null;
        RequestBody body = RequestBody.create(payload.toString(), JSON);
        for (String baseUrl : baseUrls) {
            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                Request request = new Request.Builder().url(baseUrl + path).post(body).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP " + response.code() + " for " + path + ": " + responseBody);
                    }
                    JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                    if (json.has("error")) {
                        throw new IOException(json.get("error").getAsString());
                    }
                    return json;
                } catch (IOException e) {
                    last = e;
                    sleepBackoff(attempt);
                }
            }
        }
        throw last == null ? new IOException("request failed") : last;
    }

    private static void sleepBackoff(int attempt) throws IOException {
        try {
            Thread.sleep(Duration.ofMillis(500L * (attempt + 1)).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted during retry", e);
        }
    }

    public record NetworkStatus(
            boolean online,
            String chain,
            long blocks,
            long headers,
            double progress,
            int peers,
            String source
    ) {
    }

    public record Utxo(String txid, int vout, long amountSatoshis, int confirmations) {
    }

    public record ExplorerSummary(long blockCount, String supply, int connections) {
    }
}
