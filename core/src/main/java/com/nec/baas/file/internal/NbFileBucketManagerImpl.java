/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.file.internal;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.file.*;
import com.nec.baas.json.*;
import com.nec.baas.util.*;

import java.util.List;

import okhttp3.Request;
import okhttp3.Response;

/**
 * ファイルバケットマネージャ実装
 */
public class NbFileBucketManagerImpl extends NbBaseBucketManagerImpl<NbFileBucket> implements NbFileBucketManager {
    private static final NbLogger log = NbLogger.getLogger(NbFileBucketManager.class);

    //private static final String NO_OFFLINE_MODE = "Offline mode is not enabled.";

    public NbFileBucketManagerImpl() {
        this(NbService.getInstance());
    }

    public NbFileBucketManagerImpl(NbService service) {
        super(service, NbConsts.FILE_BUCKET_PATH);

        // 2015 7EではOfflineService無効
        mOfflineService = null;
    }

    @Override
    protected NbFileBucket newBucket(String bucketName, NbBucketMode mode) {
        return new NbFileBucketImpl(mNebulaService, bucketName, mode);
    }

    protected void execGetBucketList(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /** {@inheritDoc} */
    @Override
    public NbFileBucket getBucket(final String bucketName) {
        return newBucket(bucketName, NbBucketMode.ONLINE);
    }

    /** {@inheritDoc} */
    @Override
    public void getBucket(final String bucketName, final NbCallback<NbFileBucket> callback) {
        super.getBucket(bucketName, NbBucketMode.ONLINE, callback);
    }

    @Override
    protected int execRemoveBucketCache(String bucketName, boolean isChecked) {
        //mOfflineService.fileService().removeBucketCache(bucketName);
        return NbStatus.OK;
    }

    @Override
    protected NbRestResponseHandler makeResponseHandlerCreateBucket(
            final String bucketName, final NbCallback<NbFileBucket> callback) {
        //ハンドラ生成
        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback,
                "NbFileBucketManager.createBucket") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                final NbFileBucket bucket = jsonToBucket(body, NbBucketMode.ONLINE);
                callback.onSuccess(bucket);
            }
        };

        return handler;
    }

    @Override
    protected NbRestResponseHandler makeResponseHandlerGetBucketList(
            final NbCallback<List<NbFileBucket>> callback) {
        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback,
                "NbFileBucketManager.getBucketList") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                List<NbFileBucket> resultBuckets = _convertJsonResultsToBucketList(body, NbBucketMode.ONLINE);
                callback.onSuccess(resultBuckets);
            }
        };

        return handler;

    }

    @Override
    protected void execCacheBucket(NbFileBucket bucket, NbJSONObject bodyJson) {
        //mOfflineService.fileService().saveBucketCache(bucket.getBucketName(), bodyJson.toJSONString());
    }

    @Override
    protected NbRestResponseHandler makeResponseHandlerGetBucket(
            final String bucketName, final NbBucketMode bucketMode,final NbCallback<NbFileBucket> callback) {
        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(
                callback, "NbFileBucketManager.getBucket()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                final NbFileBucket bucket = jsonToBucket(body, bucketMode);
//                cacheBucket(bucket);
                callback.onSuccess(bucket);
            }
        };

        return handler;
    }

    @Override
    protected List<NbFileBucket> getBucketListFromCache() {
        return null;
    }

    @Override
    protected NbFileBucket getBucketFromCache(String bucketName, NbBucketMode bucketMode) {
        return null;
    }

    @Override
    protected boolean isBucketExistsInCache(String bucketName, NbBucketMode bucketMode) {
        return false;
    }

}
