package com.infinitericks.wallet.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.infinitericks.wallet.core.chain.NetworkParameters;

import java.io.IOException;

/**
 * JSON-only explorer API for the wallet app (no web UI).
 * Endpoints: /ext/getsummary, /ext/getaddress/{address}
 */
public final class RickExplorerServer {
    public static void main(String[] args) throws IOException {
        RpcClient rpcClient = RpcClientFactory.fromEnvironment();
        AddressQueryService addressQuery = AddressQueryService.create(rpcClient);
        int listenPort = Integer.parseInt(env("EXPLORER_PORT", String.valueOf(NetworkParameters.EXPLORER_PORT)));
        String bindHost = env("BIND_HOST", NetworkParameters.SERVER_BIND_HOST);

        io.javalin.Javalin app = JavalinSupport.createApp();
        ServerSupport.configureErrors(app);
        app.get("/ext/health", ctx -> {
            try {
                rpcClient.call("getblockcount", new JsonArray());
                JsonObject ok = new JsonObject();
                ok.addProperty("explorer", "ok");
                ok.addProperty("rpc", "ok");
                JsonResponses.write(ctx, ok);
            } catch (IOException error) {
                JsonResponses.error(ctx, 502, error.getMessage());
            }
        });
        app.get("/ext/getsummary", ServerSupport.rpc(() -> summary(rpcClient)));
        app.get("/ext/getaddress/{address}", ctx -> JsonResponses.write(ctx, address(addressQuery, ctx.pathParam("address"))));
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

    private static JsonObject address(AddressQueryService addressQuery, String address) throws IOException {
        JsonObject balance = addressQuery.balance(address);
        JsonObject out = new JsonObject();
        String formatted = balance.get("balance").getAsString();
        out.addProperty("balance", formatted);
        out.addProperty("final_balance", formatted);
        return out;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
