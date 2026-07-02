package com.infinitericks.wallet.server;

import io.javalin.Javalin;

final class JavalinSupport {
    private JavalinSupport() {
    }

    static Javalin createApp() {
        return Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jsonMapper(new io.javalin.json.JavalinGson());
        });
    }
}
