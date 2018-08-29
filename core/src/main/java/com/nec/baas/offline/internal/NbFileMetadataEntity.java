/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.json.*;
import com.nec.baas.offline.*;
import com.nec.baas.util.*;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * ファイルメタのデータクラス.
 * @since 1.0
 */
@Setter @Getter
public class NbFileMetadataEntity extends NbAclEntity {
    private String fileName;
    private NbSyncState metaState;
    private NbSyncState fileState;
    private String createdTime;
    private String updatedTime;
    private String metaETag;
    private String fileETag;
    private boolean cacheDisabled;
    private String publicUrl;
    private long fileSize;
    private String contentType;
    private String metaId;
    private int dbId;

    /*package*/ synchronized NbJSONObject toJSONObject() {

        NbJSONObject json = new NbJSONObject();

        json.put(NbKey.FILENAME, fileName);
        json.put(NbKey.CREATED_AT, createdTime);
        json.put(NbKey.UPDATED_AT, updatedTime);
        json.put(NbKey.META_ETAG, metaETag);
        json.put(NbKey.FILE_ETAG, fileETag);
        json.put(NbKey.CACHE_DISABLED, cacheDisabled);
        json.put(NbKey.PUBLIC_URL, publicUrl);
        json.put(NbKey.ACL, this.getAclJson());
        json.put(NbKey.LENGTH, fileSize);
        json.put(NbKey.CONTENT_TYPE, contentType);
        json.put(NbKey.ID, metaId);
        json.put(NbKey.META_STATE, metaState.id);
        json.put(NbKey.FILE_STATE, fileState.id);
        json.put(NbKey.DBID, dbId);

        return json;
    }

    /**
     * JSON → FileMetadataInfo変換
     */
    protected static synchronized NbFileMetadataEntity fromJson(@NonNull NbJSONObject json) {
        NbFileMetadataEntity info = new NbFileMetadataEntity();

        info.setFileName(json.optString(NbKey.FILENAME, null));
        info.setCreatedTime(json.optString(NbKey.CREATED_AT, null));
        info.setUpdatedTime(json.optString(NbKey.UPDATED_AT, null));
        info.setMetaETag(json.optString(NbKey.META_ETAG, null));
        info.setFileETag(json.optString(NbKey.FILE_ETAG, null));
        info.setCacheDisabled(json.optBoolean(NbKey.CACHE_DISABLED, false));
        info.setPublicUrl(json.optString(NbKey.PUBLIC_URL, null));
        info.setAclJson(json.getJSONObject(NbKey.ACL));
        info.setFileSize(json.optLong(NbKey.LENGTH, 0));
        info.setContentType(json.optString(NbKey.CONTENT_TYPE, null));
        info.setMetaId(json.optString(NbKey.ID, null));
        info.setDbId(json.optInt(NbKey.DBID, -1));

        //同期状態
        info.setMetaState(NbSyncState.fromObject(json.get(NbKey.META_STATE), NbSyncState.SYNC));
        info.setFileState(NbSyncState.fromObject(json.get(NbKey.FILE_STATE), NbSyncState.SYNC));

        return info;
    }
}
