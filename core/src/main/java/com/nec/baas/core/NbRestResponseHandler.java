/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import okhttp3.Response;

/**
 * REST APIの実行結果を受け取るインターフェース。
 * @since 1.0
 */
public interface NbRestResponseHandler {
    /**
     * REST APIの実行結果を受け取るメソッド。
     * @param response Response
     * @param status ステータスコード
     */
    void handleResponse(Response response, int status);
}
