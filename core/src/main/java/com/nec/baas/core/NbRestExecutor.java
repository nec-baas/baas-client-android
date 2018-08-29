/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import com.nec.baas.core.internal.*;

import java.io.IOException;

import okhttp3.Request;
import okhttp3.Response;

/**
 * REST APIを実行するクラス。
 * @since 1.0
 */
public interface NbRestExecutor {
    /**
     * Request を非同期実行する。リクエスト結果は handler で返却される。
     * <p>
     * handler には {@link NbPreRestResponseHandler}を渡すこともできる。
     * この場合、{@link NbPreRestResponseHandler#preHandleResponse(Response)}の
     * 呼び出しも合わせて実施される。
     * @param request リクエスト
     * @param handler NbRestResponseHandler
     */
    void executeRequest(Request request, NbRestResponseHandler handler);

    /**
     * Request を同期実行する
     * @param request リクエスト
     * @return レスポンス
     * @throws IOException I/Oエラー
     */
    Response executeRequestSync(Request request) throws IOException;

    /**
     * API呼び出しカウンタの値を取得する
     * @return カウンタ値
     */
    long getApiCount();

    /**
     * API呼び出しカウンタの値を保存する
     */
    void saveApiCount();
}
