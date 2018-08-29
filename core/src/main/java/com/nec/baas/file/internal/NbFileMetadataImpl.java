/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.file.internal;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.file.*;
import com.nec.baas.http.*;
import com.nec.baas.json.*;
import com.nec.baas.offline.*;
import com.nec.baas.util.*;

import java.util.Map;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import okhttp3.Request;
import okhttp3.Response;

/**
 * NbFileMetadata 実装
 */
@Accessors(chain = true, prefix = "m")
@Getter
@Setter
public class NbFileMetadataImpl implements NbFileMetadata {
    private static final NbLogger log = NbLogger.getLogger(NbFileMetadataImpl.class);

    private static final String NO_OFFLINE_MODE = "Offline mode is not enabled.";

    private String mFileName;
    private String mContentType;

    @Setter(AccessLevel.NONE)
    private NbAcl mAcl;

    private long mLength;
    private String mPublishUrl;
    private String mCreatedTime;
    private String mUpdatedTime;

    private NbServiceImpl mService;

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private String mBucketName;

    @Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.NONE)
    private String mApiUrl; //mApiUrlは初期化時とsave成功時に更新する


    //メタETag
    private String mMetaETag = null;

    //ファイルETag
    private String mFileETag = null;

    //メタデータID
    private String mMetaId = null;

    // オプション情報(JSON)
    private NbJSONObject mOptions = null;

    //キャッシュ禁止フラグ
    private boolean mCacheDisabled = false;

    //メタデータ同期状態
    private NbSyncState mMetaState = NbSyncState.NOSTATE;

    //ファイル同期状態
    private NbSyncState mFileState = NbSyncState.NOSTATE;

    //データベースID
    private int mDbId = -1;

    protected NbFileMetadataImpl(NbService service, String bucketName, String filename, String contentType, NbAcl acl) {
        this(bucketName, filename, contentType, acl);
        mService = (NbServiceImpl)service;
    }

    private NbFileMetadataImpl(String bucketName, String filename, String contentType, NbAcl acl) {
        mBucketName = bucketName;
        this.mFileName = filename;
        this.mContentType = contentType;
        this.mAcl = acl;

        mApiUrl = getApiUrl(mFileName);
    }

    private String getApiUrl(String filename) {
        return String.format("%s/%s/%s/%s", NbConsts.FILES_PATH,
                NbUtil.encodeUrl(mBucketName), NbUtil.encodeUrl(filename), NbConsts.META_PATH_COMPONENT);
    }

    private NbHttpRequestFactory getHttpRequestFactory() {
        return mService.getHttpRequestFactory();
    }

    private void executeRequest(Request request, NbRestResponseHandler handler) {
        mService.createRestExecutor().executeRequest(request, handler);
    }

    /** {@inheritDoc} */
    @Override
    @Deprecated
    public void save(final NbFileMetadataCallback callback) {
        save(FileMetadataCallbackListToSingleWrapper.wrap(callback));
    }

    /** {@inheritDoc} */
    @Override
    public void save(@NonNull final NbCallback<NbFileMetadata> callback) {
        log.fine("save() <start>");

        saveOnline(callback);
        log.fine("save() <end>");
    }

    protected void execSave(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /**
     * メタデータの保存(オンライン)
     */
    private void saveOnline(final NbCallback<NbFileMetadata> callback) {
        Request request;
        log.fine("saveOnline() update file.");
        request = getPutRequest(callback);
        if (request == null) {
            log.fine("saveOnline() ERR not make put request.");
            return;
        }

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbFileMetadata.save()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                setMetadata(body);
                //save成功なのでmApiUrlを更新する
                if (mFileName != null) {
                    mApiUrl = getApiUrl(mFileName);
                }

                callback.onSuccess(NbFileMetadataImpl.this);
            }
            @Override
            public void onFailure(int status, NbJSONObject json) {
                log.severe("save() onFailure() ERR status=" + status);
                callback.onFailure(status, new NbErrorInfo(String.valueOf(json)));
            }
        };
        execSave(request, handler);
    }

    private Request getPutRequest(final NbCallback<NbFileMetadata> callback) {
        //リクエスト作成
        NbJSONObject bodyJson = new NbJSONObject();
        if (mFileName != null) {
            bodyJson.put(NbKey.FILENAME, mFileName);
        }
        if (mContentType != null) {
            bodyJson.put(NbKey.CONTENT_TYPE, mContentType);
        }
        if (mAcl != null) {
            bodyJson.put(NbKey.ACL, mAcl.toJsonObject());
        }
        if (mOptions != null) {
            bodyJson.put(NbKey.OPTIONS, mOptions);
        }

        //更新する値が無い場合はRest APIを実行しない
        if (bodyJson.isEmpty()) {
            log.severe("getPutRequest() <end> ERR param invalid");
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("Empty JSON"));
            return null;
        }

        Request request = getHttpRequestFactory()
                .put(mApiUrl)
                .body(bodyJson)
                .param(NbKey.META_ETAG, getMetaETag())
                .build();

        return request;
    }

    /*
     * メタデータの設定
     */
    protected void setMetadata(Map<String, Object> body) {
        setFileName((String) body.get(NbKey.FILENAME));
        setContentType((String) body.get(NbKey.CONTENT_TYPE));
        setCreatedTime((String) body.get(NbKey.CREATED_AT));
        setUpdatedTime((String) body.get(NbKey.UPDATED_AT));
        setLength(((Number) body.get(NbKey.LENGTH)).longValue());
        setPublishUrl((String) body.get(NbKey.PUBLIC_URL));

        NbAcl acl = new NbAcl((Map<String, Object>) body.get(NbKey.ACL));
        setAcl(acl);

        setMetaETag((String) body.get(NbKey.META_ETAG));
        setFileETag((String) body.get(NbKey.FILE_ETAG));

        Map<String, Object> options = (Map<String, Object>) body.get(NbKey.OPTIONS);
        setOptions(options != null ? new NbJSONObject(options) : null);

        setCacheDisabled(body.get(NbKey.CACHE_DISABLED) != null && Boolean.parseBoolean(body.get(NbKey.CACHE_DISABLED).toString()));

        setMetaId((String) body.get(NbKey.ID));
        setDbId((body.get(NbKey.DBID) == null)
                ? -1 : Integer.parseInt(body.get(NbKey.DBID).toString()));

        //↓同期状態はレスポンスのフィールドには存在しない。しかし、メタデータ取得等で
        //アプリ側へ返却する際にもコールされるためnullチェックの上で設定する。
        setMetaState(NbSyncState.fromObject(body.get(NbKey.META_STATE), NbSyncState.SYNC));
        setFileState(NbSyncState.fromObject(body.get(NbKey.FILE_STATE), NbSyncState.SYNC));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized NbJSONObject toJSONObject() {

        NbJSONObject json = new NbJSONObject();

        json.put(NbKey.FILENAME, getFileName());
        json.put(NbKey.CREATED_AT, getCreatedTime());
        json.put(NbKey.UPDATED_AT, getUpdatedTime());
        json.put(NbKey.META_ETAG, getMetaETag());
        json.put(NbKey.FILE_ETAG, getFileETag());
        json.put(NbKey.CACHE_DISABLED, isCacheDisabled());
        json.put(NbKey.PUBLIC_URL, getPublishUrl());
        json.put(NbKey.ACL, getAcl().toJsonObject());
        if (getOptions() != null) {
            json.put(NbKey.OPTIONS, getOptions());
        }
        json.put(NbKey.LENGTH, getLength());
        json.put(NbKey.CONTENT_TYPE, getContentType());
        json.put(NbKey.ID, getMetaId());
        json.put(NbKey.DBID, getDbId());
        json.put(NbKey.META_STATE, getMetaState().id);
        json.put(NbKey.FILE_STATE, getFileState().id);

        log.fine("makeFileMetadataMap()"
                        + "  isCacheDisable()=" +  isCacheDisabled()
                        + "  getLength()=" +  getLength()
                        + "  getContentType()=" +  getContentType()
                        + "  getMetadataId()=" +  getMetaId()
        );

//      log.fine("makeFileMetadataMap() mapData=" + mapData);

        return json;
    }

    /** {@inheritDoc} */
    @Override
    public NbFileMetadata setAcl(NbAcl acl) {
        if (acl != null) {
            this.mAcl = new NbAcl(acl);
        } else {
            this.mAcl = null;
        }
        return this;
    }

    /**
     * ファイルのメタデータを作成する (内部IF)。
     * @param json JSON Object
     */
    public void setCurrentParam(NbJSONObject json) {
        log.fine("setCarrentParam() json:{0}", json);

        setFileName(json.getString(NbKey.FILENAME));
        setContentType(json.getString(NbKey.CONTENT_TYPE));

        NbAcl acl = new NbAcl(json.get(NbKey.ACL));
        setAcl(acl);

        setLength(json.getNumber(NbKey.LENGTH).longValue());
        setPublishUrl(json.getString(NbKey.PUBLIC_URL));
        setCreatedTime(json.getString(NbKey.CREATED_AT));
        setUpdatedTime(json.getString(NbKey.UPDATED_AT));
        setMetaETag(json.getString(NbKey.META_ETAG));
        setFileETag(json.getString(NbKey.FILE_ETAG));
        setOptions(json.getJSONObject(NbKey.OPTIONS));

        setCacheDisabled(json.optBoolean(NbKey.CACHE_DISABLED, false));
        setMetaId(json.getString(NbKey.ID));
        setDbId(json.optInt(NbKey.DBID, -1));

        //↓同期状態はレスポンスのフィールドには存在しない。しかし、メタデータ取得等で
        //アプリ側へ返却する際にもコールされるためnullチェックの上で設定する。
        setMetaState(NbSyncState.fromObject(json.get(NbKey.META_STATE), NbSyncState.SYNC));
        setFileState(NbSyncState.fromObject(json.get(NbKey.FILE_STATE), NbSyncState.SYNC));
    }
}
