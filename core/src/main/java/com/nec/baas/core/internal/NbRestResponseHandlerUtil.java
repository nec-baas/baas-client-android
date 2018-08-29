/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import com.nec.baas.core.*;

import okhttp3.Response;

/**
 * NbRestResponseHandler ユーティリティクラス
 * @since 6.5.0
 */
public class NbRestResponseHandlerUtil {
    /**
     * preHandleResponse, handleResponse を実行する
     * @param response Response
     * @param handler Handler
     */
    public static void handleResponse(Response response, NbRestResponseHandler handler) {
        int status = preHandleResponse(response, handler);
        handler.handleResponse(response, status);
    }

    /**
     * preHandleResponse のみを実行する
     * @param response Response
     * @param handler Handler
     * @return status code
     */
    public static int preHandleResponse(Response response, NbRestResponseHandler handler) {
        if (handler instanceof NbPreRestResponseHandler) {
            NbPreRestResponseHandler preHandler = (NbPreRestResponseHandler) handler;
            return preHandler.preHandleResponse(response);
        } else {
            return response.code();
        }
    }
}
