package com.infinitericks.wallet.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.infinitericks.wallet.core.chain.NetworkParameters;
import com.infinitericks.wallet.core.wallet.Amount;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * JSON-only explorer API for the wallet app (no web UI).
 * Endpoints: /ext/getsummary, /ext/getaddress/{address}
 */
public final class RickExplorerServer {
    public static void main(String[] args) {
        RpcClient rpcClient = RpcClientFactory.fromEnvironment();
        int listenPort = Integer.parseInt(env("EXPLORER_PORT", String.valueOf(NetworkParameters.EXPLORER_PORT)));

        io.javalin.Javalin app = io.javalin.Javalin.create(config -> config.showJavalinBanner = false);
        app.get("/ext/getsummary", ctx -> ctx.json(summary(rpcClient)));
        app.get("/ext/getaddress/{address}", ctx -> ctx.json(address(rpcClient, ctx.pathParam("address"))));
        app.error(404, ctx -> ctx.status(404).json(Map.of("error", "not found")));
        app.start(listenPort);
    }

    private static JsonObject summary(RpcClient rpcClient) throws IOException {
        JsonObject info = rpcClient.call("getinfo", new JsonArray()).getAsJsonObject();
        JsonObject out = new JsonObject();
        out.addProperty("blockcount", info.get("blocks").getAsLong());
        if (info.has("moneysupply")) {
            out.addProperty("supply", info.get("moneysupply").getAsString());
        } else if (info.has("money_supply")) {
            out.addProperty("supply", info.get("money_supply").getAsString());
        } else {
            out.addProperty("supply", "0");
        }
        out.addProperty("connections", info.get("connections").getAsInt());
        return out;
    }

    private static JsonObject address(RpcClient rpcClient, String address) throws IOException {
        JsonArray params = new JsonArray();
        params.add(address);
        params.add(1);
        double balance = rpcClient.call("getreceivedbyaddress", params).getAsDouble();
        JsonObject out = new JsonObject();
        String formatted = Amount.fromSatoshis(Amount.toSatoshis(String.format(Locale.US, "%.8f", balance)));
        out.addProperty("balance", formatted);
        out.addProperty("final_balance", formatted);
        return out;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
