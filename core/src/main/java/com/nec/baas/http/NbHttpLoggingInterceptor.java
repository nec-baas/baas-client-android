/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.http;

import com.nec.baas.util.*;

import java.io.IOException;

import lombok.Getter;
import lombok.Setter;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * HTTP Logging Interceptor
 */
public class NbHttpLoggingInterceptor implements Interceptor {
    /**
     * ロギング有効
     */
    @Getter @Setter
    private boolean enabled;

    /**
     * 種別 (Net or App)
     */
    private String type;

    private static NbLogger logger = NbLogger.getLogger(NbHttpLoggingInterceptor.class);

    /**
     * Application Interceptor (singleton) を取得する
     */
    @Getter
    private static NbHttpLoggingInterceptor interceptor = new NbHttpLoggingInterceptor(false, "App");

    /**
     * Network Interceptor (singleton)を取得する
     */
    @Getter
    private static NbHttpLoggingInterceptor networkInterceptor = new NbHttpLoggingInterceptor(false, "Net");

    private NbHttpLoggingInterceptor(boolean enabled, String type) {
        this.enabled = enabled;
        this.type = type;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (enabled) {
            String log = String.format("[%s] Sending request %s %s%n%s", type, request.method(), request.url(), request.headers());
            logger.info(log);
        }

        Response response = chain.proceed(request);
        if (enabled) {
            String log = String.format("[%s] %d %s for %s%n%s",
                    type, response.code(), response.message(), response.request().url(), response.headers());
            logger.info(log);
        }

        return response;
    }
}
