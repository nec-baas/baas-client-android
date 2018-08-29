/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import com.nec.baas.core.*;

import okhttp3.Response;

/**
 * REST APIの実行結果を受け取るインターフェース。
 * バックグランドで重い処理を行うための preHandle 処理付き。
 * @since 6.5.0
 */
public interface NbPreRestResponseHandler extends NbRestResponseHandler {
    /**
     * 事前ハンドルレスポンス通知。
     * {@link #handleResponse} の前に呼び出される。
     * <p>
     * Android実装では、本メソッドはサブスレッド上で呼び出される
     * ({@link #handleResponse}は UIスレッド上で呼び出される)。
     * ダウンロード処理など処理負荷の高い処理を行う。
     * @param response Response
     * @return ステータスコード
     */
    int preHandleResponse(Response response);
}
