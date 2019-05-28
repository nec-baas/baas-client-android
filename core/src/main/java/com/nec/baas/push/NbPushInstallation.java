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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Pushインスタレーション基底クラス。<p/>
 * @since 4.0.0
 */
public abstract class NbPushInstallation {
    protected static final NbLogger log = NbLogger.getLogger(NbPushInstallation.class);

    // OS種別
    /**
     * -- GETTER --
     * OS種別を取得する。
     */
    @Getter(AccessLevel.PUBLIC) @Setter(AccessLevel.PROTECTED)
    protected String osType = null;

    // OSバージョン
    /**
     * -- GETTER --
     * OSバージョンを取得する。
     */
    @Getter(AccessLevel.PUBLIC) @Setter(AccessLevel.PROTECTED)
    protected String osVersion = null;

    // Device Token
    protected String deviceToken = null;

    // 使用する Push テクノロジ
    /**
     * -- GETTER --
     * 使用する Push テクノロジを取得する。
     */
    @Getter(AccessLevel.PUBLIC) @Setter(AccessLevel.PROTECTED)
    protected String pushType = null;

    // アプリケーションのバージョンコード
    /**
     * -- GETTER --
     * アプリケーションのバージョンコードを取得する。
     */
    @Getter(AccessLevel.PUBLIC) @Setter(AccessLevel.PROTECTED)
    protected Integer appVersionCode = null;

    // アプリケーションのバージョン
    /**
     * -- GETTER --
     * アプリケーションのバージョンを取得する。
     */
    @Getter(AccessLevel.PUBLIC) @Setter(AccessLevel.PROTECTED)
    protected String appVersionString = null;

    // オーナー情報
    /**
     * -- GETTER --
     * オーナー情報を取得する。
     */
    @Getter(AccessLevel.PUBLIC) @Setter(AccessLevel.PROTECTED)
    protected String owner = null;

    protected Set<String> mChannels = null;
    protected Set<String> mAllowedSenders = null;
    protected NbJSONObject mOptions = null;
    protected String mInstallationId = null;

    protected NbServiceImpl mNebulaService;

    // Key
    protected static final String KEY_OS_TYPE = "_osType";
    protected static final String KEY_OS_VERSION = "_osVersion";
    protected static final String KEY_DEVICE_TOKEN = "_deviceToken";
    protected static final String KEY_PUSH_TYPE = "_pushType";
    protected static final String KEY_CHANNELS = "_channels";
    protected static final String KEY_APP_VERSION_CODE = "_appVersionCode";
    protected static final String KEY_APP_VERSION_STRING = "_appVersionString";
    protected static final String KEY_ALLOWED_SENDERS = "_allowedSenders";
    // installationId用のKeyはNbKey.IDを使用
    protected static final String KEY_OWNER = "_owner";
    protected static final String KEY_OPTIONS = "options";    // RESTには出さないKey
    private static final String KEY_FULL_UPDATE = "$full_update";

    // URL
    protected static final String PUSH_INSTALLATIONS_URL = "/push/installations";

