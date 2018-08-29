/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.http.*;
import com.nec.baas.json.*;
import com.nec.baas.object.internal.*;
import com.nec.baas.offline.*;
import com.nec.baas.offline.internal.*;
import com.nec.baas.util.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.*;
import lombok.experimental.Accessors;
import okhttp3.Request;
import okhttp3.Response;

/**
 * オブジェクト管理クラス。
 * <p>
 * オブジェクトストレージに格納される JSON オブジェクトデータ1件を表現する。
 * <p>
 * 本クラスは Serializable であるが、マルチテナントモード時にはシリアライズできない。
 * (デシリアライズするときに例外がスローされる)
 * <p><strong>本クラスのインスタンスはスレッドセーフではない。</strong></p>
 * @since 1.0
 */
@Accessors(prefix = "m", chain = true)
@EqualsAndHashCode(doNotUseGetters = true)
public class NbObject implements Serializable, Map<String, Object> {
    private static final NbLogger log = NbLogger.getLogger(NbObject.class);

    /**
     * サーバから割り当てられたオブジェクトID。
     */
    @Getter @Setter
    private String mObjectId = null;

    /**
     * オブジェクトの作成日時。この値は、サーバで割り当てられたものである。
     */
    @Getter @Setter
    private String mCreatedTime = null;

    /**
     * オブジェクトの更新日時。この値は、サーバで割り当てられたものである。
     */
    @Getter @Setter
    private String mUpdatedTime = null;

    /**
     * バケット名
     */
    @Getter @Setter
    private String mBucketName = null;

    /**
     * ETag
     */
    @Getter @Setter
    private String mETag = null;

    /**
     * -- GETTER --
     * オブジェクトのモードを取得する。
     */
    @Getter @Setter(AccessLevel.PROTECTED)
    private NbBucketMode mMode;

