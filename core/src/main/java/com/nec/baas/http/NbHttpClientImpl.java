/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.http;

import com.nec.baas.util.*;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import lombok.NonNull;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * NbHttpClient 実装
 *
 * @since 1.0
 */
public class NbHttpClientImpl extends NbHttpClient {
    private static final NbLogger log = NbLogger.getLogger(NbHttpClientImpl.class);

    /**
     * 接続タイムアウト(ms)
     */
    private static final long CONNECT_TIMEOUT = 10_000;

    /**
     * 読み込みタイムアウト(ms)
     */
    private static final long READ_TIMEOUT = 10_000;

    /**
     * 書き込みタイムアウト (ms)
     */
    private static final long WRITE_TIMEOUT = 10_000;

    /*package*/ OkHttpClient mHttpClient = null;
    private Proxy mProxy;

    /*package*/ NbCertManager mCertManager;

    /*package*/ NbHttpClientImpl() {
        super();
    }

    @Override
    public Response executeRequest(@NonNull Request request) throws IOException {
        OkHttpClient client;
        synchronized (this) {
            client = mHttpClient;
        }
        if (client == null) {
            throw new IllegalStateException("HttpClient is not opened.");
        }
        log.fine("REQ : {0} {1}", request.method(), request.url().toString());

        Response response = client.newCall(request).execute();
        log.fine("RSP : {0} {1}", response.code(), response.message());

        return response;
    }

    @Override
    public synchronized void setProxy(Proxy proxy) {
        if (proxy == mProxy || (proxy != null && proxy.equals(mProxy))) {
            return; // not changed
        }

        mProxy = proxy;
        if (mHttpClient != null) {
            log.info("Proxy config changed, re-open current http client.");
            close();
            open();
        }
    }

    @Override
    public synchronized void open() {
        if (mHttpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
                    .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
                    .writeTimeout(WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
                    .addInterceptor(NbHttpLoggingInterceptor.getInterceptor())
                    .addNetworkInterceptor(NbHttpLoggingInterceptor.getNetworkInterceptor());

            builder = setSslSocketFactory(builder);
            if (mProxy != null) {
                builder = builder.proxy(mProxy);
            }

            mHttpClient = builder.build();
        }
    }

    /**
     * OkHttpClient.Builder に SSL Socket Factory を設定する
     * @param builder builder
     * @return builder
     */
    private OkHttpClient.Builder setSslSocketFactory(OkHttpClient.Builder builder) {
        KeyManager[] keyManagers = null;
        X509TrustManager trustManager = null;

        if (mCertManager != null) {
            // クライアント証明書を使用する
            log.info("client cert authentication enabled. preparing...");
            keyManagers = mCertManager.getKeyManagers();
            trustManager = mCertManager.getTrustManager();

            if (keyManagers == null || trustManager == null) {
                throw new IllegalStateException("set valid client certificate before creating SSL context.");
            }
        }
        if (allowSelfSignedCertificate) {
            // サーバ自己署名証明書を許可する
            log.info("self-signed cert is allowed. make sure security risk of this setting. preparing ...");
            trustManager = getTrustAllTrustManager();
            builder = builder.hostnameVerifier(ALLOW_ALL_HOSTNAME_VERIFIER);
        }

        if (trustManager != null) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(keyManagers, new X509TrustManager[]{trustManager}, new SecureRandom());

                builder = builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                log.severe("setSslSocketFactory(): {0}", e.getMessage());
                throw new RuntimeException("Failed to set ssl socket factory.", e);
            }
        }
        return builder;
    }

    private X509TrustManager getTrustAllTrustManager() {
        return new X509TrustManager() {
            private X509Certificate[] acceptedIssuers = new X509Certificate[]{};

            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String authType)
                    throws CertificateException {
                // do nothing
            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String authType)
                    throws CertificateException {
                // do nothing
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return acceptedIssuers;
            }
        };
    }

    private static final HostnameVerifier ALLOW_ALL_HOSTNAME_VERIFIER = new HostnameVerifier() {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    };

    @Override
    public synchronized void close() {
        if (mHttpClient != null) {
            mHttpClient.dispatcher().executorService().shutdown();
            mHttpClient = null;
        }
    }

    @Override
    public synchronized void setClientCertificate(InputStream clientCert, String password, InputStream trustedCaCert) throws IOException, GeneralSecurityException {

        mCertManager = null;

        // nullの場合、""とみなす
        if (password == null) {
            password = "";
        }

        NbCertManager config = new NbCertManager();
        try {
            // パスワードはログに出力せず、SDK内のメンバ変数には格納しないこと
            // クライアント証明書用のKeyManagerを設定
            config.configureKeyManager(clientCert, password);
            // TrustManagerの設定
            config.configureTrustManager(trustedCaCert);

            mCertManager = config;
        } catch (IOException | GeneralSecurityException e) {
            // 例外が発生した場合、証明書の設定は失敗扱いとする
            log.warning("setClientCertificate failed " + e.getMessage());
            throw e;
        } finally {
            reOpenHttpClient();
        }
    }

    @Override
    public synchronized void disableClientCertificate() {

        if (mCertManager != null) {
            log.info("disable client certificate");
            mCertManager = null;
            reOpenHttpClient();
        }
    }

    @Override
    public boolean isClientCertSet() {
        return (mCertManager != null);
    }


    private void reOpenHttpClient() {
        if (mHttpClient != null) {
            log.info("re-open current http client.");
            close();
            open();
        }
    }


}
