package com.infinitericks.wallet.api;

import okhttp3.CertificatePinner;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class RickHttpClientFactory {
    private RickHttpClientFactory() {
    }

    public static OkHttpClient create(PinProvider pinProvider, String userAgent) {
        return create(pinProvider, userAgent, Duration.ofSeconds(15), Duration.ofSeconds(30));
    }

    public static OkHttpClient create(
            PinProvider pinProvider,
            String userAgent,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        CertificatePinner.Builder pinnerBuilder = new CertificatePinner.Builder();
        for (String host : pinProvider.pinnedHosts()) {
            for (String pin : pinProvider.pinsForHost(host)) {
                pinnerBuilder.add(host, CertificatePin.okHttpPinFromHex(pin));
            }
        }
        return new OkHttpClient.Builder()
                .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .callTimeout(readTimeout.plusSeconds(5).toMillis(), TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .certificatePinner(pinnerBuilder.build())
                .addInterceptor(userAgentInterceptor(userAgent))
                .build();
    }

    private static Interceptor userAgentInterceptor(String userAgent) {
        return chain -> {
            Request request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent)
                    .build();
            return chain.proceed(request);
        };
    }
}
