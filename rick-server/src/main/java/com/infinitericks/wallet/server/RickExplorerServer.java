package com.infinitericks.wallet.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.infinitericks.wallet.core.chain.NetworkParameters;
import com.infinitericks.wallet.core.wallet.Amount;

import java.io.IOException;
import java.util.Locale;

/**
 * JSON-only explorer API for the wallet app (no web UI).
 * Endpoints: /ext/getsummary, /ext/getaddress/{address}
 */
public final class RickExplorerServer {
    public static void main(String[] args) {
        RpcClient rpcClient = RpcClientFactory.fromEnvironment();
        int listenPort = Integer.parseInt(env("EXPLORER_PORT", String.valueOf(NetworkParameters.EXPLORER_PORT)));
        String bindHost = env("BIND_HOST", NetworkParameters.SERVER_BIND_HOST);

        io.javalin.Javalin app = io.javalin.Javalin.create(config -> config.showJavalinBanner = false);
        ServerSupport.configureErrors(app);
        app.get("/ext/health", ctx -> {
            try {
                rpcClient.call("getblockcount", new JsonArray());
                JsonResponses.write(ctx, java.util.Map.of("explorer", "ok", "rpc", "ok"));
            } catch (IOException error) {
                JsonResponses.error(ctx, 502, error.getMessage());
            }
        });
        app.get("/ext/getsummary", ServerSupport.rpc(() -> summary(rpcClient)));
        app.get("/ext/getaddress/{address}", ctx -> JsonResponses.write(ctx, address(rpcClient, ctx.pathParam("address"))));
        app.error(404, JsonResponses::notFound);
        app.start(bindHost, listenPort);
    }

    private static JsonObject summary(RpcClient rpcClient) throws IOException {
        JsonObject info = RpcNodeInfo.getInfo(rpcClient);
        JsonObject out = new JsonObject();
        out.addProperty("blockcount", RpcNodeInfo.blocks(info));
        out.addProperty("supply", RpcNodeInfo.supply(info));
        out.addProperty("connections", RpcNodeInfo.connections(info));
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
