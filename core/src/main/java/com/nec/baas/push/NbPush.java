/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.http.*;
import com.nec.baas.json.*;
import com.nec.baas.object.*;
import com.nec.baas.util.*;

import java.util.Set;

import lombok.NonNull;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Pushメッセージ送信クラス。
 *
 * <p>Pushメッセージ送信を行う。</p>
 *
 * <p><strong>本クラスのインスタンスはスレッドセーフではない。</strong></p>
 * @since 1.0
 */
public class NbPush {
    private static final NbLogger log = NbLogger.getLogger(NbPush.class);

    /*package*/ NbClause mClause = null;
    /*package*/ String mMessage = null;
    /*package*/ Set<String> mAllowedReceivers = null;
    /*package*/ NbAPNSFields mAPNSFields = null;
    /*package*/ NbGCMFields mGCMFields = null;
    /*package*/ NbSseFields mSseFields = null;
    /*package*/ NbServiceImpl mNebulaService;

    // Key
    private static final String KEY_QUERY = "query";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_ALLOWED_RECEIVERS = "allowedReceivers";

    // URL
    private static final String PUSH_NOTIFICATIONS_URL = "/push/notifications";

    /**
     * コンストラクタ(マルチテナント非対応)
     * @throws java.lang.IllegalStateException
     */
    public NbPush() {
        this(NbService.getInstance());
    }

    /**
     * コンストラクタ(マルチテナント対応)
     * @param service サービス
     * @throws java.lang.IllegalStateException
     */
    public NbPush(NbService service) {
        mNebulaService = (NbServiceImpl)service;
    }

    /**
     * 送信先インスタレーションを指定するためのクエリを設定する。<br>
     * @param clause 送信先インスタレーションを指定するためのクエリ。Clauseにて生成する。
     * @see com.nec.baas.object.NbClause
     */
    public void setClause(NbClause clause) {
        mClause = clause;
    }

    /**
     * Pushメッセージ本文を設定する。<br>
     * @param message Pushメッセージ本文。
     */
    public void setMessage(String message) {
        mMessage = message;
    }

    /**
     * 通知を受信可能なユーザ・グループの一覧を設定する。<br>
     * @param allowedReceivers 通知を受信可能なユーザ・グループの一覧。
     */
    public void setAllowedReceivers(Set<String> allowedReceivers) {
        mAllowedReceivers = allowedReceivers;
    }

    /**
     * iOS 固有値を設定する。<br>
     * @param fields APNSFieldsのインスタンス
     * @see com.nec.baas.push.NbAPNSFields
     */
    public void setAPNSFields(NbAPNSFields fields) {
        mAPNSFields = fields;
    }

    /**
     * Android 固有値を設定する。<br>
     * @param fields GCMFieldsのインスタンス
     * @see com.nec.baas.push.NbGCMFields
     */
    public void setGCMFields(NbGCMFields fields) {
        mGCMFields = fields;
    }

    /**
     * SSE 固有値を設定する。<br>
     * @param fields SseFieldsのインスタンス
     * @see com.nec.baas.push.NbSseFields
     */
    public void setSseFields(NbSseFields fields) {
        mSseFields = fields;
    }

    /**
     * Pushメッセージを送信する。<br>
     * @param callback Push送信結果を受け取るコールバック。
     * @see com.nec.baas.push.NbPushCallback
     */
    public void sendPush(@NonNull final NbPushCallback callback) {

        // リクエストボディ部作成
        final NbJSONObject bodyJson = new NbJSONObject();
        if (mClause != null && mClause.getConditions() != null) {
            bodyJson.put(KEY_QUERY, mClause.getConditions());
        }
        if (mMessage != null) {
            bodyJson.put(KEY_MESSAGE, mMessage);
        }
        if (mAllowedReceivers != null) {
            bodyJson.put(KEY_ALLOWED_RECEIVERS, mAllowedReceivers);
        }
        if (mAPNSFields != null && mAPNSFields.getFields() != null) {
            bodyJson.putAll(mAPNSFields.getFields());
        }
        if (mGCMFields != null && mGCMFields.getFields() != null) {
            bodyJson.putAll(mGCMFields.getFields());
        }
        if (mSseFields != null && mSseFields.getFields() != null) {
            bodyJson.putAll(mSseFields.getFields());
        }

        // リクエスト生成
        NbHttpRequestFactory requestFactory = mNebulaService.getHttpRequestFactory();
        Request request = requestFactory.post(PUSH_NOTIFICATIONS_URL).body(bodyJson).build();

        // リクエスト送信
        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbPush.sendPush()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                callback.onSuccess(body);
            }
        };
        mNebulaService.getRestExecutorFactory().create().executeRequest(request, handler);
    }
}
