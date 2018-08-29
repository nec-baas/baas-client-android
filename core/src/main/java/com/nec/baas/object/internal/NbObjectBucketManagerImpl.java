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
import com.nec.baas.util.*;

import java.util.ArrayList;
import java.util.List;

import lombok.NonNull;
import okhttp3.Request;
import okhttp3.Response;

/**
 * オブジェクトバケット管理クラス。
 * <p/>
 *
 * {@link NbServiceImpl#objectBucketManager()} でインスタンスを取得する。
 *
 * @since 1.0
 */
public class NbObjectBucketManagerImpl extends NbBaseBucketManagerImpl<NbObjectBucket> implements NbObjectBucketManager {

    private static final String ACL_ANONYMOUS = "g:anonymous";

    public NbObjectBucketManagerImpl(NbService service) {
        super(service, NbConsts.OBJECT_BUCKET_PATH);
    }

    /** {@inheritDoc} */
    @Override
    protected NbObjectBucket newBucket(String bucketName, NbBucketMode bucketMode) {
        return new NbObjectBucketImpl(mNebulaService, bucketName, bucketMode);
    }

    protected void execGetBucketList(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /** {@inheritDoc} */
    @Override
    public NbObjectBucket getBucket(final String bucketName) {
        return getBucket(bucketName, NbBucketMode.ONLINE);
    }

    /** {@inheritDoc} */
    @Override
    public void getBucket(final String bucketName, final NbCallback<NbObjectBucket> callback) {
        getBucket(bucketName, NbBucketMode.ONLINE, callback);
    }

    /** {@inheritDoc} */
    @Override
    public NbObjectBucket getBucket(final String bucketName, final NbBucketMode bucketMode) {
        return super.getBucket(bucketName, bucketMode);
    }

    @Override
    protected int execRemoveBucketCache(String bucketName, boolean isChecked) {
        return mOfflineService.objectService().removeBucketCache(bucketName, isChecked);
    }

    //オフライン処理

    /** {@inheritDoc} */
    @Override
    public void sync(@NonNull final NbResultCallback callback) {

        //オフライン機能未サポート時はエラー返却
        if (mOfflineService == null) {
            throw new IllegalStateException(NO_OFFLINE_MODE);
        }

        NbUtil.runInBackground(new Runnable() {
            public void run() {
                execSync(callback);
            }
        });
    }

    private void execSync(final NbResultCallback callback) {
        final int result = mOfflineService.objectService().sync();

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

    protected void execDeleteBucket(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }


    @Override
    protected NbRestResponseHandler makeResponseHandlerCreateBucket(final String bucketName, final NbCallback<NbObjectBucket> callback) {
        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback,
                "NbObjectBucketManagerImpl.createBucket") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {

                final NbObjectBucket bucket = makeBucket(body, NbBucketMode.ONLINE);

                callback.onSuccess(bucket);
            }
        };

        return handler;
    }

