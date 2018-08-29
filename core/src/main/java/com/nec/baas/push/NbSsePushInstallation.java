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
import com.nec.baas.util.*;

import java.util.UUID;

import lombok.NonNull;
import okhttp3.Request;
import okhttp3.Response;

/**
 * SSE Pushインスタレーションクラス。<p/>
 *
 * SSE Pushのインスタレーション登録/更新/削除/取得を行う。
 * <p/>
 * @since 4.0.0
 */
public class NbSsePushInstallation extends NbPushInstallation {
    private static final NbLogger log = NbLogger.getLogger(NbSsePushInstallation.class);

    /*package*/ String mUserName = null;
    /*package*/ String mPassword = null;
    /*package*/ String mUri = null;

    /*package*/ static NbSsePushInstallation sInstance;

    // Key
    private static final String KEY_SSE = "_sse";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_URI = "uri";

    private static final String CLASS_NAME_FOR_ANDROID = "com.nec.baas.push.internal.NbAndroidSsePushInstallation";
    private static final String PUSH_TYPE_SSE = "sse";
    private static final String OS_TYPE_JAVA = "java";
    private static final String VERSION_UNKNOWN = "Unknown";

    protected static final String SSE_PREFERENCE_NAME = "SseInstallationPrefs";
    protected static final String SSE_PREFERENCE_UUID = "SseDeviceTokenPrefs";

    private static final String CALLBACK_PARAMETER_NULL = "callback is null.";
    private static final String INATALLATION_ID_NULL = "Null installationId.";

    /**
     * SSE Push　サーバと認証する際のユーザ名を取得する。<br>
     * @return ユーザ名
     */
    public String getUserName() {
        return mUserName;
    }

    /**
     * SSE Push　サーバと認証する際のパスワードを取得する。<br>
     * @return パスワード
     */
    public String getPassword() {
        return mPassword;
    }

    /**
     * SSE Push　サーバのURI 取得する。<br>
     * @return URI
     */
    public String getUri()
    {
        return mUri;
    }

    /**
     * コンストラクタ
     */
    protected NbSsePushInstallation() {
        super();
        mNebulaService = (NbServiceImpl)NbService.getInstance();
    }

    /**
     * 現在のSSE Pushインスタレーション情報を取得する。
     * @return インスタレーション情報
     * @throws java.lang.IllegalStateException
     */
    public static synchronized NbSsePushInstallation getCurrentInstallation() {
        if (sInstance == null) {
            try {
                Class<?> c = Class.forName(CLASS_NAME_FOR_ANDROID);
                sInstance = (NbSsePushInstallation)c.newInstance();
            } catch (Exception e) {
                sInstance = new NbSsePushInstallation();
            }
        }
        // キャッシュに保存されている情報がマスターとなる
        sInstance.loadFromPreferences();
        return sInstance;
    }

    /**
     * インスタレーションの新規登録/完全上書き更新を行う。<p/>
     *
     * インスタレーション情報を更新する前にacquire()にて更新権利の取得を行う必要がある。<br>
     * @param callback 登録したインスタレーション情報を受け取るコールバック
     * @see com.nec.baas.push.NbSsePushReceiveClient#acquireLock()
     * @see com.nec.baas.push.NbSsePushInstallationCallback
     */
    public void save(@NonNull final NbSsePushInstallationCallback callback) {

        // deviceTokenを設定
        if (getDeviceToken() == null) {
            setDeviceToken(createDeviceToken());
        }

        // リクエスト生成
        Request request = makeRequestForSave(false);

        // リクエスト送信
        executeRequest(request, new NbSimpleRestResponseHandler(callback, "NbSsePushInstallation.save()"){
            @Override
            public void onSuccess(Response response, NbJSONObject json) {
                // キャッシュに保存
                saveAndLoad(json);
                callback.onSuccess(NbSsePushInstallation.this);
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
     * インスタレーション情報をサーバから取得する。<br>
     * @param callback 取得したインスタレーション情報を受け取るコールバック
     * @see com.nec.baas.push.NbSsePushInstallationCallback
     */
    public static void refreshCurrentInstallation(@NonNull final NbSsePushInstallationCallback callback) {

        final NbSsePushInstallation installation = NbSsePushInstallation.getCurrentInstallation();

        // 他クラスの挙動に合わせる
        if (installation.getInstallationId() == null) {
            log.severe("refreshCurrentInstallation() ERR mInstallationId == null");
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo(INATALLATION_ID_NULL));
            return;
        }

        // リクエスト生成
        Request request = installation.makeRequestForRefresh();

        // リクエスト送信
        installation.executeRequest(request, new NbSimpleRestResponseHandler(callback, "NbSsePushInstallation.refreshCurrentInstallation()") {
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

    @Override
    protected String _getOsType() {
        return OS_TYPE_JAVA;
    }

    @Override
    protected String _getOsVersion() {
        return VERSION_UNKNOWN;
    }

    @Override
    protected int getVersionCode() {
        return -1;
    }

    @Override
    protected String getVersionName() {
        return VERSION_UNKNOWN;
    }

    @Override
    protected void setJsonToBody(NbJSONObject body, boolean flag) {
        super.setJsonToBody(body, flag);

        // pushTypeを設定
        if (flag || !(PUSH_TYPE_SSE.equals(getStringFromPreferences(KEY_PUSH_TYPE)))) {
            body.put(KEY_PUSH_TYPE, PUSH_TYPE_SSE);
        }
    }

    @Override
    protected NbPreferences getSharedPreferences() {
        return new NbGenericPreferences(SSE_PREFERENCE_NAME);
    }

    protected NbPreferences getSharedPreferences(String prefsName) {
        return new NbGenericPreferences(prefsName);
    }

    @Override
    protected void loadFromPreferences(NbPreferences prefs) {
        super.loadFromPreferences(prefs);

        this.mUserName = prefs.getString(KEY_USERNAME, null);
        this.mPassword = prefs.getString(KEY_PASSWORD, null);
        this.mUri = prefs.getString(KEY_URI, null);
    }

    @Override
    protected void saveJsonToPreferences(String key, Object value, NbPreferences prefs, NbJSONObject option) {
        switch (key) {
            case KEY_SSE:
                prefs.putString(KEY_USERNAME, (String)((NbJSONObject)value).get(KEY_USERNAME));
                prefs.putString(KEY_PASSWORD, (String)((NbJSONObject)value).get(KEY_PASSWORD));
                prefs.putString(KEY_URI, (String)((NbJSONObject)value).get(KEY_URI));
                break;
            default:
                super.saveJsonToPreferences(key, value, prefs, option);
                break;
        }
    }

    /**
     * デバイストークン(UUID)を生成する。
     * @return デバイストークン(UUID)
     */
    protected String createDeviceToken() {
        NbPreferences prefs = getSharedPreferences(SSE_PREFERENCE_UUID);
        String returnString = prefs.getString(KEY_DEVICE_TOKEN, null);
        if (returnString == null || returnString.isEmpty()) {
            returnString = UUID.randomUUID().toString();
            prefs.putString(KEY_DEVICE_TOKEN, returnString).apply();
        }
        return returnString;
    }
}
