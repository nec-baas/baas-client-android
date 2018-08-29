/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import com.nec.baas.core.*;
import com.nec.baas.http.*;
import com.nec.baas.json.*;
import com.nec.baas.object.*;
import com.nec.baas.offline.internal.*;
import com.nec.baas.util.*;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import okhttp3.Request;

/**
 * バケット共通の機能を提供するクラス。
 * @since 1.0
 */
@Accessors(prefix = "m")
public abstract class NbBaseBucketImpl<T extends NbBaseBucket> implements NbBaseBucket<T> {
    private static final NbLogger log = NbLogger.getLogger(NbBaseBucketImpl.class);

    protected static final String INVALID_BUCKET_MODE = "Invalid bucket mode. mode=";

    protected String mBucketName;
    protected NbBucketMode mMode;
    protected NbAcl mAcl;

    @Getter // for test
    protected String mApiUrl;

    @Getter @Setter // for test
    protected NbServiceImpl mNebulaService;

    @Getter @Setter // for test
    protected NbOfflineService mOfflineService;

    protected NbContentAcl mContentAcl;
    protected String mDescription;

    /**
     * コンストラクタ
     * @param bucketName バケット名
     * @param apiUrl API URL
     * @param mode バケットモード
     */
    protected NbBaseBucketImpl(@NonNull NbService service, @NonNull String bucketName, @NonNull String apiUrl, @NonNull NbBucketMode mode) {
        mBucketName = bucketName;
        mApiUrl = apiUrl;

        mNebulaService = (NbServiceImpl)service;
        mOfflineService = mNebulaService.getOfflineService();

        // オフライン無効にも関わらず、モードにオンラインが指定されていない場合
        if((mOfflineService == null) && mode != NbBucketMode.ONLINE) {
            throw new IllegalStateException(" invalid mode " + mode + " offline service is null");
        }
        mMode = mode;
    }

    public NbHttpRequestFactory getHttpRequestFactory() {
        return mNebulaService.getHttpRequestFactory();
    }

    protected void executeRequest(Request request, NbRestResponseHandler handler) {
        mNebulaService.createRestExecutor().executeRequest(request, handler);
    }

    /** {@inheritDoc} */
    public NbBucketMode getMode() {
        return mMode;
    }

    // for test only
    public void _setMode(NbBucketMode mode) {
        mMode = mode;
    }

    /** {@inheritDoc} */
    public String getBucketName() {
        return mBucketName;
    }

    /** {@inheritDoc} */
    public T setAcl(NbAcl acl) {
        if (acl != null) {
            mAcl = new NbAcl(acl);
        } else {
            mAcl = null;
        }
        return (T)this;
    }

    /** {@inheritDoc} */
    public NbAcl getAcl() {
        return mAcl;
    }

    /**
     * コンテンツACLを設定する。<p/>
     * save()を実行するまで設定は有効にならない。
     * @param contentAcl 設定するコンテンツACL。
     * @see NbObjectBucket#save(NbCallback)
     */
    public T setContentAcl(NbContentAcl contentAcl) {
        if (contentAcl != null) {
            mContentAcl = new NbContentAcl(contentAcl);
        } else {
            mContentAcl = null;
        }
        return (T)this;
    }

    /**
     * コンテンツACLを取得する。<p/>
     * @return 設定されたコンテンツACL。
     */
    public NbContentAcl getContentAcl() {
        return mContentAcl;
    }

    /**
     * バケットの説明文を取得する。
     * @return バケットの説明文
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * バケットの説明文を設定する。
     * @param description バケットの説明文
     */
    public T setDescription(String description) {
        mDescription = description;
        return (T)this;
    }

    /**
     * バケット情報設定に必要なリクエストbody情報を取得する。<br>
     * 呼び出し条件、本メソッド呼び出し前にACL、ContentAcl、descriptionの設定を<br>
     * 済ませておくこと。<br>
     * @return Jsonオブジェクト（body map）
     */
    protected NbJSONObject getBodyJson() {
        NbJSONObject bodyJson = new NbJSONObject();
        if (mAcl == null) {
            throw new IllegalStateException("No ACL");
        }
        if (mContentAcl == null) {
            throw new IllegalStateException("No ContentACL");
        }
        if (mDescription == null) {
            throw new IllegalStateException("No Description");
        }
        bodyJson.put(NbKey.ACL, mAcl.toJsonObject());
        bodyJson.put(NbKey.CONTENT_ACL, mContentAcl.toJsonObject());
        bodyJson.put(NbKey.DESCRIPTION, mDescription);
        return bodyJson;
    }
}
