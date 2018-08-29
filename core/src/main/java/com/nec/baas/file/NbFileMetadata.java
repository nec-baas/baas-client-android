/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.file;

import com.nec.baas.core.*;
import com.nec.baas.json.*;
import com.nec.baas.offline.*;

/**
 * ファイルメタデータ。
 * @since 1.0
 */
public interface NbFileMetadata {
    /**
     * メタデータの保存を行う。
     * @deprecated {@link #save(NbCallback)} で置き換え
     * @since 1.0
     */
    @Deprecated
    void save(final NbFileMetadataCallback callback);

    /**
     * メタデータの保存を行う。
     * 対象バケットと対象ファイルのupdate権限が必要となる。
     * @param callback 保存結果を受け取るコールバック。
     * @sincle 6.2.0
     */
    void save(final NbCallback<NbFileMetadata> callback);

    /**
     * NbFileMetadata → NbJSONObject 型変換
     */
    NbJSONObject toJSONObject();

    /**
     * ファイル名を取得する。<br>
     * @return ファイル名。
     */
    String getFileName();

    /**
     * ファイル名を設定する。
     * @param filename 設定するファイル名。
     */
    NbFileMetadata setFileName(String filename);

    /**
     * コンテンツタイプを取得する。<br>
     * @return コンテンツタイプ。
     */
    String getContentType();

    /**
     * コンテンツタイプを設定する。<br>
     * @param contentType 設定するコンテンツタイプ。
     */
    NbFileMetadata setContentType(String contentType);

    /**
     * ACLを取得する。<br>
     * @return Acl。
     */
    NbAcl getAcl();

    /**
     * ACLを設定する。<br>
     * @param acl 設定するACL。
     */
    NbFileMetadata setAcl(NbAcl acl);

    /**
     * ファイルサイズを取得する。<br>
     * @return ファイルサイズ(バイト数)。
     */
    long getLength();

    //void setLength(long length);


    /**
     * 公開URLを取得する。<br>
     * @return 公開URL。
     */
    String getPublishUrl();

    //void setPublishUrl(String url);

    /**
     * ファイルの作成時間を取得する。
     * @return ファイルの取得時間
     */
    String getCreatedTime();

    //void setCreatedTime(String createdTime);

    /**
     * ファイルの更新時間を取得する。
     * @return ファイルの更新時間
     */
    String getUpdatedTime();

    //void setUpdatedTime(String updatedTime);

    /**
     * cacheDisableフラグの設定 (未実装).<br>
     * メタデータのcacheDisableを設定する。<br>
     * cacheDisableはローカルストレージ（キャッシュ）へのファイル保存を無効にするかどうかを示す。
     * @param isDisable tureなら無効にする、falseなら無効にしない（保存可能にする）
     */
    NbFileMetadata setCacheDisabled(boolean isDisable);

    /**
     * cacheDisableフラグの参照 (未実装).<br>
     * メタデータのcacheDisableを参照する。
     */
    boolean isCacheDisabled();

    /**
     * metaETagの設定.<br>
     * メタデータに設定されたmetaETagを取得する。<br>
     * ※メタETagはサーバ側で付与するデータのため、オフラインで新規作成したデータは<br>
     * サーバと同期が取れるまで初期値のnullが設定されている。
     * @return メタデータに設定されたmetaETag
     */
    String getMetaETag();

    //void setMetaETag(String etag);

    /**
     * fileETagの取得.<br>
     * ファイルに設定されたfileETagを取得する。<br>
     * ※ファイルETagはサーバ側で付与するデータのため、オフラインで新規作成したデータは<br>
     * サーバと同期が取れるまで初期値のnullが設定されている。
     * @return ファイルに設定されたfileETag
     */
    String getFileETag();

    //void setFileETag(String etag);

    /**
     * オプション情報を設定する。
     * @param options オプション
     */
    NbFileMetadata setOptions(NbJSONObject options);

    /**
     * オプション情報を取得する
     * @return オプション
     */
    NbJSONObject getOptions();

    /**
     * 最終更新日時の取得 (未実装).<br>
     * ファイルの最終更新日時を取得する。<br>
     * ※最終更新日はサーバ側で付与するデータのため、オフラインで新規作成したデータは<br>
     * サーバと同期が取れるまで初期値のnullが設定されている。
     * @return ファイルの最終更新日時
     * @throws IllegalStateException オフライン機能無効時にコールされた
     */
    //String getLastSyncTime();

    /**
     * メタデータIDの取得.<br>
     * サーバから割り当てられたメタデータIDを取得する
     * ※メタIDはサーバ側で付与するデータのため、オフラインで新規作成したデータは<br>
     * サーバと同期が取れるまで初期値のnullが設定されている。
     * @return メタデータに設定されたID
     */
    String getMetaId();

    //void setMetaId(String metadataId);

    /** 内部IF */
    int getDbId();

    /** 内部IF */
    NbSyncState getMetaState();

    /** 内部IF */
    NbSyncState getFileState();
}
