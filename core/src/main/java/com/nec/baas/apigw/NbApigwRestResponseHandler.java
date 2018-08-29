/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */
package com.nec.baas.apigw;

import com.nec.baas.core.NbBaseCallback;
import com.nec.baas.core.NbErrorInfo;
import com.nec.baas.core.NbRestResponseHandler;
import com.nec.baas.core.NbStatus;
import com.nec.baas.json.NbJSONObject;
import com.nec.baas.util.NbLogger;
import com.nec.baas.util.NbUtil;

import java.io.InputStream;

import lombok.experimental.Accessors;
import okhttp3.Response;

/**
 * APIゲートウェイ用レスポンスハンドラ
 * Response をバイナリ(InputStream)で返却する。
 * いずれか１つの onSuccess のみをオーバライドすればよい。
 * 失敗時は自動的に callback.onFailure() を呼び出す。
 * @since 7.0.0
 */
@Accessors(prefix = "m")
public abstract class NbApigwRestResponseHandler implements NbRestResponseHandler {
    private static final NbLogger log = NbLogger.getLogger(NbApigwRestResponseHandler.class);

    private final NbBaseCallback mCallback;
    private final String mLogPrefix;

    /**
     * コンストラクタ
     * @param callback コールバック
     */
    public NbApigwRestResponseHandler(NbBaseCallback callback) {
        this(callback, "NbApigwRestResponseHandler");
    }

    /**
     * コンストラクタ
     * @param callback コールバック
     * @param logPrefix ログ表示用プレフィクス
     */
    public NbApigwRestResponseHandler(NbBaseCallback callback, String logPrefix) {
        mCallback = callback;
        mLogPrefix = logPrefix;
    }

    /**
     * レスポンス通知。
     * UIスレッド上で呼び出される。
     * @param response レスポンス
     * @param status ステータスコード
     */
    @Override
    public void handleResponse(Response response, int status) {
        log.fine(mLogPrefix + " handleResponse() called status=" + status);

        if (NbStatus.isSuccessful(status)) {
            onSuccess(response.body().byteStream(), response);
        } else {
            onFailure(status, response);
        }

    }

    /**
     * 成功時に呼び出される。
     * @param inputStream レスポンスボディ
     * @param response レスポンス
     */
    public void onSuccess(InputStream inputStream, Response response) {
        throw new IllegalStateException("You must override one of onSuccess().");
    }

    /**
     * 失敗時に呼び出される。notifyFailure()を呼び出す。
     * @param statusCode ステータスコード
     * @param response Response
     */
    public void onFailure(int statusCode, Response response) {
        if (response != null) {
            NbJSONObject json = NbUtil.restoreResponse(response);
            if (json != null) {
                notifyFailure(statusCode, String.valueOf(json));
            } else {
                notifyFailure(statusCode, response.message());
            }
        } else {
            notifyFailure(statusCode, Integer.toString(statusCode));
        }
    }

    /**
     * 失敗を通知する。
     * @param statusCode ステータスコード
     * @param reason 理由
     */
    private void notifyFailure(int statusCode, String reason) {
        mCallback.onFailure(statusCode, new NbErrorInfo(reason));
    }
}
