package com.infinitericks.wallet.api;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class RickHttpClientFactory {
    private static final ThreadLocal<String> REQUEST_HOST = new ThreadLocal<>();

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
        try {
            X509TrustManager trustManager = pinnedTrustManager(pinProvider);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            SSLSocketFactory socketFactory = sslContext.getSocketFactory();
            return new OkHttpClient.Builder()
                    .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .writeTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .callTimeout(readTimeout.plusSeconds(5).toMillis(), TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(true)
                    .sslSocketFactory(socketFactory, trustManager)
                    .addInterceptor(hostCaptureInterceptor())
                    .addInterceptor(userAgentInterceptor(userAgent))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("failed to create HTTP client", e);
        }
    }

    private static Interceptor hostCaptureInterceptor() {
        return chain -> {
            REQUEST_HOST.set(chain.request().url().host());
            try {
                return chain.proceed(chain.request());
            } finally {
                REQUEST_HOST.remove();
            }
        };
    }

    private static Interceptor userAgentInterceptor(String userAgent) {
        return chain -> {
            Request request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", userAgent)
                    .build();
            return chain.proceed(request);
        };
    }

    private static X509TrustManager pinnedTrustManager(PinProvider pinProvider) throws Exception {
        X509TrustManager system = systemTrustManager();
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                throw new UnsupportedOperationException("client certificates are not supported");
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                system.checkServerTrusted(chain, authType);
                if (chain == null || chain.length == 0) {
                    throw new java.security.cert.CertificateException("empty certificate chain");
                }
                String actual = CertificatePin.sha256Hex(chain[0].getPublicKey().getEncoded());
                String host = REQUEST_HOST.get();
                if (host == null || host.isBlank()) {
                    host = pinProvider.host();
                }
                List<String> allowed = pinProvider.pinsForHost(host);
                boolean matched = false;
                for (String pin : allowed) {
                    if (pin.equalsIgnoreCase(actual)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    throw new java.security.cert.CertificateException(
                            "certificate pin mismatch for " + host + ": " + actual
                    );
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return system.getAcceptedIssuers();
            }
        };
    }

    private static X509TrustManager systemTrustManager() throws Exception {
        TrustManagerFactoryHelper factory = new TrustManagerFactoryHelper();
        return factory.systemTrustManager();
    }

    private static final class TrustManagerFactoryHelper {
        X509TrustManager systemTrustManager() throws Exception {
            javax.net.ssl.TrustManagerFactory factory =
                    javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            return Arrays.stream(factory.getTrustManagers())
                    .filter(X509TrustManager.class::isInstance)
                    .map(X509TrustManager.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no system trust manager"));
        }
    }
}
