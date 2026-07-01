package com.infinitericks.wallet.server;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.javalin.http.Context;

final class JsonResponses {
    private static final Gson GSON = new Gson();

    private JsonResponses() {
    }

    static void write(Context ctx, Object body) {
        ctx.contentType("application/json").result(GSON.toJson(body));
    }

    static void write(Context ctx, JsonElement body) {
        ctx.contentType("application/json").result(body.toString());
    }

    static void notFound(Context ctx) {
        ctx.status(404);
        write(ctx, java.util.Map.of("error", "not found"));
    }
}
