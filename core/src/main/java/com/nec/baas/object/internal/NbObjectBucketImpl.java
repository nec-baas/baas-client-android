/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object.internal;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.json.*;
import com.nec.baas.object.*;
import com.nec.baas.offline.*;
import com.nec.baas.offline.internal.*;
import com.nec.baas.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import okhttp3.Request;
import okhttp3.Response;

/**
 * オブジェクトバケットの機能を提供するクラス。<p/>
 *
 * 通常は {@link NbObjectBucketManagerImpl#getBucket(String)} でインスタンスを取得する。
 * @since 1.0
 */
@Accessors(prefix = "m")
public class NbObjectBucketImpl extends NbBaseBucketImpl<NbObjectBucket> implements NbObjectBucket {
    private static final NbLogger log = NbLogger.getLogger(NbObjectBucketImpl.class);

    @Getter
    private String mObjectApiUrl = NbConsts.OBJECTS_PATH;

    /**
     * フィールド名の正規表現(インデックス設定用)
     * SQLインジェクション対策のため、英数字、アンダースコア、ハイフン以外を含むキーはインデックス設定できない。
     */
    private static final Pattern FIELD_NAME_REGEX = Pattern.compile("[a-zA-Z0-9_-]+");

    /**
     * コンストラクタ (内部インタフェース)
     * @param service NbService
     * @param bucketName バケット名
     * @param mode バケットモード
     */
    public NbObjectBucketImpl(NbService service, String bucketName, NbBucketMode mode) {
        super(service, bucketName, NbConsts.OBJECT_BUCKET_PATH + "/" + bucketName, mode);
        mObjectApiUrl = NbConsts.OBJECTS_PATH + "/" + mBucketName;
    }

    public NbObject newObject() {
        return new NbObject(mNebulaService, mBucketName, mMode);
    }

    /** {@inheritDoc} */
    @Override
    public void getObject(final String objectId, final NbObjectCallback callback) {
        getObject(objectId, NbObjectCallbackWrapper.wrap(callback));
    }