    @Override
    protected NbRestResponseHandler makeResponseHandlerGetBucketList(final NbCallback<List<NbObjectBucket>> callback) {
        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback,
                "NbObjectBucketManagerImpl.getBucketList") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                List<NbObjectBucket> resultBuckets = _convertJsonResultsToBucketList(body, NbBucketMode.ONLINE);
                callback.onSuccess(resultBuckets);
            }
        };

        return handler;

    }

    @Override
    protected void execCacheBucket(NbObjectBucket bucket, NbJSONObject bodyJson) {
        mOfflineService.objectService().saveBucketCache(bucket.getBucketName(), bodyJson.toJSONString());
    }

    @Override
    protected NbRestResponseHandler makeResponseHandlerGetBucket(final String bucketName, final NbBucketMode bucketMode, final NbCallback<NbObjectBucket> callback) {
        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(
                callback, "NbObjectBucketManagerImpl.getBucket()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                final NbObjectBucket bucket = makeBucket(body, bucketMode);

                if (bucketMode == NbBucketMode.REPLICA) {
                    // 同名のローカルモードのバケットが存在するかを確認
                    if (!isBucketExistsInCache(bucketName, NbBucketMode.LOCAL)) {
                        cacheBucket(bucket);
                        callback.onSuccess(bucket);
                    } else {
                        callback.onFailure(NbStatus.INTERNAL_SERVER_ERROR, new NbErrorInfo("Bucket of the same name already exists."));
                    }
                } else {
                    // onlineモードの場合
                    callback.onSuccess(bucket);
                }
            }
        };

        return handler;
    }

    @Override
    protected NbObjectBucket getBucketFromCache(final String bucketName, NbBucketMode bucketMode) {

        NbObjectBucket resultBucket = null;

        if (mOfflineService == null){
            throw new IllegalStateException(NO_OFFLINE_MODE);
        }

        //オフライン処理
        NbOfflineResult bucket = mOfflineService.objectService().readLocalBucket(bucketName);

        if (NbStatus.isSuccessful(bucket.getStatusCode())) {
            // バケットモードをチェック
            NbJSONObject json = bucket.getJsonData();
            if (bucketMode == NbBucketMode.fromObject(json.get(NbKey.BUCKET_MODE))) {
                resultBucket = makeBucket(bucket.getJsonData(), bucketMode);
            } else {
                throw new IllegalStateException("Bucket of the same name already exists.");
            }
        } else if (bucket.getStatusCode() == NbStatus.NOT_FOUND) {

            // バケットがない場合は、ACLフルアクセスのバケットを作成する

            NbObjectBucket newBucket = newBucket(bucketName, bucketMode);

            NbAcl acl = new NbAcl();
            acl.addEntry(NbAclPermission.CREATE, ACL_ANONYMOUS);
            acl.addEntry(NbAclPermission.WRITE, ACL_ANONYMOUS);
            acl.addEntry(NbAclPermission.READ, ACL_ANONYMOUS);
            acl.addEntry(NbAclPermission.UPDATE, ACL_ANONYMOUS);
            acl.addEntry(NbAclPermission.DELETE, ACL_ANONYMOUS);
            acl.addEntry(NbAclPermission.ADMIN, ACL_ANONYMOUS);
            newBucket.setAcl(acl);

            NbContentAcl contentAcl = new NbContentAcl();
            contentAcl.addEntry(NbAclPermission.CREATE, ACL_ANONYMOUS);
            contentAcl.addEntry(NbAclPermission.WRITE, ACL_ANONYMOUS);
            contentAcl.addEntry(NbAclPermission.READ, ACL_ANONYMOUS);
            contentAcl.addEntry(NbAclPermission.UPDATE, ACL_ANONYMOUS);
            contentAcl.addEntry(NbAclPermission.DELETE, ACL_ANONYMOUS);
            newBucket.setContentAcl(contentAcl);

            // createBucket()でdescriptionの指定がない場合、空文字が入るので、それに合わせる
            newBucket.setDescription("");

            cacheBucket(newBucket);

            //作成したインスタンスを返す
            resultBucket = getBucketFromCache(bucketName, bucketMode);

        }

        return resultBucket;
    }

    @Override
    protected List<NbObjectBucket> getBucketListFromCache() {

        List<NbObjectBucket> resultBuckets = new ArrayList<>();

        if (mOfflineService == null){
            throw new IllegalStateException(NO_OFFLINE_MODE);
        }

        //オフライン処理
        NbOfflineResult buckets = mOfflineService.objectService().readLocalBucketList();
        if (NbStatus.isSuccessful(buckets.getStatusCode())) {
            NbJSONObject body = buckets.getJsonData();
            resultBuckets = super._convertJsonResultsToBucketList(body);
        }

        return resultBuckets;
    }

    @Override
    protected boolean isBucketExistsInCache(String bucketName, NbBucketMode bucketMode) {
        return mOfflineService.objectService().isBucketExists(bucketName, bucketMode);
    }
}
