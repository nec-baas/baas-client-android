/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.json.*;
import com.nec.baas.push.internal.*;

import lombok.NonNull;
import okhttp3.Request;
import okhttp3.Response;

/**
 * FCM Pushインスタレーションクラス。
 *
 * <p>FCM Pushのインスタレーション登録/更新/削除/取得を行う。</p>
 *
 * <p><strong>最初に getCurrentInstallation() を呼び出した時点で、最後に作成されていた NbService のインスタンスが利用される。</strong></p>
 * @since 6.0.0
 */
public class NbFcmPushInstallation extends NbAndroidPushInstallation {
    //private static NbLogger log = NbLogger.getLogger(NbFcmPushInstallation.class);

    /*package*/ static NbFcmPushInstallation sInstance;

    /**
     * 現在の FCM Pushインスタレーション情報を取得する。
     *
     * @return インスタレーション情報
     * @throws java.lang.IllegalStateException
     */
    public static synchronized NbFcmPushInstallation getCurrentInstallation() {
        if (sInstance == null) {
            sInstance = new NbFcmPushInstallation();
        }
        // キャッシュに保存されている情報がマスターとなる
        sInstance.loadFromPreferences();
        return sInstance;
    }

    protected NbFcmPushInstallation() {
        super();
    }

    /*package*/ static void __clearInstance() {
        sInstance = null;
    }

    /**
     * Registration Token を保存する (FCM / GCM3.0以降用)。<p>
     * InstanceIDListenerService で registration ID / token を受取り
     * 本 API で保存すること。
     * @param registrationToken Registration Token
     */
    public void saveRegistrationToken(String registrationToken) {
        setDeviceToken(registrationToken);
        putStringToPreferences(KEY_DEVICE_TOKEN, registrationToken);
    }

    /**
     * APIサーバに対してインスタレーションの新規登録/完全上書き更新を行う。<p>
     *
     * 事前にFCM/GCMへの登録が完了している必要がある。<br>
     * 事前に購読するチャネルの一覧とインスタレーションに対して Push を送信可能なユーザ・グループを設定する必要がある。<br>
     *
     * @param callback 登録したインスタレーション情報を受け取るコールバック
     * @see NbAndroidPushInstallation#setChannels(java.util.Set)
     * @see NbAndroidPushInstallation#setAllowedSenders(java.util.Set)
     * @see NbFcmPushInstallationCallback
     */
    public void save(@NonNull final NbFcmPushInstallationCallback callback) {

        // 基底クラスで行った方がベターだが、SDE側に影響があるため、ここでチェックする
        // アプリが設定可能な必須パラメータ(channels,allowedSenders)のnullチェック
        if (getChannels() == null || getAllowedSenders() == null) {
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("Null channels or null allowedSenders."));
            return;
        }

        // deviceTokenはアプリが設定する訳ではないので、上とはチェックを分ける
        if (getDeviceToken() == null) {
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("Null deviceToken. call register()."));
            return;
        }

        // リクエスト生成
        Request request = makeRequestForSave(false);

        // リクエスト送信
        executeRequest(request, new NbSimpleRestResponseHandler(callback, "NbFcmPushInstallation.save()") {
            @Override
            public void onSuccess(Response response, NbJSONObject json) {
                // キャッシュに保存
                saveAndLoad(json);
                callback.onSuccess(NbFcmPushInstallation.this);
            }
            @Override
            public void onFailure(int statusCode, NbJSONObject json) {
                if (statusCode == NbStatus.NOT_FOUND) {
                    // キャッシュを削除
                    deleteAndLoad();
                }
                callback.onFailure(statusCode, new NbErrorInfo(String.valueOf(json)));
            }
        });
    }

    /**
     * APIサーバに対してインスタレーションの部分更新を行う。<br>
     *
     * @param data 部分更新するデータ
     * @param callback 登録したインスタレーション情報を受け取るコールバック
     * @see NbFcmPushInstallationCallback
     */
    @Deprecated
    public static void partUpdateInstallation(@NonNull final NbJSONObject data, @NonNull final NbFcmPushInstallationCallback callback) {

        final NbFcmPushInstallation installation = NbFcmPushInstallation.getCurrentInstallation();

        // 他クラスの挙動に合わせる
        if (installation.getInstallationId() == null) {
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("Null installationId."));
            return;
        }

        // リクエスト生成
        Request request = installation.makeRequestForPartUpdate(data);

        // リクエスト送信
        installation.executeRequest(request, new NbSimpleRestResponseHandler(callback, "NbFcmPushInstallation.partUpdateInstallation()") {
            @Override
            public void onSuccess(Response response, NbJSONObject json) {
                // キャッシュに保存
                installation.saveAndLoad(json);
                callback.onSuccess(installation);
            }
            @Override
            public void onFailure(int statusCode, NbJSONObject json) {
                if (statusCode == NbStatus.NOT_FOUND) {
                    // キャッシュを削除
                    installation.deleteAndLoad();
                }
                callback.onFailure(statusCode, new NbErrorInfo(String.valueOf(json)));
            }
        });
    }

    /**
     * インスタレーション情報をAPIサーバから取得する。<br>
     *
     * @param callback 取得したインスタレーション情報を受け取るコールバック
     * @see NbFcmPushInstallationCallback
     */
    public static void refreshCurrentInstallation(@NonNull final NbFcmPushInstallationCallback callback) {

        final NbFcmPushInstallation installation = NbFcmPushInstallation.getCurrentInstallation();

        // 他クラスの挙動に合わせる
        if (installation.getInstallationId() == null) {
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("Null installationId."));
            return;
        }

        // リクエスト生成
        Request request = installation.makeRequestForRefresh();

        // リクエスト送信
        installation.executeRequest(request, new NbSimpleRestResponseHandler(callback, "NbFcmPushInstallation.refreshCurrentInstallation()") {
            @Override
            public void onSuccess(Response response, NbJSONObject json) {
                // キャッシュに保存
                installation.saveAndLoad(json);
                callback.onSuccess(installation);
            }
            @Override
            public void onFailure(int statusCode, NbJSONObject json) {
                if (statusCode == NbStatus.NOT_FOUND) {
                    // キャッシュを削除
                    installation.deleteAndLoad();
                }
                callback.onFailure(statusCode, new NbErrorInfo(String.valueOf(json)));

            }
        });
    }

    /**
     * FCM/GCMへの登録状態の確認を行う。<br>
     *
     * @return FCM/GCMに登録済の場合はtrue、未登録の場合はfalseを返す
     */
    public boolean isRegistered() {
        return getDeviceToken() != null;
    }

    @Override
    protected void setJsonToBody(NbJSONObject body, boolean flag) {
        super.setJsonToBody(body, flag);

        // pushTypeを設定
        if (flag || !("gcm".equals(getStringFromPreferences(KEY_PUSH_TYPE)))) {
            body.put(KEY_PUSH_TYPE, "gcm");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected NbPreferences getSharedPreferences() {
        return new NbAndroidPreferences(mContext, "FcmInstallationPrefs");
    }
}