    @Override
    public void getObject(@NonNull final String objectId, @NonNull final NbCallback<NbObject> callback) {
        log.fine("getObject() <start> objectId=" + objectId);

        switch(mMode) {
            case ONLINE:
                log.fine("getObject() online");
                Request request = getHttpRequestFactory().get(mObjectApiUrl).addPathComponent(objectId).build();

                NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbObjectBucketImpl.getObject") {
                    @Override
                    public void onSuccess(Response response, NbJSONObject body) {
                        log.fine("getObject() -> onSuccess()");
                        NbObject object = makeObject(body, mBucketName);
                        callback.onSuccess(object);
                    }
                    //onFailure発生時は、super class側でエラー通知を行う
                };
                execGetObject(request, handler, objectId);
                break;
            case REPLICA:
            case LOCAL:
                log.fine("getObject() offline");
                //オフライン処理
                getObjectToLocal(objectId, callback);
                break;
            default:
                throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }
        log.fine("getObject() <end>");
    }

    private void getObjectToLocal(final String objectId, final NbCallback<NbObject> callback) {
        log.fine("getObjectToLocal() run() <start>");
        NbOfflineResult result = mOfflineService.objectService().readLocalData(objectId, mBucketName);
        if (NbStatus.isSuccessful(result.getStatusCode())) {
            NbObject object = new NbObject(mNebulaService, mBucketName, mMode);
            object.setCurrentParam(result.getJsonData(), true);
            callback.onSuccess(object);
        } else {
            callback.onFailure(result.getStatusCode(), new NbErrorInfo("Failed to read local data."));
        }
        log.fine("getObjectToLocal() run() <end>");
    }

    protected void execGetObject(Request request, NbRestResponseHandler handler, String objectId) {
        executeRequest(request, handler);
    }

    @Override
    @Deprecated
    public void query(final NbQuery query, final NbObjectCallback callback) {
        _query(query, callback);
    }

    /** {@inheritDoc} */
    @Override
    public void query(NbQuery query, NbCallback<List<NbObject>> callback) {
        _query(query, callback);
    }

    /** {@inheritDoc} */
    @Override
    public void queryWithCount(NbQuery query, NbCountCallback<List<NbObject>> callback) {
        _query(query, callback);
    }

    private void _query(final NbQuery query, @NonNull final NbBaseCallback callback) {
        log.fine("query() <start> query=" + query);

        switch(mMode) {
            case ONLINE:
                log.fine("query() online");
                queryOnline(query, callback);
                break;
            case REPLICA:
            case LOCAL:
                log.fine("query() offline");
                //オフライン処理
                // TODO: ポリシチェック
                queryToLocal(query, callback);
                break;
            default:
                throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }
        log.fine("query() <end>");
    }

    // サーバにクエリを発行する
    private void queryOnline(final NbQuery query, final NbBaseCallback callback) {
        // クエリパラメータ取得
        Map<String, String> param = queryToParam(query);

        Request request = getHttpRequestFactory().get(mObjectApiUrl).params(param).build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbObjectBucketImpl.query()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                List<NbObject> resultList = new ArrayList<>();

                int deleteCount = 0;
                final NbJSONArray<NbJSONObject> objList = body.getJSONArray(NbKey.RESULTS);

                if (objList != null) {
                    for (NbJSONObject jobj : objList) {
                        NbObject nebulaObj = makeObject(jobj, mBucketName);

                        if (nebulaObj != null) {
                            resultList.add(nebulaObj);
                        } else {
                            //返却カウント数を調整するため削除済みデータをカウント
                            deleteCount++;
                        }
                    }
                }
                int count = -1;
                if (body.containsKey(NbKey.COUNT)) {
                    count = (Integer)body.get(NbKey.COUNT) - deleteCount;
                    log.fine("query() count={0} (serverCount={1} deleteCount={2})",
                            count, body.get(NbKey.COUNT), deleteCount);
                }
                doQuerySuccessCallback(callback, resultList, count);
            }
            //onFailure発生時は、super class側でエラー通知を行う
        };
        execQuery(request, handler);
    }

    private void doQuerySuccessCallback(NbBaseCallback callback, List<NbObject> objects, int count) {
        if (callback instanceof NbCountCallback) {
            NbCountCallback<List<NbObject>> cb = (NbCountCallback<List<NbObject>>)callback;
            cb.onSuccess(objects, count);
        }
        else if (callback instanceof NbCallback) {
            NbCallback<List<NbObject>> cb = (NbCallback<List<NbObject>>)callback;
            cb.onSuccess(objects);
        }
        else if (callback instanceof NbObjectCallback) {
            NbObjectCallback cb = (NbObjectCallback)callback;
            cb.onSuccess(objects, count >= 0 ? count : null);
        }
    }

    // NbQuery をクエリパラメータに変換する
    private Map<String, String> queryToParam(NbQuery query) {
        Map<String, String> param = new HashMap<>();
        if (query == null) return param;

        // where
        if (query.getClause() != null && query.getClause().getConditions() != null) {
            param.put(NbKey.WHERE, query.getClause().getConditions().toJSONString());
        }

        // order
        List<String> orders = query.getSortOrders();
        if (orders != null && !orders.isEmpty()) {
            StringBuilder b = new StringBuilder();
            int count = 0;
            for (String order : orders) {
                if (count > 0) b.append(",");
                b.append(order);
                count++;
            }
            param.put(NbKey.ORDER, b.toString());
        }

        param.put(NbKey.SKIP, String.valueOf(query.getSkipCount()));
        param.put(NbKey.LIMIT, String.valueOf(query.getLimit()));
        param.put(NbKey.COUNT, String.valueOf(query.getCountQueryAsNum()));

        if (query.isDeleteMark()) {
            param.put(NbKey.DELETE_MARK, "1");
        }

        // projection
        if (query.getProjection() != null && !query.getProjection().isEmpty()) {
            param.put(NbKey.PROJECTION, query.getProjection().toJSONString());
        }

        return param;
    }

    private void queryToLocal(final NbQuery query, final NbBaseCallback callback) {
        log.fine("queryToLocal() <start>"
                + " query=" + query);

        NbOfflineResult result = mOfflineService.objectService().queryLocalData(query, mBucketName);
        if (NbStatus.isSuccessful(result.getStatusCode())) {
            NbJSONObject resultMap = result.getJsonData();
            final NbJSONArray<NbJSONObject> objList = resultMap.getJSONArray(NbKey.RESULTS);

            List<NbObject> objects = new ArrayList<>();
            if (objList != null) {
                log.fine("queryToLocal() objList.size()=" + objList.size());
                for (NbJSONObject jobj : objList) {
                    objects.add(makeObject(jobj, mBucketName));
                }
            }
            int count = -1;
            if (resultMap.containsKey(NbKey.COUNT)) {
                count = (int) resultMap.get(NbKey.COUNT);
            }
            log.fine("queryToLocal() count=" + count);

            doQuerySuccessCallback(callback, objects, count);
        } else {
            callback.onFailure(result.getStatusCode(), new NbErrorInfo("Failed to query local data."));
        }
        log.fine("queryToLocal() <end>");
    }

    protected void execQuery(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }


    /** {@inheritDoc} */
    public void save(@NonNull final NbCallback<NbObjectBucket> callback) {
        log.fine("save() <start>");

        switch (mMode) {
            case ONLINE:
                log.fine("save() online");
                saveOnline(callback);
                break;
            case LOCAL:
                log.fine("save() offline");
                saveToLocal(callback);
                break;
            default:
                throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }
        log.fine("save() <end>");
    }

    private void saveOnline(final NbCallback<NbObjectBucket> callback) {
        //リクエストボディ作成
        NbJSONObject bodyJson = getBodyJson();
        String requestBody = bodyJson.toJSONString();

        Request request = getHttpRequestFactory()
                .put(NbConsts.OBJECT_BUCKET_PATH).addPathComponent(mBucketName)
                .body(requestBody).build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbObjectBucketImpl.save()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                NbAcl resultAcl = new NbAcl(body.getJSONObject(NbKey.ACL));
                setAcl(resultAcl);

                NbContentAcl resultContentAcl = new NbContentAcl(body.getJSONObject(NbKey.CONTENT_ACL));
                setContentAcl(resultContentAcl);

                setDescription(body.getString(NbKey.DESCRIPTION));

                callback.onSuccess(NbObjectBucketImpl.this);
            }
        };
        execSave(request, handler, requestBody);
    }

    private void saveToLocal(final NbCallback<NbObjectBucket> callback) {
        // description等を必須にするのは、オンラインと合わせておく
        NbJSONObject json = getBodyJson();
        // バケット名とモードをJSONに入れる
        json.put(NbKey.NAME, mBucketName);
        json.put(NbKey.BUCKET_MODE, mMode.idString());

        // ローカルDBに保存
        int result = mOfflineService.objectService().saveBucketCache(mBucketName, json.toJSONString(), true);
        if (NbStatus.isNotSuccessful(result)) {
            // 保存失敗の場合
            callback.onFailure(result, new NbErrorInfo("Failed to save bucket to local DB."));
            return;
        }

        // バケット情報を読み出す
        NbOfflineResult bucket = mOfflineService.objectService().readLocalBucket(mBucketName);

        if (NbStatus.isSuccessful(bucket.getStatusCode())) {
            NbJSONObject resultJson = bucket.getJsonData();

            NbAcl acl = new NbAcl(resultJson.getJSONObject(NbKey.ACL));
            setAcl(acl);

            NbContentAcl contentAcl = new NbContentAcl(resultJson.getJSONObject(NbKey.CONTENT_ACL));
            setContentAcl(contentAcl);

            setDescription(resultJson.getString(NbKey.DESCRIPTION));
            callback.onSuccess(NbObjectBucketImpl.this);
        } else {
            // read権限がない場合、このルートに入ってしまう
            callback.onFailure(bucket.getStatusCode(), new NbErrorInfo("Failed to read bucket to local DB."));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void aggregate(@NonNull final NbJSONArray pipeline, final NbJSONObject options, @NonNull final NbCallback<NbJSONArray> callback) {
        // オンラインモードでない時はエラー返却
        if (mMode != NbBucketMode.ONLINE) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }

        //リクエストボディ作成
        NbJSONObject bodyJson = new NbJSONObject();
        bodyJson.put(NbKey.PIPELINE, pipeline);
        
        if (options != null) {
            bodyJson.put(NbKey.OPTIONS, options);
        }

        String requestBody = bodyJson.toJSONString();

        Request request = getHttpRequestFactory()
                .post(NbConsts.OBJECTS_PATH)
                .addPathComponent(mBucketName)
                .addPathComponent(NbConsts.AGGREGATE_PATH_COMPONENT)
                .body(requestBody).build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbObjectBucketImpl.aggregate()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                NbJSONArray results = body.getJSONArray(NbKey.RESULTS);
                callback.onSuccess(results);
            }
        };
        executeRequest(request, handler);
    }

    /** {@inheritDoc} */
    @Override
    public void deleteObjects(final NbQuery query, final NbCallback<Integer> callback) {
        deleteObjects(query, true, callback);
    }

    /** {@inheritDoc */
    @Override
    public void deleteObjects(final NbQuery query, final boolean softDelete, @NonNull final NbCallback<Integer> callback) {
        if (this.getMode() != NbBucketMode.ONLINE) {
            throw new IllegalStateException("Not Online Bucket");
        }

        final Map<String, String> params = new HashMap<>();
        final NbClause clause = query != null ? query.getClause() : null;
        if (clause != null  && clause.getConditions() != null) {
            params.put(NbKey.WHERE, clause.getConditions().toJSONString());
        }
        if (softDelete) {
            params.put(NbKey.DELETE_MARK, "1");
        }

        Request request = getHttpRequestFactory()
                .delete(NbConsts.OBJECTS_PATH).addPathComponent(mBucketName)
                .params(params).build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbObjectBucketImpl.deleteObjects()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                int count = body.getInt("deletedObjects");
                callback.onSuccess(count);
            }
        };

        executeRequest(request, handler);
    }


    /**
     * バケットの同期を行う。<p/>
     * 同期ではバケット情報とバケットに保存されたオブジェクトをサーバから取得後、オフライン用データベースのデータを更新する。<br>
     * 処理の進捗はSyncEventListenerで通知する。
     * @see NbObjectSyncEventListener
     */
    public void sync(@NonNull final NbResultCallback callback) {
        log.fine("sync() <start>");

        // レプリカモードでない時はエラー返却
        if (mMode != NbBucketMode.REPLICA) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }

        NbQuery scope = null;
        try {
            scope = mOfflineService.objectService().getSyncScope(mBucketName);
        } catch (NullPointerException ex) {
            // IllegalStateExceptionに変換
        } catch (NbDatabaseException ex) {
            // IllegalStateExceptionに変換
        }

        if(scope == null) {
            throw new IllegalStateException("Failed to get SyncScope");
        }

        NbUtil.runInBackground(new Runnable() {
            public void run() {
                execSync(callback);
            }
        });

        log.fine("sync() <end>");
    }

    private void execSync(final NbResultCallback callback) {
        final int result = mOfflineService.objectService().syncBucket(mBucketName);

        NbServiceImpl.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (NbStatus.isSuccessful(result)) {
                    callback.onSuccess();
                } else {
                    callback.onFailure(result, new NbErrorInfo("sync failure"));
                }
            }
        });
    }

    /** {@inheritDoc} */
    public void removeCache() {
        switch (mMode) {
            case REPLICA:
            case LOCAL:
                mOfflineService.objectService().removeBucketCache(mBucketName);
                break;
            default:
                throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }
    }

    protected void execSave(Request request, NbRestResponseHandler handler, String requestBody) {
        executeRequest(request, handler);
    }

