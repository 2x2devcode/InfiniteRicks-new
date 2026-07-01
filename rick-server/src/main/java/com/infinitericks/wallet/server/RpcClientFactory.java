package com.infinitericks.wallet.server;

import com.infinitericks.wallet.core.chain.NetworkParameters;

final class RpcClientFactory {
    private RpcClientFactory() {
    }

    static RpcClient fromEnvironment() {
        String host = env("RICK_RPC_HOST", "127.0.0.1");
        int port = Integer.parseInt(env("RICK_RPC_PORT", String.valueOf(NetworkParameters.RPC_PORT)));
        String user = env("RICK_RPC_USER", "rickrpc");
        String password = env("RICK_RPC_PASSWORD", "rickrpc");
        System.out.println("[rick-server] RPC target http://" + host + ":" + port + " user=" + user);
        return new RpcClient(host, port, user, password);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
