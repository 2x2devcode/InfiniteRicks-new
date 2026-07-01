package com.infinitericks.wallet.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;

final class RpcNodeInfo {
    private RpcNodeInfo() {
    }

    static JsonObject getInfo(RpcClient rpcClient) throws IOException {
        return rpcClient.call("getinfo", new JsonArray()).getAsJsonObject();
    }

    static long blocks(JsonObject info) {
        if (info.has("blocks")) {
            return info.get("blocks").getAsLong();
        }
        return 0L;
    }

    static long headers(JsonObject info) {
        if (info.has("headers")) {
            return info.get("headers").getAsLong();
        }
        return blocks(info);
    }

    static int connections(JsonObject info) {
        if (!info.has("connections")) {
            return 0;
        }
        JsonElement value = info.get("connections");
        if (value.isJsonPrimitive()) {
            return value.getAsInt();
        }
        return 0;
    }

    static String supply(JsonObject info) {
        if (info.has("moneysupply")) {
            return info.get("moneysupply").getAsString();
        }
        if (info.has("money_supply")) {
            return info.get("money_supply").getAsString();
        }
        return "0";
    }
}