    // lombok で処理すべきだが、以下問題のため個別記述
    // see. https://code.google.com/p/android/issues/detail?id=77902
    /**
     * Device Tokenを取得する。<br>
     * @return DeviceToken
     */
    public String getDeviceToken() {
        return deviceToken;
    }
    /**
     * Device Tokenを設定する。<br>
     * @param deviceToken DeviceToken
     */
    protected void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }

    /**
     * 購読するチャネルの一覧を設定する。<p/>
     *
     * 初期値はnull、nullオブジェクトは要求時のデータには含まれない。
     * @param channels 購読するチャネルの一覧
     */
    public void setChannels(Set<String> channels) {
        mChannels = channels;
    }

    /**
     * 購読するチャネルの一覧を取得する。<br>
     * @return 購読するチャネルの一覧
     */
    public Set<String> getChannels() {
        return mChannels;
    }

    /**
     * このインスタレーションに対して Push を送信可能なユーザ・グループを設定する。<p/>
     *
     * 初期値はnull、nullオブジェクトは要求時のデータには含まれない。
     * @param allowedSenders 送信可能なユーザ・グループ
     */
    public void setAllowedSenders(Set<String> allowedSenders) {
        mAllowedSenders = allowedSenders;
    }

    /**
     * このインスタレーションに対して Push を送信可能なユーザ・グループを取得する。<br>
     * @return 送信可能なユーザ・グループ
     */
    public Set<String> getAllowedSenders() {
        return mAllowedSenders;
    }

    /**
     * 任意のKey-Valueを設定する。<p/>
     *
     * 初期値はnull、nullオブジェクトは要求時のデータには含まれない。
     * @param options 任意のKey-Value
     */
    public void setOptions(NbJSONObject options) {
        mOptions = options;
    }

    /**
     * 任意のKey-Valueを取得する。<br>
     * @return 任意のKey-Value
     */
    public NbJSONObject getOptions() {
        return mOptions;
    }

    /**
     * インスタレーションIDを取得する。<br>
     * @return インスタレーションID
     */
    public String getInstallationId() {
        return mInstallationId;
    }

    /**
     * コンストラクタ
     */
    protected NbPushInstallation() {
    }

    /**
     * save用リクエストを生成する。<br>
     * @param recoveryFlag trueの場合、インスタレーションIDの存在有無に関係なく、新規登録となる
     * @return 生成したHttpUriRequest
     */
    protected Request makeRequestForSave(boolean recoveryFlag) {
        Request request;

        // JSON 生成
        NbJSONObject bodyJson = new NbJSONObject();
        setJsonToBody(bodyJson, true);

        // リクエスト生成
        if (!recoveryFlag && (getInstallationId() != null)) {
            // 完全上書き更新の場合
            NbJSONObject fullUpdateJson = new NbJSONObject();
            fullUpdateJson.put(KEY_FULL_UPDATE, bodyJson);
            request = mNebulaService.getHttpRequestFactory().put(PUSH_INSTALLATIONS_URL).addPathComponent(getInstallationId()).body(fullUpdateJson).build();
        } else {
            // 新規登録の場合
            request = mNebulaService.getHttpRequestFactory().post(PUSH_INSTALLATIONS_URL).body(bodyJson).build();
        }

        return request;
    }

    /**
     * partUpdate用リクエストを生成する。<br>
     * @param data 部分更新するデータ
     * @return 生成したHttpUriRequest
     */
    protected Request makeRequestForPartUpdate(final NbJSONObject data) {
        // JSON 生成
        NbJSONObject bodyJson = new NbJSONObject();
        bodyJson.putAll(data);
        setJsonToBody(bodyJson, false);

        // リクエスト生成
        Request request = mNebulaService.getHttpRequestFactory().put(PUSH_INSTALLATIONS_URL).addPathComponent(getInstallationId()).body(bodyJson).build();

        return request;
    }

    /**
     * refresh用リクエストを生成する。<br>
     * @return 生成したHttpUriRequest
     */
    protected Request makeRequestForRefresh() {
        // リクエスト生成
        Request request = mNebulaService.getHttpRequestFactory().get(PUSH_INSTALLATIONS_URL).addPathComponent(getInstallationId()).build();

        return request;
    }

    /**
     * delete用リクエストを生成する。<br>
     * @return 生成したHttpUriRequest
     */
    protected Request makeRequestForDelete() {
        // リクエスト生成
        Request request = mNebulaService.getHttpRequestFactory().delete(PUSH_INSTALLATIONS_URL).addPathComponent(getInstallationId()).build();

        return request;
    }

    /**
     * リクエストを送信する。<br>
     * @param request Request
     * @param handler NbRestResponseHandler
     */
    protected void executeRequest(Request request, NbRestResponseHandler handler) {
        // リクエスト送信
        mNebulaService.getRestExecutorFactory().create().executeRequest(request, handler);
    }

    /**
     * インスタレーションを削除する。<br>
     * @param callback 実行結果を受け取るコールバック
     * @see com.nec.baas.core.NbResultCallback
     */
    public void deleteInstallation(@NonNull final NbResultCallback callback) {
        // 他クラスの挙動に合わせる
        if (getInstallationId() == null) {
            log.severe("deleteInstallation() mInstallationId == null");
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("Null installationId."));
            return;
        }

        // リクエスト生成
        Request request = makeRequestForDelete();

        // リクエスト送信
        executeRequest(request, new NbSimpleRestResponseHandler(callback, "NbPushInstallation.deleteInstallation()") {
            @Override
            public void onSuccess(Response response) {
                // キャッシュを削除
                deleteAndLoad();
                callback.onSuccess();
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
     * リクエストボディにJSON(インスタレーション情報)を設定する。<br>
     * @param body リクエストボディ
     * @param flag 新規登録/完全上書き更新の場合はtrue, 部分更新の場合はfalse
     */
    protected void setJsonToBody(NbJSONObject body, boolean flag) {
        if (body == null) {
            return;
        }

        if (flag) {
            // アプリ設定可能分
            if (this.mOptions != null) {
                for (Map.Entry<String,Object> entry : mOptions.entrySet()) {
                    body.put(entry.getKey(), entry.getValue());
                }
            }
            if (this.mChannels != null) {
                body.put(KEY_CHANNELS, this.mChannels);
            }
            if (this.mAllowedSenders != null) {
                body.put(KEY_ALLOWED_SENDERS, this.mAllowedSenders);
            }

        }

        // 以降は、新規登録/完全上書き更新の場合は設定する
        // 部分更新の場合はキャッシュと差分があれば、設定する
        String osType = _getOsType();
        if (flag || !(osType.equals(getStringFromPreferences(KEY_OS_TYPE)))) {
            body.put(KEY_OS_TYPE, osType);
        }

        String version = _getOsVersion();
        if (flag || !(version.equals(getStringFromPreferences(KEY_OS_VERSION)))) {
            body.put(KEY_OS_VERSION, version);
        }

        if (this.deviceToken != null) {
            if (flag || !(this.deviceToken.equals(getStringFromPreferences(KEY_DEVICE_TOKEN)))) {
                body.put(KEY_DEVICE_TOKEN, this.deviceToken);
            }
        }
        if (flag || checkAppVersionCode()) {
            body.put(KEY_APP_VERSION_CODE, getVersionCode());
        }
        String versionString = getVersionName();
        if (versionString != null) {
            if (flag || !(versionString.equals(getStringFromPreferences(KEY_APP_VERSION_STRING)))) {
                body.put(KEY_APP_VERSION_STRING, getVersionName());
            }
        }

        // pushTypeはサブクラスで設定する
    }

    /**
     * OSタイプ取得
     * @return
     */
    protected abstract String _getOsType();

    /**
     * OSバージョン取得
     * @return
     */
    protected abstract String _getOsVersion();

    /**
     * アプリバージョンコード取得
     * @return
     */
    protected abstract int getVersionCode();

    /**
     * アプリバージョン名取得
     * @return
     */
    protected abstract String getVersionName();

    /**
     * アプリケーションのバージョンコードをチェックする。<br>
     * キャッシュに保存されている値と現在値が差分があれば、trueを返す。
     * @return チェック結果
     */
    protected boolean checkAppVersionCode() {
        String registeredVersionString = getStringFromPreferences(KEY_APP_VERSION_CODE);
        int registeredVersion = Integer.MIN_VALUE;
        if (registeredVersionString != null) {
            registeredVersion = Integer.parseInt(registeredVersionString);
        }
        int currentVersion = getVersionCode();
        return registeredVersion != currentVersion;
    }

    /**
     * プリファレンスを取得する。
     * @return
     */
    protected abstract NbPreferences getSharedPreferences();

    /**
     * SharedPreferenceからインスタレーション情報をロードする。<br>
     */
    protected final void loadFromPreferences() {
        NbPreferences prefs = getSharedPreferences();
        loadFromPreferences(prefs);
    }

    /**
     * SharedPreferenceからインスタレーション情報をロードする。<br>
     * サブクラスで項目追加可。<br>
     * @param prefs SharedPreference
     */
    protected void loadFromPreferences(NbPreferences prefs) {
        this.osType = prefs.getString(KEY_OS_TYPE, null);
        this.osVersion = prefs.getString(KEY_OS_VERSION, null);
        this.deviceToken = prefs.getString(KEY_DEVICE_TOKEN, null);
        this.pushType = prefs.getString(KEY_PUSH_TYPE, null);
        this.mChannels = prefs.getStringSet(KEY_CHANNELS, null);
        this.appVersionCode = null;
        String appVersionCodeString = prefs.getString(KEY_APP_VERSION_CODE, null);
        if (appVersionCodeString != null) {
            this.appVersionCode = Integer.valueOf(appVersionCodeString);
        }
        this.appVersionString = prefs.getString(KEY_APP_VERSION_STRING, null);
        this.mAllowedSenders = prefs.getStringSet(KEY_ALLOWED_SENDERS, null);
        this.owner = prefs.getString(KEY_OWNER, null);
        this.mInstallationId = prefs.getString(NbKey.ID, null);
        this.mOptions = null;
        String optionsString = prefs.getString(KEY_OPTIONS, null);
        if (optionsString != null) {
            this.mOptions = NbJSONParser.parse(optionsString);
        }
    }

    /**
     * SharedPreferenceにkey-value形式(valueはstring)で保存する。<br>
     * @param key key
     * @param value value
     */
    protected final void putStringToPreferences(String key, String value) {
        getSharedPreferences().putString(key, value).apply();
    }

    /**
     * SharedPreferenceからstringを読み出す。<br>
     * @param key key
     * @return 読み出した値
     */
    protected final String getStringFromPreferences(String key) {
        return getSharedPreferences().getString(key, null);
    }

    /**
     * SharedPreferenceにJSON情報を保存する。<br>
     * @param json JSON情報
     */
    protected void saveJsonToPreferences(NbJSONObject json) {
        if (json == null) return;

        // SharedPreferenceを削除してから、保存する
        deletePreferences();

        NbPreferences prefs = getSharedPreferences();
        NbJSONObject option = new NbJSONObject();

        for (Map.Entry<String,Object> entry : json.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            saveJsonToPreferences(key, value, prefs, option);
        }
        if (option.size() > 0) {
            // SharedPreferenceにそのまま保存できないので、stringに変換した後に保存する
            String optionString = option.toJSONString();
            prefs.putString(KEY_OPTIONS, optionString);
        }

        prefs.apply();
    }

    /**
     * SharedPreferenceにJSON情報を保存する。<br>
     * サブクラスで項目追加可。
     * @param key JSON key
     * @param value JSON value
     * @param prefs NbPreferences
     * @param option 任意のkey-valueを設定
     */
    protected void saveJsonToPreferences(String key, Object value, NbPreferences prefs, NbJSONObject option) {
        switch (key) {
            case KEY_OS_TYPE:
            case KEY_OS_VERSION:
            case KEY_DEVICE_TOKEN:
            case KEY_PUSH_TYPE:
            case KEY_APP_VERSION_STRING:
            case NbKey.ID:
            case KEY_OWNER:
                prefs.putString(key, (String)value);
                break;

            case KEY_APP_VERSION_CODE:
                // appVersionCodeも参照型として扱いたいため、Stringとして保存する
                String appVersionCodeString = String.valueOf((int)value);
                prefs.putString(key, appVersionCodeString);
                break;

            case KEY_CHANNELS:
            case KEY_ALLOWED_SENDERS:
                // JsonManagerで配列はArrayList型にデシリアライズされるため、
                // 変換を行う
                ArrayList<String> valueList = (ArrayList<String>)value;
                Set<String> valueSet = new HashSet<>(valueList);
                prefs.putStringSet(key, valueSet);
                break;

            default:
                option.put(key, value);
                break;
        }
    }

    /**
     * SharedPreferenceを削除する。<br>
     */
    protected void deletePreferences() {
        getSharedPreferences().clear().apply();
    }

    /**
     * SharedPreferenceにJSON情報を保存した後で、インスタレーション情報をロードする。<br>
     * @param json JSON情報
     */
    protected void saveAndLoad(NbJSONObject json) {
        saveJsonToPreferences(json);
        loadFromPreferences();
    }

    /**
     * SharedPreferenceを削除した後で、インスタレーション情報をロードする。<br>
     */
    protected void deleteAndLoad() {
        deletePreferences();
        loadFromPreferences();
    }
}
