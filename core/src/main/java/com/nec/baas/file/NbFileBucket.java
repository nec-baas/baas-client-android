/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.file;

import com.nec.baas.core.*;
import com.nec.baas.json.*;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * ファイルバケット。
 * <p>
 * 通常は {@link NbFileBucketManager#getBucket(String)} でインスタンスを取得する。
 * @since 1.0
 */
public interface NbFileBucket extends NbBaseBucket<NbFileBucket> {
    /**
     * ファイル作成パラメータ。
     */
    @Accessors(fluent = true)
    @Getter
    class NewFileParam {
        /** ファイル名(必須) */
        String filename;

        /** Content-Type(必須) */
        String contentType;

        /** ロードするファイルのパス */
        String filepath;

        /** ファイルデータを読み取る InputStream */
        InputStream filedata;

        /** ACL */
        @Setter
        NbAcl acl;

        /** Content-Length (自動設定時は 0) */
        @Setter
        long contentLength;

        /** Option情報 */
        @Setter
        NbJSONObject options;

        public NewFileParam(String filename, String contentType, String filepath) {
            this.filename = filename;
            this.contentType = contentType;
            this.filepath = filepath;
        }

        public NewFileParam(String filename, String contentType, InputStream filedata) {
            this.filename = filename;
            this.contentType = contentType;
            this.filedata = filedata;
        }
    }

    /**
     * ファイル更新パラメータ。
     */
    @Accessors(fluent = true)
    @Getter
    class UpdateFileParam {
        /** ファイル名(必須) */
        String filename;

        /** Content-Type(必須) */
        String contentType;

        /** ロードするファイルのパス */
        String filepath;

        /** ファイルデータを読み取る InputStream */
        InputStream filedata;

        /** Content-Length (自動設定時は 0) */
        @Setter
        long contentLength;

        public UpdateFileParam(String filename, String contentType, String filepath) {
            this.filename = filename;
            this.contentType = contentType;
            this.filepath = filepath;
        }

        public UpdateFileParam(String filename, String contentType, InputStream filedata) {
            this.filename = filename;
            this.contentType = contentType;
            this.filedata = filedata;
        }
    }

    /**
     * ファイルの新規アップロードを行う。
     * <p>
     * 必要なパラメータは {@link NewFileParam} で指定する。
     * バケットのcreate権限が必要。
     * ファイルが既に存在する場合エラーとなる(コールバックで通知される)。
     * <ul>
     * <li>制限事項：キャッシュ禁止フラグは常にfalse扱いで動作する。
     * </ul>
     * @param param アップロードパラメータ
     * @param callback アップロードしたファイルのメタデータを取得するコールバック。
     * @since 6.5.0
     */
    void uploadNewFile(NewFileParam param, NbCallback<NbFileMetadata> callback);

    /**
     * ファイルの新規アップロードを行う。
     * <p>
     * @deprecated {@link #uploadNewFile(String, String, NbAcl, String, NbCallback)} で置き換え
     */
    @Deprecated
    void uploadNewFile(String fileName, String filePath, NbAcl acl, String contentType,
                       final NbFileMetadataCallback callback);

    /**
     * ファイルの新規アップロードを行う(ファイルパス指定)。
     * <p>
     * ACLはオプションのため指定しなくても良い。
     * バケットのcreate権限が必要。
     * ファイルが既に存在する場合エラーとなる(コールバックで通知される)。
     * <ul>
     * <li>制限事項：キャッシュ禁止フラグは常にfalse扱いで動作する。
     * </ul>
     * @param fileName アップロードするファイルの名前。
     * @param filePath アップロードするローカルファイルのパス(絶対パス)。
     * @param acl アップロードするファイルに設定するACL。
     * @param contentType アップロードするファイルのコンテンツタイプ。
     * @param callback アップロードしたファイルのメタデータを取得するコールバック。
     * @since 6.5.0
     */
    void uploadNewFile(String fileName, String filePath, NbAcl acl, String contentType,
                       final NbCallback<NbFileMetadata> callback);

    /**
     * ファイルの新規アップロードを行う。
     * <p>
     * @deprecated {@link #uploadNewFile(String, InputStream, NbAcl, String, NbCallback)} で置き換え
     */
    @Deprecated
    void uploadNewFile(String fileName, InputStream fileData, NbAcl acl, String contentType,
                       final NbFileMetadataCallback callback);

    /**
     * ファイルの新規アップロードを行う(InputStream指定)。
     * <p>
     * ACLはオプションのため指定しなくても良い。
     * バケットのcreate権限が必要。
     * ファイルが既に存在する場合エラーとなる(コールバックでエラーが通知される)。
     * <ul>
     * <li>制限事項：メタデータのみキャッシュする。ファイルデータをキャッシュしたい際は
     * ダウンロードや同期を実行すること。
     * <li>制限事項：キャッシュ禁止フラグは常にfalse扱いで動作する。
     * </ul>
     * @param fileName アップロードするファイルの名前
     * @param fileData アップロードするファイルのデータを読み込むストリーム
     * @param acl アップロードするファイルに設定するACL
     * @param contentType アップロードするファイルのコンテンツタイプ
     * @param callback アップロードしたファイルのメタデータを取得するコールバック
     * @since 6.5.0
     */
    void uploadNewFile(String fileName, InputStream fileData, NbAcl acl, String contentType,
                       final NbCallback<NbFileMetadata> callback);

    /**
     * ファイルの新規アップロードを行う。
     * <p>
     * @deprecated {@link #uploadNewFile(String, InputStream, NbAcl, long, String, NbCallback)} で置き換え
     */
    @Deprecated
    void uploadNewFile(String fileName, InputStream fileData, NbAcl acl,
                       long contentLength, String contentType,
                       final NbFileMetadataCallback callback);

    /**
     * ファイルの新規アップロードを行う(InputStream指定、Content-Length指定付き)。
     * <p>
     * ACLはオプションのため指定しなくても良い。
     * バケットのcreate権限が必要。
     * ファイルが既に存在する場合エラーとなる(コールバックでエラーが通知される)。
     * <ul>
     *     <li>contentLength はコンテンツのバイト数に正確に一致していなければならない。
     *     一致していない場合、後続のリクエストにも影響を及ぼす。</li>
     *     <li>制限事項：メタデータのみキャッシュする。ファイルデータをキャッシュしたい際は
     *     ダウンロードや同期を実行すること。
     *     <li>制限事項：キャッシュ禁止フラグは常にfalse扱いで動作する。
     * </ul>
     * @param fileName アップロードするファイルの名前
     * @param fileData アップロードするファイルのデータを読み込むストリーム
     * @param acl アップロードするファイルに設定するACL
     * @param contentLength アップロードするファイルのバイト数。不明時は -1 (chunked encoding)。
     * @param contentType アップロードするファイルのコンテンツタイプ
     * @param callback アップロードしたファイルのメタデータを取得するコールバック
     * @since 6.5.0
     */
    void uploadNewFile(String fileName, InputStream fileData, NbAcl acl,
                       long contentLength, String contentType,
                       final NbCallback<NbFileMetadata> callback);

    /**
     * ファイルの更新アップロードを行う。
     * <p>
     * 必要なパラメータは {@link UpdateFileParam} で指定する。
     * バケットのupdate権限が必要。
     * ファイルの上書きを行う場合ファイルの書き込み権限が必要となる。
     * 該当ファイルが存在しない場合エラーとなる。
     * @param param 更新パラメータ
     * @param callback アップロードしたファイルのメタデータを取得するコールバック。
     * @since 6.5.0
     */
    void uploadUpdateFile(UpdateFileParam param, final NbCallback<NbFileMetadata> callback);

    /**
     * ファイルの更新アップロードを行う。
     * <p>
     * @deprecated {@link #uploadUpdateFile(String, String, String, NbCallback)} で置き換え
     */
    @Deprecated
    void uploadUpdateFile(String fileName, String filePath, String contentType,
                          final NbFileMetadataCallback callback);

    /**
     * ファイルの更新アップロードを行う(ファイルパス指定)。
     * <p>
     * ACLはオプションのため指定しなくても良い。
     * バケットのupdate権限が必要。
     * ファイルの上書きを行う場合ファイルの書き込み権限が必要となる。
     * 該当ファイルが存在しない場合エラーとなる。
     * <ul>
     * <li>制限事項：メタデータのみキャッシュする。ファイルデータをキャッシュしたい際は
     * ダウンロードや同期を実行すること。
     * <li>制限事項：キャッシュ禁止フラグは常にfalse扱いで動作する。例えば、
     * 事前にダウンロードしてキャッシュ上にファイルが存在する状態でも、当該ファイルを
     * 更新アップロードした場合は、キャッシュ上のファイル本体は削除される。
     * </ul>
     * @param fileName アップロードするファイルの名前。
     * @param filePath アップロードするファイルのローカルパス(絶対パス)。
     * @param contentType アップロードするファイルのコンテンツタイプ。
     * @param callback アップロードしたファイルのメタデータを取得するコールバック。
     * @since 6.5.0
     */
    void uploadUpdateFile(String fileName, String filePath, String contentType,
                          final NbCallback<NbFileMetadata> callback);

    /**
     * ファイルの更新アップロードを行う。
     * <p>
     * @deprecated {@link #uploadUpdateFile(String, InputStream, String, NbCallback)} で置き換え
     */
    @Deprecated
    void uploadUpdateFile(String fileName, InputStream fileData, String contentType,
                          final NbFileMetadataCallback callback);

    /**
     * ファイルの更新アップロードを行う(InputStream指定)。
     * <p>
     * ACLはオプションのため指定しなくても良い。
     * バケットのupdate権限が必要。
     * ファイルの上書きを行う場合ファイルの書き込み権限が必要となる。
     * 該当ファイルが存在しない場合エラーとなる。
     * <ul>
     * <li>制限事項：メタデータのみキャッシュする。ファイルデータをキャッシュしたい際は
     * ダウンロードや同期を実行すること。
     * <li>制限事項：キャッシュ禁止フラグは常にfalse扱いで動作する。例えば、
     * 事前にダウンロードしてキャッシュ上にファイルが存在する状態でも、当該ファイルを
     * 更新アップロードした場合は、キャッシュ上のファイル本体は削除される。
     * </ul>
     * @param fileName アップロードするファイルの名前
     * @param fileData アップロードするファイルのデータを読み込むストリーム
     * @param contentType アップロードするファイルのコンテンツタイプ
     * @param callback アップロードしたファイルのメタデータを取得するコールバック
     * @since 6.5.0
     */
    void uploadUpdateFile(String fileName, InputStream fileData, String contentType,
                          final NbCallback<NbFileMetadata> callback);

    /**
     * ファイルの更新アップロードを行う。
     * <p>
     * @deprecated {@link #uploadUpdateFile(String, InputStream, long, String, NbCallback)} で置き換え
     */
    @Deprecated
    void uploadUpdateFile(String fileName, InputStream fileData,
                          long contentLength, String contentType,
                          final NbFileMetadataCallback callback);

    /**
Nb     * ファイルの更新アップロードを行う(InputStream指定, Content-Length指定付き)。
     * <p>
     * ACLはオプションのため指定しなくても良い。
     * バケットのupdate権限が必要。
     * ファイルの上書きを行う場合ファイルの書き込み権限が必要となる。
     * 該当ファイルが存在しない場合エラーとなる。
     * <ul>
     *     <li>contentLength はコンテンツのバイト数に正確に一致していなければならない。
     *     一致していない場合、後続のリクエストにも影響を及ぼす。</li>
     * <li>制限事項：メタデータのみキャッシュする。ファイルデータをキャッシュしたい際は
     * ダウンロードや同期を実行すること。
     * <li>制限事項：キャッシュ禁止フラグは常にfalse扱いで動作する。例えば、
     * 事前にダウンロードしてキャッシュ上にファイルが存在する状態でも、当該ファイルを
     * 更新アップロードした場合は、キャッシュ上のファイル本体は削除される。
     * </ul>
     * @param fileName アップロードするファイルの名前
     * @param fileData アップロードするファイルのデータを読み込むストリーム
     * @param contentLength アップロードするファイルのバイト数。不明時は -1 (chunked encoding)。
     * @param contentType アップロードするファイルのコンテンツタイプ
     * @param callback アップロードしたファイルのメタデータを取得するコールバック
     * @since 6.5.0
     */
    void uploadUpdateFile(String fileName, InputStream fileData,
                          long contentLength, String contentType,
                          final NbCallback<NbFileMetadata> callback);

    /**
     * ファイルのダウンロードを行う(保存先ファイル指定)。
     * <p>
     * バケットおよび対象ファイルのread権限が必要となる。
     * ダウンロードしたファイルの保存先の書き込み権限が必要となる。
     * downloadFileにファイルが存在する場合上書き保存となる。
     * <p>
     * 制限事項：キャッシュ禁止フラグは常にfalse扱いで動作する。
     * @param fileName ダウンロードするファイルの名前
     * @param downloadFile ダウンロードしたファイルの保存先
     * @param callback ダウンロード結果を受け取るコールバック。保存先のファイルパスが渡される。
     */
    void downloadFile(final String fileName, final File downloadFile, final NbCallback<String> callback);

    /**
     * ファイルのダウンロードを行う(OutputStream指定)。
     * <p>
     * バケットおよび対象ファイルのread権限が必要となる。
     * ダウンロードしたファイルの保存先の書き込み権限が必要となる。
     * <p>制限事項：キャッシュ禁止フラグは常にfalse扱いで動作する。
     * @param fileName ダウンロードするファイルの名前。
     * @param outputStream ダウンロードしたファイルを書き込むストリーム
     * @param callback ダウンロード結果を受け取るコールバック。
     */
    void downloadFile(final String fileName, final OutputStream outputStream, final NbResultCallback callback);

    /**
     * ファイルの削除を行う。<br>
     * バケットおよび対象ファイルのdelete権限が必要となる。<br>
     * オフラインのみで作成されたファイル（メタID=null）を削除する場合は、<br>
     * キャッシュのみ削除する。サーバへリクエストしない。
     * @param fileName 削除するファイルの名前。
     * @param callback 削除結果を受け取るコールバック。
     */
    void deleteFile(final String fileName, final NbResultCallback callback);

    /**
     * 内部API: ファイル削除
     * @param fileName ファイル名
     * @param softDelete 仮削除フラグ
     * @param callback コールバック
     */
    void _deleteFile(final String fileName, final boolean softDelete, final NbResultCallback callback);

    /**
     * ファイルのメタデータの一覧を取得する。<p/>
     *
     * バケットおよび対象ファイルのread権限が必要となる。<br>
     * オフラインでメタデータを編集した状態で、オンラインでメタデータ一覧を取得した際、<br>
     * キャッシュ上で衝突が発生しなければローカル（編集された）のメタデータを返却する。<br>
     * キャッシュ上で衝突が発生した場合もローカル（編集された）のメタデータを返却するが、<br>
     * 当該データの同期状態(state)は「NbSyncState.CONFLICTED」になる。
     * @param isPublished trueの場合公開済みメタデータの一覧を取得する。
     * @param callback メタデータの一覧を受け取るコールバック。
     */
    void getFileMetadataList(boolean isPublished, final NbCallback<List<NbFileMetadata>> callback);

    /**
     * 内部API: ファイルメタデータ一覧を取得する
     * @param isPublished 公開済みデータ一覧取得
     * @param isDeleted 仮削除済みデータ一覧を含める
     * @param callback コールバック
     */
    void _getFileMetadataList(boolean isPublished, boolean isDeleted, final NbCallback<List<NbFileMetadata>> callback);

    /**
     * ファイルのメタデータを取得する。<p/>
     * @deprecated {@link #getFileMetadata(String, NbCallback)} で置き換え
     */
    @Deprecated
    void getFileMetadata(String fileName, final NbFileMetadataCallback callback);

    /**
     * ファイルのメタデータを取得する。<p/>
     * バケットおよび対象ファイルのread権限が必要となる。
     * @param fileName メタデータを取得するファイルの名前。
     * @param callback メタデータを受け取るコールバック。
     */
    void getFileMetadata(String fileName, final NbCallback<NbFileMetadata> callback);

    /**
     * ファイルの公開を行う。<p/>
     * @deprecated {@link #enablePublishFile(String, NbCallback)} で置き換え
     */
    @Deprecated
    void enablePublishFile(String fileName, final NbFileMetadataCallback callback);

    /**
     * ファイルの公開を行う。<p/>
     * 対象ファイルのadmin権限と対象バケットのupdate権限が必要となる。<br>
     * オフライン時はエラーとなる。
     * @param fileName 公開を行うファイルの名前。
     * @param callback 公開URLを保存したメタデータを受け取るコールバック。
     */
    void enablePublishFile(String fileName, final NbCallback<NbFileMetadata> callback);

    /**
     * ファイルの公開解除を行う。<p/>
     * @deprecated {@link #disablePublishFile(String, NbCallback)} で置き換え
     */
    @Deprecated
    void disablePublishFile(String fileName, final NbFileMetadataCallback callback);

    /**
     * ファイルの公開解除を行う。<p/>
     * 対象ファイルのadmin権限と対象バケットのupdate権限が必要となる。<br>
     * オフライン時はエラーとなる。
     * @param fileName 公開解除を行うファイルの名前。
     * @param callback 公開解除の結果を受け取るコールバック。
     */
    void disablePublishFile(String fileName, final NbCallback<NbFileMetadata> callback);

    /**
     * バケットの設定を保存する。<p/>
     * ROOTバケットと本バケットのupdate権限が必要となる。<br>
     * ACLの変更を行う際は本バケットのadmin権限が必要となる。<br>
     * オフライン時はエラーとなる。
     * @param callback 設定の保存結果を受け取るコールバック。
     */
    void save(final NbCallback<NbFileBucket> callback);
}