    /**
     * オブジェクトに設定されたACL。
     */
    @Getter @Setter
    private NbAcl mAcl = null;

    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PACKAGE)
    private NbJSONObject mJsonObject = new NbJSONObject();

    // test only
    public void _setJsonObject(NbJSONObject json) {
        this.mJsonObject = json;
    }

    @Getter(AccessLevel.PACKAGE)
    private String mApiUrl;

    //DataSecurity
    private String mImportance = null;

    /*package*/ transient NbServiceImpl mNebulaService;

    /*package*/ transient NbOfflineService mOfflineService;

    // for test
    public NbService _getService() {
        return mNebulaService;
    }

    /**
     * フィールド名の正規表現。ピリオドは含めることができない。
     * 先頭に $ を含めることはできない。
     */
    private static final Pattern FIELD_NAME_REGEX = Pattern.compile("\\A[^.$][^.]*\\z");

    /**
     * コンストラクタ (マルチテナント非対応)。
     * オンラインモードのオブジェクトを生成する。
     * @param bucketName オブジェクトを格納するバケットの名前。
     */
    public NbObject(String bucketName) {
        this(null, bucketName, NbBucketMode.ONLINE);
    }

    /**
     * コンストラクタ (マルチテナント対応)。
     * オンラインモードのオブジェクトを生成する。
     * @param service NbService
     * @param bucketName オブジェクトを格納するバケットの名前。
     */
    public NbObject(NbService service, String bucketName) {
        this(service, bucketName, NbBucketMode.ONLINE);
    }

    /**
     * コンストラクタ(マルチテナント非対応)
     * @param bucketName オブジェクトを格納するバケットの名前。
     * @param mode オブジェクトのモード
     */
    public NbObject(String bucketName, NbBucketMode mode) {
        this(null, bucketName, mode);
    }

    /**
     * コンストラクタ(マルチテナント対応)
     * @param service NbService
     * @param bucketName オブジェクトを格納するバケットの名前。
     * @param mode オブジェクトのモード
     */
    public NbObject(NbService service, String bucketName, NbBucketMode mode) {
        if (service == null) {
            service = NbService.getInstance();
        }
        init(service, bucketName, mode);
    }

    private void init(NbService service, @NonNull String bucketName, @NonNull NbBucketMode mode) {
        // bucketNameとmodeはアプリに設定されるため、個別にチェックする

        mNebulaService = (NbServiceImpl)service;
        mBucketName = bucketName;
        mApiUrl = NbConsts.OBJECTS_PATH + "/" + mBucketName;
        mOfflineService = mNebulaService.getOfflineService();

        // オフライン無効にも関わらず、モードにオンラインが指定されていない場合
        if(mOfflineService == null && mode != NbBucketMode.ONLINE) {
            throw new IllegalStateException(" invalid mode " + mode + " offline service is null");
        }
        mMode = mode;
    }

    /*package*/ NbHttpRequestFactory getHttpRequestFactory() {
        return mNebulaService.getHttpRequestFactory();
    }

    private void executeRequest(Request request, NbRestResponseHandler handler) {
        mNebulaService.createRestExecutor().executeRequest(request, handler);
    }

    /**
     * 書き込まれたオブジェクトデータの保存を行う。
     * <p>
     * @deprecated {@link #save(NbCallback)} で置き換え
     * @since 1.0
     */
    @Deprecated
    public void save(final NbObjectCallback callback) {
        save(NbObjectCallbackWrapper.wrap(callback));
    }

    /**
     * 書き込まれたオブジェクトデータの保存を行う。
     * <p>
     * オブジェクトのデータがバケットに未作成の場合はオブジェクトを作成する。
     * オブジェクトのデータがバケットに作成済みの場合はオブジェクトの上書きを行う。
     * <p>
     * オンラインモードの場合、オブジェクトデータをサーバに保存する。
     * レプリカ・ローカルモードの場合、オフライン用データベースに対し保存を実行する。
     *
     * @param callback 保存したオブジェクトを受け取るコールバック。
     * @since 6.5.0
     */
    public void save(@NonNull final NbCallback<NbObject> callback) {
        log.fine("save() <start>");

        //リクエスト作成
        final NbJSONObject bodyJson = new NbJSONObject();
        for (Map.Entry<String,Object> entry : mJsonObject.entrySet()) {
            String key = entry.getKey();
            if ( !isValidFieldName(key)) {
                log.severe("save() <end> ERR param invalid");
                callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo(String.format("Bad field name: '%s'", key)));
                return;
            }
            bodyJson.put(key, entry.getValue());
        }
        if (mAcl != null) {
            bodyJson.put(NbKey.ACL, mAcl.toJsonObject());
        }
        if (mImportance != null) {
            bodyJson.put(NbKey.IMPORTANCE, mImportance);
        }

        switch(mMode) {
            case ONLINE:
                log.fine("save() online");

                Request request = getSaveRequest(bodyJson);

                //レスポンス処理
                NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbObject.save()") {
                    @Override
                    public void onSuccess(Response response, NbJSONObject body) {
                        //将来的、デバッグログ出力用にリザルトコードを取得しておく
                        //makeResponse2ResultMap(response);
                        setCurrentParam(body, true);
                        callback.onSuccess(NbObject.this);
                    }

                    // onFailureで個別に処理する事項は無いため、super classで処理
                };

                execSave(request, handler, bodyJson);
                break;
            case REPLICA:
            case LOCAL:
                log.fine("save() offline");
                // TODO: ポリシチェック
                //オフライン処理
                saveToLocal(bodyJson, callback);
                break;
            default:
                throw new IllegalStateException("invalid mode=" + mMode);
        }
        log.fine("save() <end>");
    }

    private Request getSaveRequest(final NbJSONObject bodyJson) {
        Request request;
        if (mETag != null) {
            // 更新
            NbJSONObject fullUpdateJson = new NbJSONObject();
            fullUpdateJson.put("$full_update", bodyJson);
            request = getHttpRequestFactory().put(mApiUrl).addPathComponent(mObjectId).param(NbKey.ETAG, mETag).body(fullUpdateJson).build();
        } else {
            // 新規
            if (mObjectId != null) {
                //オフライン生成データをオンラインで更新
                //キャッシュで生成したIDを設定
                bodyJson.put(NbKey.ID, mObjectId);
            }
            request = getHttpRequestFactory().post(mApiUrl).body(bodyJson).build();
        }
        return request;
    }

    protected void execSave(Request request, NbRestResponseHandler handler, final NbJSONObject bodyJson) {
        executeRequest(request, handler);
    }

    private void saveToLocal(final NbJSONObject bodyJson, final NbCallback<NbObject> callback) {
        log.fine("saveToLocal() run() <start>");
        // TODO: 他に合わせてバケット存在確認チェックは一旦外す
        NbOfflineResult offlineResult = null;
        bodyJson.put(NbKey.UPDATED_AT, mUpdatedTime);
        bodyJson.put(NbKey.ETAG, mETag);
        if ((mCreatedTime != null) && (!mCreatedTime.isEmpty())) {
            bodyJson.put(NbKey.CREATED_AT, mCreatedTime);
        }
        if (mObjectId != null) {
            bodyJson.put(NbKey.ID, mObjectId);
            NbJSONObject fullUpdateJson = new NbJSONObject();
            if (mETag != null) {
                fullUpdateJson.put("$full_update", bodyJson);
            } else {
                //未同期データがオフラインで更新された場合は、同期時に新規データとして
                //扱われるように状態はdirtyのままにする。(full_updateにしない）
                fullUpdateJson = bodyJson;
            }

            offlineResult = mOfflineService.objectService().updateLocalData(mObjectId, mBucketName, fullUpdateJson);
        } else {
            offlineResult = mOfflineService.objectService().createLocalData(mBucketName, bodyJson);
        }
        if (NbStatus.isSuccessful(offlineResult.getStatusCode())) {
            setCurrentParam(offlineResult.getJsonData(), true);
            callback.onSuccess(NbObject.this);
        } else {
            callback.onFailure(offlineResult.getStatusCode(), new NbErrorInfo("Failed to save local."));
        }
        log.fine("saveToLocal() run() <end>");
    }

    /**
     * オブジェクトデータの部分更新を行う。
     * <p>
     * @deprecated {@link #partUpdateObject(NbJSONObject, NbCallback)} で置き換え
     */
    @Deprecated
    public void partUpdateObject(final Map<String, Object> data, final NbObjectCallback callback) {
        partUpdateObject(new NbJSONObject(data), callback);
    }


    /**
     * オブジェクトデータの部分更新を行う。
     * <p>
     * @deprecated {@link #partUpdateObject(NbJSONObject, NbCallback)} で置き換え
     */
    @Deprecated
    public void partUpdateObject(final NbJSONObject data, final NbObjectCallback callback) {
        partUpdateObject(data, NbObjectCallbackWrapper.wrap(callback));
    }

    /**
     * オブジェクトデータの部分更新を行う。<p/>
     *
     * オブジェクトIDが設定されていない場合、メソッドの実行結果はエラーとなる。<p/>
     *
     * dataには更新用のデータを格納する。
     * 更新用データには MongoDB の更新演算子が使用できる。
     *
     * <pre>
     * {@code
     * // 例："name"と"score"を持つオブジェクトの"score"のみ更新する。
     * NbJSONObject json = new NbJSONObject();
     * json.put("score", 100);
     * nebulaObject.partUpdateObject(json, callback);
     * }
     * </pre>
     *
     * オンラインモードの場合、サーバに対し部分更新を実行する。
     * レプリカ・ローカルモードの場合、オフライン用データベースに対し部分更新を実行する。
     * レプリカ・ローカルモードではMongoDBの更新演算子は使用できない。<br>
     *
     * @param data オブジェクト更新データを格納した NbJSONObject。
     * @param callback 保存したオブジェクトを受け取るコールバック。
     * @since 6.5.0
     */
    public void partUpdateObject(@NonNull final NbJSONObject data, @NonNull final NbCallback<NbObject> callback) {
        log.fine("partUpdateObject() <start>");

        if (mObjectId == null) {
            log.severe("partUpdateObject() <end> ERR mObjectId == null");
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("Null objectId."));
            return;
        }

        switch(mMode) {
            case ONLINE:
                log.fine("partUpdateObject() online");
                //リクエスト処理
                Request request = getPartUpdateRequest(data);

                NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback,
                        "NbObject.partUpdateObject()") {
                    @Override
                    public void onSuccess(Response response, NbJSONObject body) {
                        setCurrentParam(body, true);
                        callback.onSuccess(NbObject.this);
                    }

                    // onFailureで個別に処理する事項は無いため、super classで処理
                };

                execPartUpdateObject(request, handler, data);
                break;
            case REPLICA:
            case LOCAL:
                log.fine("partUpdateObject() offline");
                //オフライン処理
                // TODO: ポリシチェック
                partUpdateObjectToLocal(data, callback);
                break;
            default:
                throw new IllegalStateException("invalid mode=" + mMode);
        }
        log.fine("partUpdateObject() <end>");
    }

    /**
     * mJsonObject に data をマージした NbJSONObject を新規作成する
     * @param data マージするデータ
     * @return JSON Object
     */
    private NbJSONObject createMergedObjectMap(NbJSONObject data) {
        NbJSONObject merged = new NbJSONObject(mJsonObject); // shallow copy
        for (Map.Entry<String,Object> entry : data.entrySet()) {
            merged.put(entry.getKey(), entry.getValue());
        }
        return merged;
    }

    private Request getPartUpdateRequest(final NbJSONObject data) {
        Request request;
        if (mETag != null) {
            request = getHttpRequestFactory().put(mApiUrl).addPathComponent(mObjectId).param(NbKey.ETAG,mETag).body(data).build();
        } else {
            // キャッシュからサーバへ新規作成で反映させる
            // 更新データをマージする
            NbJSONObject bodyJson = createMergedObjectMap(data);
            if (mAcl != null) {
                bodyJson.put(NbKey.ACL, mAcl.toJsonObject());
            }
            //キャッシュで生成したIDを設定
            bodyJson.put(NbKey.ID, mObjectId);
            request = getHttpRequestFactory().post(mApiUrl).body(bodyJson).build();
        }
        return request;
    }

    protected void execPartUpdateObject(Request request, NbRestResponseHandler handler,
            final NbJSONObject data) {
        executeRequest(request, handler);
    }

    private void partUpdateObjectToLocal(final NbJSONObject data, final NbCallback<NbObject> callback) {
        //オフライン処理
        log.fine("partUpdateObjectToLocal() run() <start>");
        // TODO: 他に合わせてバケット存在確認チェックは一旦外す
        final NbJSONObject bodyJson = createMergedObjectMap(data);
        if (mAcl != null) {
            bodyJson.put(NbKey.ACL, mAcl.toJsonObject());
        }
        bodyJson.put(NbKey.UPDATED_AT, mUpdatedTime);
        bodyJson.put(NbKey.CREATED_AT, mCreatedTime);
        bodyJson.put(NbKey.ETAG, mETag);
        bodyJson.put(NbKey.ID, mObjectId);

        // DataSecurity
        if (mImportance != null) {
            bodyJson.put(NbKey.IMPORTANCE, mImportance);
        }

        NbOfflineResult resultJson = mOfflineService.objectService().updateLocalData(mObjectId,
                mBucketName, bodyJson);
        if (NbStatus.isSuccessful(resultJson.getStatusCode())) {
            setCurrentParam(resultJson.getJsonData(), true);
            callback.onSuccess(NbObject.this);
        } else {
            callback.onFailure(resultJson.getStatusCode(), new NbErrorInfo("Failed to partUpdateObjectToLocal()."));
        }
        log.fine("partUpdateObjectToLocal() run() <end>");
    }

    /**
     * オブジェクトの削除を行う。<p/>
     *
     * オブジェクトIDが設定されていない場合、メソッドの実行結果はエラーとなる。<p/>
     *
     * オンラインモードの場合、サーバに対し削除を実行する。<br>
     * レプリカ・ローカルモードの場合、オフライン用データベースに対し削除を実行する。<br>
     * オフライン中の削除はステータス変更のみ行う。<br>
     * @param callback オブジェクトの削除結果を取得するコールバック
     * @see NbObject#getObjectId()
     */
    public void deleteObject(final NbResultCallback callback) {
        deleteObject(true, callback);
    }

    /**
     * オブジェクトの削除を行う。<p/>
     *
     * オブジェクトIDが設定されていない場合、メソッドの実行結果はエラーとなる。<p/>
     *
     * オンラインモードの場合、サーバに対し削除を実行する。<br>
     * レプリカ・ローカルモードの場合、オフライン用データベースに対し削除を実行する。<br>
     * オフライン中の削除はステータス変更のみ行う。<br>
     * @param softDelete trueにした場合は論理削除、falseの場合は物理削除
     * @param callback オブジェクトの削除結果を取得するコールバック
     * @see NbObject#getObjectId()
     */
    public void deleteObject(boolean softDelete, @NonNull final NbResultCallback callback) {
        log.fine("deleteObject() <start>");

        if (mObjectId == null || mObjectId.isEmpty()) {
            log.severe("deleteObject() <end> ERR mObjectId=null or empty");
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("Invalid objectId."));
            return;
        }

        switch(mMode) {
            case ONLINE:
                log.fine("deleteObject() online");
                //同期する時にはサーバ側で削除されたデータをローカルDB上からも
                //削除する必要があるため、物理削除するのではなく_deletedフラグを付与して削除する。

                Map<String,String> params = new HashMap<>();
                if (softDelete) {
                    params.put(NbKey.DELETE_MARK, "1");
                }
                if (mETag != null){
                    params.put(NbKey.ETAG, mETag);
                }

                Request request = getHttpRequestFactory()
                        .delete(mApiUrl).addPathComponent(mObjectId)
                        .params(params)
                        .build();

                NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbObject.deleteObject()") {
                    @Override
                    public void onSuccess(Response response) {
                        mObjectId = null;
                        callback.onSuccess();
                    }

                    // onFailureで個別に処理する事項は無いため、super classで処理
                };

                execDeleteObject(request, handler);
                break;
            case REPLICA:
            case LOCAL:
                log.fine("deleteObject() offline");
                //オフライン処理
                // TODO: ポリシチェック
                deleteToLocal(callback);
                break;
            default:
                throw new IllegalStateException("invalid mode=" + mMode);
        }
        log.fine("deleteObject() <end>");
    }

    private void deleteToLocal(final NbResultCallback callback) {
        //オフライン処理
        log.fine("deleteToLocal() run() <start>");
        // TODO: 他に合わせてバケット存在確認チェックは一旦外す
        NbOfflineResult result =
                mOfflineService.objectService().deleteLocalData(mObjectId, mBucketName, mETag);
        if (NbStatus.isSuccessful(result.getStatusCode())) {
            mObjectId = null;
            callback.onSuccess();
        } else {
            callback.onFailure(result.getStatusCode(), new NbErrorInfo("Failed to delete local data."));
        }
        log.fine("deleteToLocal() run() <end>");
    }

    /**
     * 指定したkeyに対応するデータを文字列として取得する。<p/>
     * keyに対応する型やデータが存在しない場合はdefStringを返す。
     * @param key 取得するデータのキー
     * @param defString キーが存在しない場合の既定値
     * @return 取得した文字列
     */
    public String getString(String key, String defString) {
        String result = defString;
        if (mJsonObject.containsKey(key)) {
            if (mJsonObject.get(key) instanceof String) {
                result = (String) mJsonObject.get(key);
            }
        }
        return result;
    }

    /**
     * 指定したkeyに対応するデータを数値として取得する。<p/>
     * keyに対応する型やデータが存在しない場合はdefNumberを返す。
     * @param key 取得するデータのキー
     * @param defNumber キーが存在しない場合の既定値
     * @return 取得した数値
     */
    public Number getNumber(String key, Number defNumber) {
        Number result = defNumber;
        if (mJsonObject.containsKey(key)) {
            if (mJsonObject.get(key) instanceof Number) {
                result = (Number) mJsonObject.get(key);
            }
        }
        return result;
    }

    /**
     * 指定したkeyに対応するデータを Object として取得する。<p/>
     * 取得可能なオブジェクトの型は、以下のいずれか。
     * <ul>
     *   <li>Number</li>
     *   <li>String</li>
     *   <li>Boolean</li>
     *   <li>{@link NbJSONObject} </li>
     *   <li>{@link NbJSONArray}</li>
     * </ul>
     * keyに対応するデータが存在しない、取得可能な型でない場合はdefAnyを返す。
     * 取得したObjectは適切な型にキャストして使用すること。
     *
     * @param key 取得するデータのキー
     * @param defAny キーが存在しない場合の既定値
     * @return 取得したObject
     */
    public Object getAny(String key, Object defAny) {
        Object result = defAny;
        if (mJsonObject.containsKey(key)) {
            Object jsonValue = mJsonObject.get(key);
            if (jsonValue instanceof String ||
                jsonValue instanceof Number ||
                jsonValue instanceof Boolean ||
                jsonValue instanceof NbJSONObject ||
                jsonValue instanceof NbJSONArray) {
                result = jsonValue;
            }
        }
        return result;
    }

    /**
     * 指定したkeyに対応するデータを NbJSONObject として取得する。<p/>
     * keyに対応する型やデータが存在しない場合は defJson を返す。
     * @param key 取得するデータのキー
     * @param defJson キーが存在しない場合の既定値
     * @return 取得したObject
     */
    public NbJSONObject getJSONObject(String key, NbJSONObject defJson) {
        NbJSONObject result = defJson;
        if (mJsonObject.containsKey(key)) {
            if (mJsonObject.get(key) instanceof NbJSONObject) {
                result = (NbJSONObject) mJsonObject.get(key);
            }
        }
        return result;
    }

    /**
     * 指定したkeyに対応するデータを NbJSONArray として取得する。<p/>
     * keyに対応する型やデータが存在しない場合はdefArrayを返す。
     * @param key 取得するデータのキー
     * @param defArray キーが存在しない場合の既定値
     * @return 取得した値
     */
    public NbJSONArray getJSONArray(String key, NbJSONArray defArray) {
        NbJSONArray result = defArray;
        if (mJsonObject.containsKey(key)) {
            if (mJsonObject.get(key) instanceof NbJSONArray) {
                result = (NbJSONArray) mJsonObject.get(key);
            }
        }
        return result;
    }

    /**
     * 指定したkeyに対応するデータを真偽値として取得する。<p/>
     * keyに対応する型やデータが存在しない場合はdefBooleanを返す。
     * @param key 取得するデータのキー
     * @param defBoolean キーが存在しない場合の既定値
     * @return 取得した値
     */
    public Boolean getBoolean(String key, Boolean defBoolean) {
        Boolean result = defBoolean;
        if (mJsonObject.containsKey(key)) {
            if (mJsonObject.get(key) instanceof Boolean) {
                result = (Boolean) mJsonObject.get(key);
            }
        }
        return result;
    }

    /**
     * 指定された key に対応する value をオブジェクトに設定する。
     *
     * <p>
     *     key にはピリオドを含むことはできない。また先頭に $ を含めることはできない。
     * </p>
     *
     * <p>value に指定できるオブジェクトの型は、以下のいずれか。
     * <ul>
     *   <li>Number</li>
     *   <li>String</li>
     *   <li>Boolean</li>
     *   <li>Map&lt;String,Object&gt; - {@link NbJSONObject} を含む</li>
     *   <li>List&lt;Object&gt; - {@link NbJSONArray} を含む</li>
     * </ul>
     *
     * <p>Map, List に格納可能なオブジェクトの型も上記に準ずる。
     *
     * @param key 指定したvalueを識別するためのキー
     * @param value 指定したkeyに対応する値
     */
    @Override
    public Object put(String key, Object value) {
        if (!isValidFieldName(key)) {
            throw new IllegalArgumentException("Invalid field name : " + key);
        }
        return mJsonObject.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        return mJsonObject.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ?> map) {
        mJsonObject.putAll(map);
    }

    @Override
    public void clear() {
        mJsonObject.clear();
    }

    /**
     * 指定されたACLをオブジェクトに設定する。<br>
     * @param acl オブジェクトに設定するACL。
     */
    public void putAcl(NbAcl acl) {
        if (acl != null) {
            mAcl = new NbAcl(acl);
        } else {
            mAcl = null;
        }
    }

    protected void execDeleteObject(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /**
     * オブジェクトに格納されたキーのSetを取得する。
     * @return 本クラスに格納されたキーのSet。
     */
    public Set<String> keySet() {
        return mJsonObject.keySet();
    }

    @Override
    public Collection<Object> values() {
        return mJsonObject.values();
    }

    /**
     * オブジェクトに格納されたキー・値の Entry Set を返す
     * @return 本クラスに格納されたキー・値の Entry Set。
     */
    public Set<Map.Entry<String,Object>> entrySet() {
        return mJsonObject.entrySet();
    }


    /** {@inheritDoc} */
    @Override
    public int size() {
        return mJsonObject.size();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() {
        return mJsonObject.isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public Object get(Object key) {
        return mJsonObject.get(key);
    }

    /**
     * キーに対応する値（フィールド)が存在しているか判定する。
     * なお、値が null の場合も存在していると判定する。
     * @param key キー
     * @return 値が存在すれば true
     */
    @Override
    public boolean containsKey(Object key) {
        return mJsonObject.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return mJsonObject.containsValue(value);
    }

    /**
     * キーに対応する値が null であるか、もしくは値が存在しないかを判定する
     * @param key キー
     * @return 値が null もしくは値が存在しない場合は true
     */
    public boolean isNull(String key) {
        return !mJsonObject.containsKey(key) || mJsonObject.get(key) == null;
    }

    /**
     * オブジェクトから指定された key のデータを削除する。
     * @param key 削除対象のkey
     */
    public void remove(String key) {
        if (key != null) {
            mJsonObject.remove(key);
        }
    }

    /**
     * NbJSONObject に変換する
     * @return NbJSONObject
     */
    public NbJSONObject toJSONObject() {
        NbJSONObject json = (NbJSONObject)mJsonObject.clone();
        if (mObjectId != null) {
            json.put(NbKey.ID, mObjectId);
        }
        if (mAcl != null) {
            json.put(NbKey.ACL, mAcl.toJsonObject());
        }
        if (mCreatedTime != null) {
            json.put(NbKey.CREATED_AT, mCreatedTime);
        }
        if (mUpdatedTime != null) {
            json.put(NbKey.UPDATED_AT, mUpdatedTime);
        }
        if (mETag != null) {
            json.put(NbKey.ETAG, mETag);
        }
        return json;
    }

    /**
     * JSON 文字列に変換する
     * @return JSON文字列
     */
    public String toJSONString() {
        return toJSONObject().toJSONString();
    }
    
    @Override
    public String toString() {
        return toJSONString();
    }

    //オフライン処理
    /**
     * オブジェクトの同期状態を取得する。
     * 同期状態は {@link NbSyncState} で定義されている。
     * レプリカモードのみ有効。
     * @return オブジェクトの同期状態
     */
    public NbSyncState getSyncState() {
        NbSyncState state;
        if(mMode == NbBucketMode.REPLICA) {
            state = mOfflineService.objectService().getSyncState(mObjectId, mBucketName);
            if (state == NbSyncState.DIRTY_FULL) {
                state = NbSyncState.DIRTY;
            }
        } else {
            throw new IllegalStateException("invalid mode=" + mMode);
        }
        return state;
    }

    /**
     * オブジェクトの最終更新日時を取得する。
     * レプリカモードのみ有効。
     * @return オブジェクトの最終更新日時
     */
    public String getLastSyncTime() {
        String time;
        if(mMode == NbBucketMode.REPLICA) {
            time = mOfflineService.objectService().getObjectLastSyncTime(mObjectId, mBucketName);
        } else {
            throw new IllegalStateException("invalid mode=" + mMode);
        }
        return time;
    }

    /** 内部インタフェース */
    public void setCurrentParam(NbJSONObject map, boolean isClear) {
        if (map == null) return;

        //サーバレスポンスを反映させる場合など、全データ上書きの場合は現フィールドをクリアする。
        if (isClear) {
            initParam();
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            switch (key) {
                case NbKey.ID:
                    setObjectId((String) value);
                    break;
                case NbKey.CREATED_AT:
                    setCreatedTime((String) value);
                    break;
                case NbKey.UPDATED_AT:
                    setUpdatedTime((String) value);
                    break;
                case NbKey.ACL:
                    NbAcl acl;
                    try {
                        acl = new NbAcl(value);
                    } catch (IllegalArgumentException e ){
                        log.severe("setCurrentParam() invalid ACL detected. set empty ACL instead of parameter:" + value);
                        acl = new NbAcl();
                    }
                    putAcl(acl);
                    break;
                case NbKey.ETAG:
                    setETag((String) value);
                    break;
                case NbKey.IMPORTANCE:
                    setImportance((String) value);
                    break;
                default:
                    put(key, value);
                    break;
            }
        }
    }

    protected void initParam() {
        setObjectId(null);
        setCreatedTime(null);
        setUpdatedTime(null);
        setETag(null);
        putAcl(null);
        if (mJsonObject != null) {
            mJsonObject.clear();
        }
        setImportance(null);
    }

    /**
     * オブジェクトに設定されたデータを NbJSONObject 形式で取得する。
     * @return オブジェクトに設定されたデータ
     */
    public NbJSONObject getObjectData() {
        return mJsonObject;
    }

    //DataSecurity
    /**
     * 指定されたImportanceをオブジェクトに設定する。<br>
     * @param importance オブジェクトに設定するImportance。
     */
    public void setImportance(String importance) {
        mImportance = importance;
    }

    /**
     * オブジェクトに設定されたImportanceを解除する。
     */
    public void resetImportance() {
        mImportance = null;
    }

    /**
     * オブジェクトに設定されたImportanceを取得する。<br>
     * @return オブジェクトに設定されたImportance
     */
    public String getImportance() {
        return mImportance;
    }

    /**
     * フィールド名が正しいかチェックする。
     * nullチェック、使用可能文字チェックを行う。
     * @param fieldName フィールド名
     * @return 使用可能であればtrue、使用不可であればfalse
     */
    public static boolean isValidFieldName(String fieldName) {
        //フィールド名は必須なのでnullチェックを行う
        if (fieldName == null) {
            return false;
        }

        Matcher m = FIELD_NAME_REGEX.matcher(fieldName);
        return m.matches();
    }

    /**
     * @deprecated {@link #getCreatedTime()} で置き換え
     */
    @Deprecated
    public String getCreateTime() {
        return getCreatedTime();
    }

    /**
     * Serializable 対応: writeObject
     * @param stream
     * @throws IOException
     */
    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
    }

    /**
     * Serializable 対応: readObject
     * @param stream
     * @throws IOException
     */
    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        init(NbService.getInstance(), mBucketName, mMode);
    }
}
