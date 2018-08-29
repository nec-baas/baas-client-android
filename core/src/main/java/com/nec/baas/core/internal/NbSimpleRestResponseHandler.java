/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import com.nec.baas.core.*;
import com.nec.baas.json.*;
import com.nec.baas.util.*;

import lombok.Setter;
import lombok.experimental.Accessors;
import okhttp3.Response;

/**
 * NbRestResponseHandler 簡略版。
 * Response が JSON の場合のみ対応。
 * いずれか１つの onSuccess のみをオーバライドすればよい。
 * 失敗時は自動的に callback.onFailure() を呼び出す。
 */
@Accessors(prefix = "m")
public abstract class NbSimpleRestResponseHandler implements NbPreRestResponseHandler {
    private static final NbLogger log = NbLogger.getLogger(NbSimpleRestResponseHandler.class);

    private final NbBaseCallback mCallback;
    private final String mLogPrefix;

    @Setter
    volatile private NbJSONObject mJson = null;

    /**
     * コンストラクタ
     * @param callback コールバック
     */
    public NbSimpleRestResponseHandler(NbBaseCallback callback) {
        this(callback, "NbSimpleRestResponseHandler");
    }

    /**
     * コンストラクタ
     * @param callback コールバック
     * @param logPrefix ログ表示用プレフィクス
     */
    public NbSimpleRestResponseHandler(NbBaseCallback callback, String logPrefix) {
        mCallback = callback;
        mLogPrefix = logPrefix;
    }

    /**
     * 事前ハンドルレスポンス通知。
     * サブスレッド上で呼び出される。（呼び出し先へダウンロード処理など処理負荷の高い処理を行う）
     * @param response レスポンス
     * @return ステータスコード
     */
    @Override
    public int preHandleResponse(Response response) {
        int status = response.code();
        mJson = NbUtil.restoreResponse(response);

        if (NbStatus.isSuccessful(status)) {
            status = preHandleResponse(mJson);
        }

        log.fine(mLogPrefix + " preHandleResponse() call status=" + status);
        return status;
    }

    /**
     * 事前ハンドルレスポンス通知。200系ステータスコードのときにだけ呼び出される。
     * サブクラスで適宜オーバライドする。
     * @param json JSON Object
     * @return ステータスコード
     */
    public int preHandleResponse(NbJSONObject json) {
        return NbStatus.OK;
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
            onSuccess(response);
        } else {
            onFailure(status, response);
        }
        mJson = null;   //通知が済んだら解放
    }

    /**
     * 成功時に呼び出される。
     * デフォルトの実装では、JSON をパースして onSuccess(Map<String,Object>) を呼び出す。
     * @param response Response
     */
    public void onSuccess(Response response) {
        //レスポンス復元処理
        final NbJSONObject json = mJson;

        if (json == null) {
            log.warning(mLogPrefix + " ERROR: body == null");
            mCallback.onFailure(NbStatus.UNPROCESSABLE_ENTITY_ERROR, new NbErrorInfo("Null body."));
        } else {
            onSuccess(response, json);
        }
    }

    /**
     * 成功時に呼び出される。パースされたJSONが渡される。
     * @param response Response
     * @param json JSONデータ
     */
    public void onSuccess(Response response, NbJSONObject json) {
        throw new IllegalStateException("You must override one of onSuccess().");
    }

    /**
     * 失敗時に呼び出される。onFailure(int statusCode, , Map<String,Object> json)を呼び出す。
     * @param statusCode ステータスコード
     * @param response Response
     */
    public void onFailure(int statusCode, Response response) {
        if (mJson != null) {
            onFailure(statusCode, mJson);
        } else if (response != null) {
            notifyFailure(statusCode, response.message());
        } else {
            notifyFailure(statusCode, Integer.toString(statusCode));
        }
    }

    /**
     * 失敗時に呼び出される。パースされたJSONが渡される。
     * デフォルトの実装では、callback.onFailure() を呼び出す。
     * @param statusCode ステータスコード
     */
    public void onFailure(int statusCode, NbJSONObject json) {
        notifyFailure(statusCode, String.valueOf(json));
    }

    private void notifyFailure(int statusCode, String reason) {
        mCallback.onFailure(statusCode, new NbErrorInfo(reason));
    }
}