//    /** {@inheritDoc} */
//    @Override
//    public void setAutoSyncInterval(long interval) {
//        log.fine("setAutoSyncInterval() <start>");
//        if (mMode != NbBucketMode.REPLICA) {
//            throw new IllegalStateException("invalid mode " + mMode);
//        }
//        try {
//            log.finer("setAutoSyncInterval beginTransaction");
//            mOfflineService.beginTransaction();
//            mOfflineService.objectService().setAutoSyncInterval(mBucketName, interval);
//        } catch (IllegalArgumentException ex) {
//            throw ex;
//        } catch (NbDatabaseException ex) {
//            throw ex;
//        } finally {
//            mOfflineService.endTransaction();
//            log.finer("setAutoSyncInterval endTransaction");
//        }
//        log.fine("setAutoSyncInterval() <end>");
//    }
//
//    /** {@inheritDoc} */
//    @Override
//    public long getAutoSyncInterval() {
//        long result = 0;
//        log.fine("getAutoSyncInterval() <start>");
//        if (mMode != NbBucketMode.REPLICA) {
//            throw new IllegalStateException("invalid mode " + mMode);
//        }
//        try {
//            result = mOfflineService.objectService().getAutoSyncInterval(mBucketName);
//        } catch (IllegalArgumentException ex) {
//            throw ex;
//        } catch (NbDatabaseException ex) {
//            throw ex;
//        }
//        log.fine("getAutoSyncInterval() <end>");
//        return result;
//    }

    @Override
    public void setResolveConflictPolicy(int policy) {
        setResolveConflictPolicy(NbConflictResolvePolicy.fromInt(policy));
    }

    @Override
    public void setResolveConflictPolicy(NbConflictResolvePolicy policy) {
        if (mMode != NbBucketMode.REPLICA) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        } else {
            mOfflineService.objectService().setResolveConflictPolicy(getBucketName(), policy);
        }
    }

    @Override
    public NbConflictResolvePolicy getResolveConflictPolicy() {
        if (mMode != NbBucketMode.REPLICA) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        } else {
            return mOfflineService.objectService().getResolveConflictPolicy(getBucketName());
        }
    }

