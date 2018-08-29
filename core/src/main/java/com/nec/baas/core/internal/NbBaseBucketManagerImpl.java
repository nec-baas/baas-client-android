/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import com.nec.baas.core.*;
import com.nec.baas.http.*;
import com.nec.baas.json.*;
import com.nec.baas.offline.internal.*;
import com.nec.baas.util.*;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import okhttp3.Request;
import okhttp3.Response;

/**
 * バケット共通管理クラス。<p/>
 *
 * @since 1.0
 */
@Accessors(prefix = "m")
public abstract class NbBaseBucketManagerImpl<T extends NbBaseBucket> {
    private static final NbLogger log = NbLogger.getLogger(NbBaseBucketManagerImpl.class);

    protected static final String NO_OFFLINE_MODE = "Offline mode is not enabled.";
    protected static final String INVALID_BUCKET_MODE = "Invalid bucket mode. ";
    protected static final String INVALID_BUCKET_NAME = "Invalid bucket name.";

    @Getter @Setter // for test
    protected NbServiceImpl mNebulaService;
    @Getter @Setter // for test
    protected NbOfflineService mOfflineService;

    @Getter  // for test
    protected String mBucketUrl;

    protected NbBaseBucketManagerImpl(NbService service, String bucketUrl) {
        mNebulaService = (NbServiceImpl)service;
        mOfflineService = mNebulaService.getOfflineService();
        mBucketUrl = bucketUrl;
    }

    // UT 用
    public NbServiceImpl _getService() {
        return mNebulaService;
    }

    public NbHttpRequestFactory getHttpRequestFactory() {
        return mNebulaService.getHttpRequestFactory();
    }

    protected void executeRequest(Request request, NbRestResponseHandler handler) {
        mNebulaService.createRestExecutor().executeRequest(request, handler);
    }

    /**
     * Bucket インスタンスを生成する
     * (実装クラス側でオーバライドする)
     * @param bucketName バケット名
     * @param bucketMode バケットモード
     * @return Bucket
     */
    abstract protected T newBucket(String bucketName, NbBucketMode bucketMode);

    /**
     * バケットの作成を行う。
     * <p>
     * description、ACL、contentAclはオプションのため指定しなくても良い。
     * バケットの作成にはROOTバケットに対するcreate権限が必要となる。
     * 本メソッドはレプリカ・ローカルモードでのバケット情報更新は不可とする。
     * </p>
     * @param bucketName 作成するバケットの名前
     * @param description バケットの説明文
     * @param acl バケットに設定するACL
     * @param contentAcl バケットに設定するコンテンツACL
     * @param callback 作成したバケットを取得するコールバック。
     */
    public void createBucket(@NonNull final String bucketName, String description, NbAcl acl,
                             NbContentAcl contentAcl, @NonNull final NbCallback<T> callback) {

        //バケット名チェック
        if (!isValidBucketName(bucketName)) {
            throw new IllegalArgumentException(INVALID_BUCKET_NAME);
        }

        //リクエスト生成
        Request request = makeRequestCreateBucket(bucketName, acl, contentAcl, description);

        //レスポンスハンドラ生成
        NbRestResponseHandler handler = makeResponseHandlerCreateBucket(bucketName, callback);
        //リクエスト実行
        execCreateBucket(request, handler);
    }

