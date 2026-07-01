package com.infinitericks.wallet.server;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;

import java.io.IOException;

final class ServerSupport {
    private ServerSupport() {
    }

    static void configureErrors(Javalin app) {
        app.exception(Exception.class, (exception, ctx) -> {
            int status = exception instanceof IOException ? 502 : 500;
            System.err.println("[rick-server] " + requestLabel(ctx) + " -> " + exception.getMessage());
            exception.printStackTrace(System.err);
            JsonResponses.error(ctx, status, rootMessage(exception));
        });
    }

    static Handler rpc(RpcAction action) {
        return ctx -> JsonResponses.write(ctx, action.run());
    }

    @FunctionalInterface
    interface RpcAction {
        Object run() throws IOException;
    }

    private static String requestLabel(Context ctx) {
        return ctx.method() + " " + ctx.path();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return message;
    }
}
