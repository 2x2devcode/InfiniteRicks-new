package com.infinitericks.wallet.server;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

    static void error(Context ctx, int status, String message) {
        JsonObject out = new JsonObject();
        out.addProperty("error", message == null || message.isBlank() ? "unknown error" : sanitize(message));
        ctx.status(status);
        write(ctx, out);
    }

    private static String sanitize(String message) {
        if (message.contains("<HTML") || message.contains("<html")) {
            return "upstream error (see server logs)";
        }
        if (message.length() > 500) {
            return message.substring(0, 500) + "...";
        }
        return message;
    }
}