//    /** {@inheritDoc} */
//    public List<String> getPendingSyncObjectList() {
//        if (mMode != NbBucketMode.REPLICA) {
//            log.fine("getPendingSyncObjectList() disable offline");
//            throw new IllegalStateException("invalid mode " + mMode);
//        }
//        List<String> resultList = mOfflineService.objectService().getPendingSyncObjectList(mBucketName);
//
//        return resultList;
//    }

    /** {@inheritDoc} */
    @Override
    public String getLastSyncTime() {
        if (mMode != NbBucketMode.REPLICA) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        } else {
            return mOfflineService.objectService().getLastSyncTime(mBucketName);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setSyncScope(NbQuery scope) {
        if (mMode != NbBucketMode.REPLICA) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }

        try {
            log.finer("setSyncScope beginTransaction");
            mOfflineService.beginTransaction();
            mOfflineService.objectService().setSyncScope(mBucketName, scope);
        } catch (IllegalArgumentException | NbDatabaseException ex) {
            throw ex;
        } finally {
            mOfflineService.endTransaction();
            log.finer("setSyncScope endTransaction");
        }
        log.fine("setSyncScope() <end>");
    }

    /** {@inheritDoc} */
    @Override
    public NbQuery getSyncScope() {
        NbQuery scope = null;
        if (mMode != NbBucketMode.REPLICA) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }

        try {
            scope = mOfflineService.objectService().getSyncScope(mBucketName);
        } catch (NullPointerException | NbDatabaseException ex) {
            throw ex;
        }
        return scope;
    }

    /** {@inheritDoc} */
    @Override
    public void removeSyncScope() {
        if (mMode != NbBucketMode.REPLICA) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }

        try {
            mOfflineService.beginTransaction();
            mOfflineService.objectService().removeSyncScope(mBucketName);
        } catch (NullPointerException | NbDatabaseException ex) {
            throw ex;
        } finally {
            mOfflineService.endTransaction();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void registerSyncEventListener(NbObjectSyncEventListener listener) {
        log.fine("registerSyncEventListener() <start>");
        if (mMode != NbBucketMode.REPLICA) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }
        try {
            mOfflineService.objectService().registerSyncEventListener(mBucketName, listener);
        } catch (NullPointerException ex) {
            throw ex;
        }
        log.fine("registerSyncEventListener() <end>");
    }

    /** {@inheritDoc} */
    @Override
    public void unregisterSyncEventListener(NbObjectSyncEventListener listener) {
        log.fine("unregisterSyncEventListener() <start>");
        if (mMode != NbBucketMode.REPLICA) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }
        try {
            mOfflineService.objectService().unregisterSyncEventListener(mBucketName, listener);
        } catch (NullPointerException ex) {
            throw ex;
        }
    }

    /**
     * JSON Object から NebulaObjectを作成する。
     * @param json JSON
     * @param bucketName バケット名
     * @return NbObject
     */
    protected NbObject makeObject(@NonNull NbJSONObject json, @NonNull String bucketName) {

        NbObject obj = new NbObject(mNebulaService, bucketName, mMode);

        for (Map.Entry<String,Object> entry: json.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case NbKey.ID:
                    obj.setObjectId((String)value);
                    break;
                case NbKey.CREATED_AT:
                    obj.setCreatedTime((String)value);
                    break;
                case NbKey.UPDATED_AT:
                    obj.setUpdatedTime((String)value);
                    break;
                case NbKey.ACL:
                    NbAcl acl;
                    try {
                        acl = new NbAcl(value);
                    } catch (IllegalArgumentException e ){
                        log.severe("makeObject() invalid ACL detected. set empty ACL instead of parameter:" + value);
                        acl = new NbAcl();
                    }
                    obj.putAcl(acl);
                    break;
                case NbKey.ETAG:
                    obj.setETag((String)value);
                    break;
                //DataSecurity
                case NbKey.IMPORTANCE:
                    obj.setImportance((String)value);
                    break;
                default:
                    obj.put(key, value);
                    break;
            }
        }
        return obj;
    }

    @Override
    public void setIndexToLocal(@NonNull final Map<String, NbIndexType> indexKeys, @NonNull final NbResultCallback callback) {
        log.fine("setIndexToLocal() <start> indexKeys=" + indexKeys);

        if (mMode == NbBucketMode.ONLINE) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }

        for (Map.Entry<String, NbIndexType> entry : indexKeys.entrySet()) {
            if (!isValidFieldName(entry.getKey())) {
                throw new IllegalArgumentException("Invalid field name : " + entry.getKey());
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("Invalid type : " + entry.getValue());
            }
        }

        // 既に存在するインデックスと同じものであれば即OKを返して終了
        Map<String, NbIndexType> currentIndexes = mOfflineService.objectService().getIndexFromLocalData(mBucketName);
        if (currentIndexes.equals(indexKeys)) {
            log.info("Same as current indexes, skip!");
            callback.onSuccess();
            return;
        }

        // 非同期で実行
        NbUtil.runInBackground(new Runnable() {
            public void run() {
                NbOfflineResult result = mOfflineService.objectService().setIndexToLocalData(indexKeys, mBucketName);
                if (NbStatus.isSuccessful(result.getStatusCode())) {
                    callback.onSuccess();
                } else {
                    callback.onFailure(result.getStatusCode(), new NbErrorInfo("failed to set index to local data."));
                }
            }
        });

        log.fine("setIndexToLocal() <end>");
    }

    @Override
    public Map<String, NbIndexType> getIndexFromLocal() {
        log.fine("getIndexFromLocal() <start>");

        if (mMode == NbBucketMode.ONLINE) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }

        log.fine("getIndexFromLocal() <end>");
        return  mOfflineService.objectService().getIndexFromLocalData(mBucketName);
    }

    /**
     * ローカル用にフィールド名が正しいかチェックする。
     * nullチェック、使用可能文字チェックを行う。
     * @param fieldName フィールド名
     * @return 使用可能であればtrue、使用不可であればfalse
     */
    private static boolean isValidFieldName(String fieldName) {
        //フィールド名は必須なのでnullチェックを行う
        if (fieldName == null) {
            return false;
        }

        Matcher m = FIELD_NAME_REGEX.matcher(fieldName);
        return m.matches();
    }


    /** {@inheritDoc} */
    @Override
    public void executeBatchOperation(@NonNull final NbJSONArray batchList, final String requestToken, @NonNull final NbCallback<NbJSONArray> callback){

        if (mMode != NbBucketMode.ONLINE) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }

        NbRestResponseHandler handler = makeResponseHandlerBatchOperation(callback);
        executeBatchOperation(batchList, requestToken, handler);
    }

    /** {@inheritDoc} */
    @Override
    public void executeBatchOperation(@NonNull final NbJSONObject requests, final String requestToken, @NonNull final NbCallback<NbJSONArray> callback) {

        if (mMode != NbBucketMode.ONLINE) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }

        if (requests.getJSONArray(NbKey.REQUESTS) == null) {
            throw new IllegalArgumentException("Illegal argument.");
        }

        NbJSONArray batchArray = requests.getJSONArray(NbKey.REQUESTS);
        NbRestResponseHandler handler = makeResponseHandlerBatchOperation(callback);

        executeBatchOperation(batchArray, requestToken, handler);
    }

    /**
     * バッチ処理要求実行
     * @param batchList バッチ要求一覧
     * @param requestToken リクエストトークン
     * @param handler 要求結果を受けるハンドラ
     */
    public void executeBatchOperation(NbJSONArray batchList, String requestToken, NbRestResponseHandler handler){

        Request request;

        // バッチリクエストの JSON ボディ
        String batchURL = NbConsts.OBJECTS_PATH + "/" + mBucketName + NbConsts.BATCH_URL;

        NbJSONObject batchMap = new NbJSONObject();
        batchMap.put(NbKey.REQUESTS, batchList);

        // クエリパラメータ生成
        Map<String, String> param = new HashMap<>();

        // push時も削除はdeleteフラグで削除
        param.put(NbKey.DELETE_MARK, "1");

        //再送用にリクエストトークンを付加する。
        if (requestToken != null) param.put(NbKey.REQUEST_TOKEN, requestToken);

        request = getHttpRequestFactory()
                .post(batchURL)
                .body(batchMap)
                .params(param)
                .build();

        execBatch(request, handler);
    }

    /** {@inheritDoc} */
    @Override
    public NbJSONObject createBatchRequest(@NonNull final NbObject object, @NonNull NbBatchOperationType type) {

        if (mMode != NbBucketMode.ONLINE) {
            throw new IllegalStateException(INVALID_BUCKET_MODE + mMode);
        }

        NbJSONObject obj = object.toJSONObject();
        NbJSONObject ret = new NbJSONObject();

        // 不正キーを含むObjectの場合はエラー
        if (type != NbBatchOperationType.DELETE){
            for (Map.Entry<String,Object> entry : obj.entrySet()) {
                String key = entry.getKey();
                if ( !NbObject.isValidFieldName(key)) {
                    throw new IllegalArgumentException("Invalid field name : " + key);
                }
            }
        }

        // 更新、削除処理でObjectIDがない場合はエラー
        if (type != NbBatchOperationType.INSERT){
            if (object.getObjectId() == null) {
                throw new IllegalArgumentException("ObjectID is null");
            }
        }

        switch(type){
            case INSERT:
                // save
                ret.put(NbKey.OP, NbConsts.INSERT_OP);
                if (obj.containsKey(NbKey.ID)) ret.put(NbKey.ID, object.getObjectId());


                // 予約キーデータの削除
                obj.remove(NbKey.ETAG);
                obj.remove(NbKey.CREATED_AT);
                obj.remove(NbKey.UPDATED_AT);

                ret.put(NbKey.DATA, obj);

                break;
            case UPDATE:
                // full update
                ret.put(NbKey.OP, NbConsts.UPDATE_OP);
                ret.put(NbKey.ID, object.getObjectId());
                if (obj.containsKey(NbKey.ETAG)) ret.put(NbKey.ETAG, object.getETag());

                // 予約キーデータの削除
                obj.remove(NbKey.ID);
                obj.remove(NbKey.ETAG);
                obj.remove(NbKey.CREATED_AT);
                obj.remove(NbKey.UPDATED_AT);

                NbJSONObject fullUpdate = new NbJSONObject();
                fullUpdate.put("$full_update", obj);
                ret.put(NbKey.DATA, fullUpdate);

                break;
            case DELETE:
                // delete
                ret.put(NbKey.OP, NbConsts.DELETE_OP);
                ret.put(NbKey.ID, object.getObjectId());
                if (obj.containsKey(NbKey.ETAG)) ret.put(NbKey.ETAG, object.getETag());

                break;
            default:
                // do nothing
        }

        return ret;
    }

    protected void execBatch(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    protected NbRestResponseHandler makeResponseHandlerBatchOperation(final NbCallback<NbJSONArray> callback) {

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbObjectBucketImpl.executeBatchOperation()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                NbJSONArray resultList = body.getJSONArray(NbKey.RESULTS);

                // convert NbObject
                for (int i = 0; i<resultList.size(); i++){
                    NbJSONObject tmpObj = (NbJSONObject) resultList.get(i);

                    if (!tmpObj.containsKey(NbKey.DATA)){
                        continue;
                    }

                    NbObject object = newObject();
                    object.setCurrentParam((NbJSONObject) tmpObj.get(NbKey.DATA), true);

                    tmpObj.put(NbKey.DATA, object);
                }

                callback.onSuccess(resultList);
            }

            // onFailureで個別に処理する事項は無いため、super classで処理
        };
        return handler;
    }

}