    /**
     * バケット作成のリクエスト実行
     */
    protected void execCreateBucket(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /**
     * バケット作成のリクエスト生成
     */
    protected Request makeRequestCreateBucket(
            String bucketName, NbAcl acl, NbContentAcl contentAcl, String description) {
        NbJSONObject json = new NbJSONObject();
        String requestBody = null;

        if (acl != null) {
            json.put(NbKey.ACL, acl.toJsonObject());
        }
        if (contentAcl != null) {
            json.put(NbKey.CONTENT_ACL, contentAcl.toJsonObject());
        }
        if (description != null) {
            json.put(NbKey.DESCRIPTION, description);
        } else {
            json.put(NbKey.DESCRIPTION, "");
        }
        if (!json.isEmpty()) {
            requestBody = json.toJSONString();
        }
        String apiUrl = mBucketUrl + "/" + bucketName;
        Request request = getHttpRequestFactory().put(apiUrl).body(requestBody).build();

        return request;
    }

    /**
     * バケット作成のレスポンスハンドラ生成
     */
    abstract protected NbRestResponseHandler makeResponseHandlerCreateBucket(
            final String bucketName, final NbCallback<T> callback);

    /**
     * サーバ上のバケット一覧を取得する。
     * <p>
     * ROOTバケットに対するread権限が必要となる。
     * </p>
     * @param callback バケット名を取得するコールバック。
     */
    public void getBucketList(@NonNull final NbCallback<List<T>> callback) {

        //リクエスト生成
        Request request = makeRequestGetBucketList();

        //レスポンスハンドラ生成
        NbRestResponseHandler handler = makeResponseHandlerGetBucketList(callback);

        //リクエスト実行
        execGetBucketList(request, handler);

    }

    /**
     * ローカルDB上のバケット一覧を取得する。
     */
    public List<T> getBucketList() {
        return getBucketListFromCache();
    }

    /**
     * バケット一覧取得のリクエスト実行
     */
    protected void execGetBucketList(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /**
     * バケット一覧取得のレスポンスハンドラ生成
     */
    abstract protected NbRestResponseHandler makeResponseHandlerGetBucketList(NbCallback<List<T>> callback);

    /**
     * JSON results から Bucket List に変換
     */
    protected List<T> _convertJsonResultsToBucketList(NbJSONObject body, NbBucketMode bucketMode) {
        List<T> resultBuckets = new ArrayList<>();
        List<NbJSONObject> bucketList = (List<NbJSONObject>) body.get(NbKey.RESULTS);
        if (bucketList != null) {
            for (NbJSONObject bucketJson : bucketList) {
                resultBuckets.add(makeBucket(bucketJson, bucketMode));
            }
        }
        return resultBuckets;
    }

    /**
     * JSON results から Bucket List に変換
     * バケットモード情報は JSON results の中に含まれている
     */
    protected List<T> _convertJsonResultsToBucketList(NbJSONObject body) {
        List<T> resultBuckets = new ArrayList<>();
        List<NbJSONObject> bucketList = (List<NbJSONObject>) body.get(NbKey.RESULTS);
        if (bucketList != null) {
            for (NbJSONObject bucketJson : bucketList) {
                NbBucketMode bucketMode = NbBucketMode.fromObject(bucketJson.get(NbKey.BUCKET_MODE));
                resultBuckets.add(makeBucket(bucketJson, bucketMode));
            }
        }
        return resultBuckets;
    }

    /**
     * ローカルDBへバケット情報格納
     */
    abstract protected void execCacheBucket(T bucket, NbJSONObject bodyJson);

    private void cacheBucketSub(T bucket) {
        NbJSONObject json = new NbJSONObject();
        json.put(NbKey.ACL, bucket.getAcl().toJsonObject());
        json.put(NbKey.CONTENT_ACL, bucket.getContentAcl().toJsonObject());
        json.put(NbKey.DESCRIPTION, bucket.getDescription());
        json.put(NbKey.NAME, bucket.getBucketName());
        json.put(NbKey.BUCKET_MODE, bucket.getMode().idString());
        execCacheBucket(bucket, json);
    }

    /**
     * バケットの情報をローカルDBに格納する
     * @param bucket バケット名
     */
    public void cacheBucket(T bucket) {
        if (mOfflineService != null) {
            cacheBucketSub(bucket);
        }
    }

    /**
     * バケット一覧取得のリクエスト生成
     */
    private Request makeRequestGetBucketList() {
        //リクエスト生成
        return getHttpRequestFactory().get(mBucketUrl).build();
    }

    /**
     * JSON → Bucket変換
     */
    protected T makeBucket(NbJSONObject body, NbBucketMode bucketMode) {
        T bucket = newBucket(body.getString(NbKey.NAME), bucketMode);

        NbAcl acl = new NbAcl(body.getJSONObject(NbKey.ACL));
        bucket.setAcl(acl);

        NbContentAcl contentAcl = new NbContentAcl(body.getJSONObject(NbKey.CONTENT_ACL));
        bucket.setContentAcl(contentAcl);

        bucket.setDescription(body.getString(NbKey.DESCRIPTION));

        return bucket;
    }

    /**
     * バケットを取得する。サーバに対する問い合わせは行わない。
     * @param bucketName バケット名
     * @param bucketMode バケットモード
     * @return バケット
     */
    public T getBucket(@NonNull final String bucketName, @NonNull final NbBucketMode bucketMode) {

        //バケット名チェック
        if (!isValidBucketName(bucketName)) {
            throw new IllegalArgumentException(INVALID_BUCKET_NAME);
        }

        T baseBucket = null;
        switch(bucketMode) {
            case ONLINE:
                baseBucket = newBucket(bucketName, bucketMode);
                break;
            case REPLICA:
            case LOCAL:
                if (mOfflineService == null) {
                    throw new IllegalStateException(NO_OFFLINE_MODE);
                }
                baseBucket = getBucketFromCache(bucketName, bucketMode);
                break;
            default:
                throw new IllegalArgumentException(INVALID_BUCKET_MODE + bucketMode);
        }
        return baseBucket;
    }

    /**
     * バケット情報を取得する。サーバに対する非同期問い合わせが発生する。
     * <p>
     * ROOTバケットおよび対象バケットに対するread権限が必要となる。
     * バケット情報の取得に成功した場合、ローカルDBに情報をキャッシュする。
     * </p>
     * @param bucketName 情報を取得するバケットの名前。
     * @param bucketMode バケットモード。
     * @param callback バケットを取得するコールバック。
     */
    public void getBucket(@NonNull final String bucketName, @NonNull final NbBucketMode bucketMode, @NonNull final NbCallback<T> callback) {

        //バケット名チェック
        if (!isValidBucketName(bucketName)) {
            throw new IllegalArgumentException(INVALID_BUCKET_NAME);
        }

        if ((bucketMode == NbBucketMode.ONLINE)||(bucketMode == NbBucketMode.REPLICA)){

            if (bucketMode == NbBucketMode.REPLICA) {
                if (mOfflineService== null) {
                    throw new IllegalStateException(NO_OFFLINE_MODE);
                } else if (isBucketExistsInCache(bucketName, NbBucketMode.LOCAL)) {
                    // ローカルモードの同名バケットが存在する場合
                    callback.onFailure(NbStatus.INTERNAL_SERVER_ERROR, new NbErrorInfo("Bucket of the same name already exists."));
                    return;
                }
            }

            //リクエスト生成
            Request request = makeRequestGetBucket(bucketName);

            //ハンドラ生成
            NbRestResponseHandler handler = makeResponseHandlerGetBucket(bucketName, bucketMode,callback);

            //リクエスト実行
            execGetBucket(request, handler);

        }else{
            throw new IllegalArgumentException(INVALID_BUCKET_MODE + bucketMode);
        }

    }

    /**
     * バケット取得のリクエスト実行
     */
    protected void execGetBucket(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /**
     * バケット取得のレスポンスハンドラ生成
     */
    abstract protected NbRestResponseHandler
    makeResponseHandlerGetBucket(final String bucketname,
                                 final NbBucketMode bucketMode,
                                 final NbCallback<T> callback);

    /**
     * バケット取得のリクエスト生成
     */
    private Request makeRequestGetBucket(String bucketName) {
        //リクエスト生成
        return getHttpRequestFactory().get(mBucketUrl).addPathComponent(bucketName).build();
    }

    /**
     * ローカルDBからバケット一覧取得
     */
    abstract protected List<T> getBucketListFromCache();

    /**
     * ローカルDBからバケット取得
     */
    abstract protected T getBucketFromCache(final String bucketName, NbBucketMode bucketMode);

    /**
     * サーバ上のバケットを削除する。
     * <p>
     * ROOTバケットおよび対象バケットに対するdelete権限が必要となる。
     * バケットの削除に成功した場合、ローカルDBからバケット情報を削除する。
     * </p>
     * @param bucketName 削除するバケットの名前。
     * @param callback 実行結果を取得するコールバック。
     */
    public void deleteBucket(@NonNull final String bucketName, @NonNull final NbResultCallback callback) {

        //バケット名チェック
        if (!isValidBucketName(bucketName)) {
            throw new IllegalArgumentException(INVALID_BUCKET_NAME);
        }

        //リクエスト生成
        Request request = makeRequestDeleteBucket(bucketName);

        //ハンドラ生成
        NbRestResponseHandler handler = makeResponseHandlerDeleteBucket(bucketName, callback);

        //リクエスト実行
        execDeleteBucket(request, handler);
    }

    /**
     * ローカルDB上のバケットを削除する。
     * <p>
     * 対象バケットに対するdelete権限が必要となる。
     * </p>
     * @param bucketName 削除するバケットの名前。
     */
    public void deleteBucket(@NonNull final String bucketName) {

        //バケット名チェック
        if (!isValidBucketName(bucketName)) {
            throw new IllegalArgumentException(INVALID_BUCKET_NAME);
        }

        if (mOfflineService == null) {
            throw new IllegalStateException(NO_OFFLINE_MODE);
        }

        int result = execRemoveBucketCache(bucketName, true);

        if (NbStatus.isNotSuccessful(result)) {
            // delete権限なし等でバケットが削除できない場合
            throw new IllegalStateException("Failed to deleteBucket. result = " + result);
        }
    }

    /**
     * バケット削除のリクエスト実行
     */
    protected void execDeleteBucket(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /**
     * バケット削除のレスポンスハンドラ生成
     */
    private NbRestResponseHandler makeResponseHandlerDeleteBucket(
            final String bucketName, final NbResultCallback callback) {
        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(
                callback, "BasicBucketManager.deleteBucket()") {
            @Override
            public void onSuccess(Response response) {
                if (mOfflineService != null && isBucketExistsInCache(bucketName, NbBucketMode.REPLICA)) {
                    // レプリカモードの同名バケットは削除するが、ローカルモードの同名バケットは削除しない
                    // サーバ側と一致させるため、無条件にバケットを削除する
                    execRemoveBucketCache(bucketName, false);
                }
                callback.onSuccess();
            }
        };

        return handler;
    }

    /**
     * ローカルDBからバケット情報削除
     */
    abstract protected int execRemoveBucketCache(String bucketName, boolean isChecked);

    /**
     * バケット削除のリクエスト生成
     */
    private Request makeRequestDeleteBucket(String bucketName) {
        //リクエスト生成
        Request request = getHttpRequestFactory().delete(mBucketUrl).addPathComponent(bucketName).build();

        return request;
    }

    /**
     * バケット名が使用できるかチェックする。<br>
     * チェック項目はnullチェック、0文字チェックを行う。
     * @param bucketName
     * @return 使用可能であればtrue、使用不可であればfalse
     */
    protected boolean isValidBucketName(String bucketName) {
        if ((bucketName == null) || bucketName.isEmpty()) {
            return false;
        }

        return true;

    }

    /**
     * オフラインサービスを設定する
     */
    public void setOfflineService(){
        mOfflineService = mNebulaService.getOfflineService();
    }

    /**
     * ローカルDBに同じバケット名、バケットモードのバケットが存在するかを確認する。<br>
     * @param bucketName バケット名
     * @param bucketMode バケットモード
     * @return ローカルDBに同じバケット名、バケットモードのバケットが存在する場合はtrueを返却する。
     */
    abstract protected boolean isBucketExistsInCache(String bucketName, NbBucketMode bucketMode);
}
