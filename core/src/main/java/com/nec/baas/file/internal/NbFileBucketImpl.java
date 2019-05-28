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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.NonNull;
import okhttp3.Request;
import okhttp3.Response;

/**
 * NbFileBucket 実装
 */
public class NbFileBucketImpl extends NbBaseBucketImpl<NbFileBucket> implements NbFileBucket {
    private static final NbLogger log = NbLogger.getLogger(NbFileBucketImpl.class);

    private static final String HEADER_ACL = NbConsts.HEADER_ACL;
    private static final String HEADER_META_OPTIONS = NbConsts.HEADER_META_OPTIONS;

    private static final String PUBLISH_PATH_COMPONENT = "publish";

    /**
     * コンストラクタ(内部IF)
     * @param bucketName バケット名
     */
    public NbFileBucketImpl(NbService service, String bucketName, NbBucketMode mode) {
        super(service, bucketName, NbConsts.FILES_PATH + "/" + bucketName, mode);

        // オンラインのみ有効
        if (mode != NbBucketMode.ONLINE) {
            throw new IllegalArgumentException(" invalid mode. " + mode + " only NbBucketMode.ONLINE is acceptable for NbFileBucket");
        }

        // オフラインモード強制無効化
        mOfflineService = null;
    }

    public NbFileMetadataImpl newFileMetadata(String filename, String contentType, NbAcl acl, String bucketName) {
        return new NbFileMetadataImpl(mNebulaService, bucketName, filename, contentType, acl);
    }

    /** {@inheritDoc} */
    @Override
    public void uploadNewFile(String fileName, InputStream fileData, NbAcl acl, long contentLength, String contentType,
                              final NbCallback<NbFileMetadata> callback) {
        NewFileParam param = new NewFileParam(fileName, contentType, fileData)
                .acl(acl).contentLength(contentLength);
        uploadNewFile(param, callback);
    }

    @Override
    @Deprecated
    public void uploadNewFile(String fileName, String filePath, NbAcl acl, String contentType, final NbFileMetadataCallback callback) {
        uploadNewFile(fileName, filePath, acl, contentType, FileMetadataCallbackListToSingleWrapper.wrap(callback));
    }

    /** {@inheritDoc} */
    @Override
    public void uploadNewFile(String fileName, String filePath, NbAcl acl, String contentType, final NbCallback<NbFileMetadata> callback) {
        NewFileParam param = new NewFileParam(fileName, contentType, filePath)
                .acl(acl);
        uploadNewFile(param, callback);
    }

    @Override
    @Deprecated
    public void uploadNewFile(String fileName, InputStream fileData, NbAcl acl, String contentType,
                              final NbFileMetadataCallback callback) {
        uploadNewFile(fileName, fileData, acl, -1, contentType, callback);
    }

    /** {@inheritDoc} */
    @Override
    public void uploadNewFile(String fileName, InputStream fileData, NbAcl acl, String contentType,
                              final NbCallback<NbFileMetadata> callback) {
        uploadNewFile(fileName, fileData, acl, -1, contentType, callback);
    }

    @Override
    @Deprecated
    public void uploadNewFile(String fileName, InputStream fileData, NbAcl acl, long contentLength, String contentType,
                              final NbFileMetadataCallback callback) {
        uploadNewFile(fileName, fileData, acl, contentLength, contentType, FileMetadataCallbackListToSingleWrapper.wrap(callback));
    }

    @Override
    public void uploadNewFile(@NonNull NewFileParam param, @NonNull NbCallback<NbFileMetadata> callback) {
        final String fileName = param.filename();
        final String contentType = param.contentType();
        long contentLength = param.contentLength();

        InputStream fileData = param.filedata();
        final String filePath = param.filepath();

        log.fine("uploadNewFile <start> filename=" + fileName
                + " contentType=" + param.contentType());
        if (fileName == null) {
            throw new IllegalArgumentException("fileName is null");
        }
        if (contentType == null) {
            throw new IllegalArgumentException("contentType is null");
        }

        if (fileData == null) {
            fileData = loadFromFilepath(filePath, callback);
            if (fileData == null) {
                return; // failed
            }

            contentLength = new File(filePath).length();
            callback = getFileMetadataCallback(callback, fileData);
        }

        uploadNewFileOnline(param, fileData, contentLength, callback);

        log.fine("uploadNewFile(path) <end>");
    }

    /**
     * ファイルの新規アップロード（オンライン）
     */
    private void uploadNewFileOnline(NewFileParam param, InputStream in, long contentLength, final NbCallback<NbFileMetadata> callback) {
        final String filename = param.filename();
        final String contentType = param.contentType();
        final NbAcl acl = param.acl();
        final NbJSONObject options = param.options();

        //TBD:ファイルサイズチェック
        //ここでパイプストリーム用にスレッドを起動する。
        //立ち上げたスレッド上で、アップロード前にストリームを一時ファイルに書き込む。
        //書き込んだファイルからサイズを取得し、サイズチェックを行う。

        //リクエスト作成
        Map<String, String> headers = new HashMap<>();
        if (acl != null) {
            headers.put(HEADER_ACL, acl.toJsonString());
        }
        if (options != null) {
            headers.put(HEADER_META_OPTIONS, options.toJSONString());
        }

        log.fine("uploadNewFile(stream) mApiUrl=" + mApiUrl);

        Request request = getHttpRequestFactory()
                .post(mApiUrl).addPathComponent(filename)
                .body(in, contentLength, contentType)
                .headers(headers)
                .build();

        NbRestResponseHandler handler = makeMetadataResponseHandler(callback, "NbFileBucket.uploadNewFile(stream)");
        execUploadNewFile(request, handler, filename, contentType, in, acl);
    }

    /**
     * 指定 filePath からファイルをロードする
     * @param filePath
     * @param callback
     * @return
     */
    private InputStream loadFromFilepath(@NonNull String filePath, @NonNull NbCallback<NbFileMetadata> callback) {

        File file = new File(filePath);
        if (!file.exists()) {
            log.severe("No such file: path=" + filePath);
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo(String.format("%s does not exist.", filePath)));
            return null;
        }

        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e1) {
            log.warning("File not found: {0}", e1.getMessage());
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("File not found."));
            return null;
        }
    }

    /**
     * NbCallback を wrap し、API完了時に InputStream を close する
     * @param callback callback
     * @param fis close する InputStream
     * @return wrap された callback
     */
    private NbCallback<NbFileMetadata> getFileMetadataCallback(final NbCallback<NbFileMetadata> callback,
                                                               final InputStream fis) {
        return new NbCallback<NbFileMetadata>() {
            @Override
            public void onSuccess(NbFileMetadata meta) {
                //log.fine("getFileMetadataCallback() onSuccess() metas={0}", meta);
                try {
                    fis.close();
                } catch (IOException e) {
                    log.warning("getFileMetadataCallback.onSuccess(): {0}", e.getMessage());
                } finally {
                    //アプリへコールバック
                    callback.onSuccess(meta);
                }
            }
            @Override
            public void onFailure(int statusCode, NbErrorInfo errorInfo) {
                //log.severe("getFileMetadataCallback() onFailure() ERR statusCode={0}", statusCode);
                try {
                    fis.close();
                } catch (IOException e) {
                    log.warning("getFileMetadataCallback.onFailure(): {0}", e.getMessage());
                } finally {
                    //アプリへコールバック
                    callback.onFailure(statusCode, errorInfo);
                }
            }
        };
    }

    /**
     * ファイルの新規アップロード（RESTAPI実行）
     */
    protected void execUploadNewFile(Request request, NbRestResponseHandler handler,
                                     String fileName, String contentType, InputStream fileData, NbAcl acl) {
        executeRequest(request, handler);
    }

    @Override
    @Deprecated
    public void uploadUpdateFile(String fileName, String filePath, String contentType,
                                 final NbFileMetadataCallback callback) {
        uploadUpdateFile(fileName, filePath, contentType, FileMetadataCallbackListToSingleWrapper.wrap(callback));
    }

    /** {@inheritDoc} */
    @Override
    public void uploadUpdateFile(String fileName, String filePath, String contentType,
                                 final NbCallback<NbFileMetadata> callback) {
        uploadUpdateFile(new UpdateFileParam(fileName, contentType, filePath), callback);
    }

    @Override
    public void uploadUpdateFile(@NonNull UpdateFileParam param, @NonNull NbCallback<NbFileMetadata> callback) {
        final String fileName = param.filename();
        final String contentType = param.contentType();
        final String filePath = param.filepath();
        InputStream fileData = param.filedata();
        long contentLength = param.contentLength();

        log.fine("uploadUpdateFile <start>"
                + " filename=" + fileName + " contentType=" + contentType);

        if (fileName == null) {
            throw new IllegalArgumentException("fileName is null");
        }
        if (contentType == null) {
            throw new IllegalArgumentException("contentType is null");
        }

        if (fileData == null) {
            fileData = loadFromFilepath(filePath, callback);
            if (fileData == null) {
                return; // failed
            }

            contentLength = new File(filePath).length();
            callback = getFileMetadataCallback(callback, fileData);
        }

        uploadUpdateFileOnline(param, fileData, contentLength, callback);

        log.fine("uploadUpdateFile(path) <end>");
    }

    /**
     * ファイルの更新アップロード（オンライン）
     */
    private void uploadUpdateFileOnline(@NonNull UpdateFileParam param, InputStream fileData, long contentLength, NbCallback<NbFileMetadata> callback) {
        final String fileName = param.filename();
        final String contentType = param.contentType();

        Request request = getHttpRequestFactory()
                .put(mApiUrl).addPathComponent(fileName)
                .body(fileData, contentLength, contentType)
                .build();

        NbRestResponseHandler handler = makeMetadataResponseHandler(callback, "NbFileBucket.uploadUpdateFile");
        execUploadUpdateFile(request, handler, fileName, contentType, fileData);
    }

    /** {@inheritDoc} */
    @Override
    public void uploadUpdateFile(String fileName, InputStream fileData, long contentLength, String contentType,
                                 final NbCallback<NbFileMetadata> callback) {
        uploadUpdateFile(new UpdateFileParam(fileName, contentType, fileData).contentLength(contentLength), callback);
    }

    @Override
    @Deprecated
    public void uploadUpdateFile(String fileName, InputStream fileData, String contentType,
                                 final NbFileMetadataCallback callback) {
        uploadUpdateFile(fileName, fileData, -1, contentType, FileMetadataCallbackListToSingleWrapper.wrap(callback));
    }

    /** {@inheritDoc} */
    @Override
    public void uploadUpdateFile(String fileName, InputStream fileData, String contentType,
                                 final NbCallback<NbFileMetadata> callback) {
        uploadUpdateFile(fileName, fileData, -1, contentType, callback);
    }

    @Override
    @Deprecated
    public void uploadUpdateFile(String fileName, InputStream fileData, long contentLength, String contentType,
                                 final NbFileMetadataCallback callback) {
        uploadUpdateFile(fileName, fileData, contentLength, contentType, FileMetadataCallbackListToSingleWrapper.wrap(callback));
    }

    /**
     * FileMetadata返却用レスポンスハンドラ生成
     */
    private NbRestResponseHandler makeMetadataResponseHandler(final NbCallback<NbFileMetadata> callback,
                                                            final String logPrefix) {
        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, logPrefix) {
            @Override
            public void onSuccess(Response response, NbJSONObject json) {
                log.fine(logPrefix + " -> onSuccess() body:" + json);

                NbFileMetadata meta;
                meta = makeFileMetadata(json);

                callback.onSuccess(meta);
            }
            @Override
            public void onFailure(int status, NbJSONObject json) {
                log.fine("" + logPrefix + " -> onFailure()"
                        + " status:" + status + " json:" + json);
                callback.onFailure(status, new NbErrorInfo(String.valueOf(json)));
            }
        };
        return handler;
    }

    /**
     * ファイルの更新アップロード（RESTAPI実行）
     */
    protected void execUploadUpdateFile(Request request, NbRestResponseHandler handler,
                                        String fileName, String contentType, InputStream fileData) {
        executeRequest(request, handler);
    }

    /** {@inheritDoc} */
    @Override
    public void downloadFile(@NonNull final String fileName, @NonNull final File downloadFile, @NonNull final NbCallback<String> callback) {
        log.fine("downloadFile(path) <start> filename=" + fileName
                + " downloadFile=" + downloadFile);

        //ダウンロードコールバック
        DownloadFileCallback downloadCallback = getDownloadFileCallback(downloadFile, callback);
        //サーバからファイル本体をダウンロード
        downloadFile(fileName, downloadCallback);

        log.fine("downloadFile(path) <end>");
    }

    protected void execDownloadFile(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }


    private DownloadFileCallback getDownloadFileCallback(final File downloadFile,
                                                         final NbCallback<String> callback) {
        DownloadFileCallback newCallback = new DownloadFileCallback(callback) {

            @Override
            public int preHandleResponse(InputStream inputStream, long size, String fileETag) {
                log.fine("getDownloadFileCallback()->"
                        + "preHandleResponse() inputStream=" + inputStream + " size=" + size);

                return execDownloadFilePreHandleResponse(inputStream, downloadFile, fileETag,
                        size, getMetadata());
            }

            @Override
            public void onSuccess(boolean isCacheSave) {
                log.fine("getDownloadFileCallback()-> onSuccess()");
                callback.onSuccess(downloadFile.getPath());
            }
            @Override
            public void onFailure(int statusCode, NbErrorInfo errorInfo) {
                log.fine("getDownloadFileCallback() ->"
                        + " error onFailure(" + statusCode + ")");
                callback.onFailure(statusCode, errorInfo);
            }
        };

        return newCallback;
    }

    private DownloadFileCallback getDownloadStreamCallback(final OutputStream outputStream,
                                                           final NbResultCallback callback) {
        DownloadFileCallback newCallback = new DownloadFileCallback(callback) {
            @Override
            public int preHandleResponse(InputStream inputStream, long size, String fileETag) {
                return execDownloadPreHandleResponse(inputStream, outputStream, fileETag, size,
                        getMetadata());
            }
            @Override
            public void onSuccess(boolean isCacheSave) {
                log.fine("getDownloadStreamCallback()-> onSuccess()");
                callback.onSuccess();
            }
            @Override
            public void onFailure(int statusCode, NbErrorInfo errorInfo) {
                log.fine("getDownloadStreamCallback()->"
                        + " onFailure() statusCode=" + statusCode);
                callback.onFailure(statusCode, errorInfo);
            }
        };

        return newCallback;
    }

    /**
     * レスポンスからダウンロードファイルを取得する処理。（ファイル版）
     * サブスレッド上(AsyncTask#doInBackGround()から呼び出される。
     * ストリームデータなので、一度キャッシュに保存して再読み込みした後、
     * アプリ指定のダウンロード先へ保存する。
     * @param inputStream レスポンス
     * @param downloadFile ファイル保存先
     * @param fileETag ETag
     * @param size ファイルサイズ
     * @param meta メタデータ
     * @return ステータスコード
     */
    private int execDownloadFilePreHandleResponse(InputStream inputStream,
                                                  File downloadFile, String fileETag, long size, NbFileMetadata meta) {
        FileOutputStream fos = null;
        int status = NbStatus.OK;
        try {
            fos = new FileOutputStream(downloadFile);
        } catch (IOException e) {
            //e.printStackTrace();
            log.severe("execDownloadFilePreHandleResponse() : Error: {0}", e.getMessage());
            //アプリ指定のダウンロード先がストリームで読み込めないため、エラーリターン
            return NbStatus.UNPROCESSABLE_ENTITY_ERROR;
        }

        status = execDownloadPreHandleResponse(inputStream, fos, fileETag, size,
                meta);
        if (NbStatus.isNotSuccessful(status)) {
            //Exception発生時or書き込みエラー時はファイル削除
            downloadFile.delete();
        }

        try {
            fos.close();
        } catch (IOException e) {
            //e.printStackTrace();
            log.warning(e.getMessage());
        }

        log.fine("execDownloadFilePreHandleResponse(): status={0}", status);
        return status;
    }

    /**
     * レスポンスからダウンロードファイルを取得する処理。（ファイル、ストリーム共通）
     * サブスレッド上(AsyncTask#doInBackGround()から呼び出される。
     * ストリームデータなので、一度キャッシュに保存して再読み込みした後、
     * アプリ指定のダウンロード先へ保存する。
     * @param inputStream レスポンス
     * @param outputStream 出力先ストリーム
     * @param fileETag ETag
     * @param size ファイルサイズ
     * @param meta メタデータ
     * @return ステータスコード
     */
    private int execDownloadPreHandleResponse(InputStream inputStream,
                                              final OutputStream outputStream, String fileETag, long size, NbFileMetadata meta) {
        log.fine("execDownloadPreHandleResponse() <start>");

        InputStream localInputStream = inputStream;

        //ダウンロード先へストリーム書き込み
        try {
            NbFileUtil.writeToStream(outputStream, localInputStream, size);
        } catch (IOException ex) {
            log.severe("execDownloadPreHandleResponse()->"
                    + " writeToStream() ex=" + ex);
            return NbStatus.UNPROCESSABLE_ENTITY_ERROR;
        } finally {
            //一時ファイル or サーバ側のストリームは解放する。
            try {
                localInputStream.close();
            } catch (IOException e) {
                //e.printStackTrace();
                log.warning(e.getMessage());
            }
        }
        log.fine("execDownloadPreHandleResponse() <end>");

        return NbStatus.OK;
    }

    /** {@inheritDoc} */
    @Override
    public void downloadFile(@NonNull final String fileName, @NonNull final OutputStream outputStream,
                             @NonNull final NbResultCallback callback) {
        log.fine("downloadFile(stream) <start> filename=" + fileName
                + " fileData=" + outputStream);
        //ダウンロードコールバック
        DownloadFileCallback downloadCallback = getDownloadStreamCallback(outputStream, callback);
        //サーバからファイル本体をダウンロード
        downloadFile(fileName, downloadCallback);

        log.fine("downloadFile(stream) <end>");
    }

    private static abstract class DownloadFileCallback implements NbBaseCallback {
        private NbBaseCallback mCallback;
        private NbFileMetadata mMetadata;

        public void setMetadata(NbFileMetadata meta) {
            mMetadata = meta;
        }

        public NbFileMetadata getMetadata() {
            return mMetadata;
        }

        public DownloadFileCallback(NbBaseCallback callback) {
            mCallback = callback;
        }
        /**
         * ダウンロード成功時に呼び出される。isCacheSaveはクライアントデータで代替可能な場合に<br>
         * キャッシュ保存をスキップさせるフラグ<br>
         */
        public abstract void onSuccess(boolean isCacheSave);

        /**
         * ダウンロード成功時に呼び出される。(UIスレッド上で呼び出される)<br>
         */
        public void onSuccess() {
            onSuccess(true);
        }
        /**
         * ダウンロード成功時に呼び出される。(サブスレッド上で呼び出される)。
         * inputStreamはpreHandleResponse内でクローズすること。
         * @param is サーバから受信したファイルデータの InputStream
         * @param size サイズ
         * @param fileETag ETag
         * @return ステータスコード
         */
        public abstract int preHandleResponse(InputStream is, long size, String fileETag) ;

        @Override
        public void onFailure(int statusCode, NbErrorInfo errorInfo) {
            mCallback.onFailure(statusCode, errorInfo);
        }
    }

    private void downloadFile(final String fileName, final DownloadFileCallback callback) {
        log.fine("downloadFile(private) <start>");

        if (fileName == null) {
            log.severe("downloadFile(private) <end> ERR no fileName");
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("fileName is null."));
            return;
        }

        //リクエスト作成
        Request request = getHttpRequestFactory().get(mApiUrl).addPathComponent(fileName).build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbFileBucket.downloadFile(private)") {
            @Override
            public int preHandleResponse(Response response) {
                log.fine("downloadFile(private)->preHandleResponse()"
                        + " <start>");

                // 200 OK でない場合は、即時リターン
                int statusCode = response.code();
                if (NbStatus.isNotSuccessful(statusCode)) {
                    // エラー情報の JSON を復元しておく
                    this.setJson(NbUtil.restoreResponse(response));
                    return statusCode;
                }

                //レスポンスボディ復元処理
                InputStream is = NbFileUtil.restoreStreamFromResponse(response);
                if (is == null) {
                    log.severe("downloadFile(private) preHandleResponse()"
                            + " ERR is == null");
                    //レスポンスが正しく取れない場合はサーバ側エラー
                    return NbStatus.INTERNAL_SERVER_ERROR;
                }
                //サイズ取得
                long size = NbFileUtil.restoreContentSizeFromResponse(response);
                String fileETag = NbFileUtil.restoreFileETagFromResponse(response);
                log.fine("downloadFile(private)->preHandleResponse()"
                        + " size=" + size + " fileETag=" + fileETag);
                //レスポンスが正しく取得できなかった場合はサーバエラーで終了
                if ((size == 0) || (fileETag == null)) {
                    return NbStatus.INTERNAL_SERVER_ERROR;
                }

                // inputStream は callback 先で close すること
                int result =  callback.preHandleResponse(is, size, fileETag);
                log.fine("downloadFile(private)->preHandleResponse()"
                        + " <end> result=" + result);
                return result;
            }

            @Override
            public void onSuccess(Response response) {
                log.fine("downloadFile(private)->onSuccess() call");
                callback.onSuccess();
            }
        };

        log.fine("downloadFile(private) <end>");

        execDownloadFile(request, handler);
    }

    /** {@inheritDoc} */
    @Override
    public void deleteFile(final String fileName, final NbResultCallback callback) {
        _deleteFile(fileName, false, callback);
    }

    @Override
    public void _deleteFile(@NonNull final String fileName, final boolean softDelete, @NonNull final NbResultCallback callback) {
        log.fine("deleteFile() <start> fileName=" + fileName);
        deleteFileOnlineDispatch(fileName, softDelete, callback);

        log.fine("deleteFile() <end>");
    }

    /*
     * オンラインのファイル削除処理切り分け
     */
    private void deleteFileOnlineDispatch(final String fileName, final boolean softDelete, final NbResultCallback callback) {
        Map<String, String> params = null;
        if (softDelete) {
            params = new HashMap<>();
            params.put(NbKey.DELETE_MARK, "1");
        }
        deleteFileOnline(fileName, params, callback);    //オンライン削除
    }

    /*
     * ファイルの削除(オンライン)
     */
    public void deleteFileOnline(final String fileName, Map<String, String> param,
                                 final NbResultCallback callback) {
        //リクエスト作成
        Request request = getHttpRequestFactory()
                .delete(mApiUrl).addPathComponent(fileName)
                .params(param)
                .build();

        NbRestResponseHandler handler = makeDeleteResponseHandler(fileName, callback);
        execDeleteFile(request, handler);
    }

    private NbRestResponseHandler makeDeleteResponseHandler(final String fileName, final NbResultCallback callback) {
        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbFileBucket.deleteFile") {
            @Override
            public void onSuccess(Response response) {
                callback.onSuccess();
            }
            @Override
            public void onFailure(int status, NbJSONObject json) {
                log.severe("deleteFileOnline() onFailure() ERR status=" + status);
                callback.onFailure(status, new NbErrorInfo(String.valueOf(json)));
            }
        };
        return handler;
    }

    protected void execDeleteFile(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /** {@inheritDoc} */
    @Override
    public void getFileMetadataList(boolean isPublished, final NbCallback<List<NbFileMetadata>> callback) {
        _getFileMetadataList(isPublished, false, callback);
    }

    /** {@inheritDoc} */
    @Override
    public void _getFileMetadataList(boolean isPublished, boolean isDeleted, @NonNull final NbCallback<List<NbFileMetadata>> callback) {
        log.fine("getFileMetadataList() <start>"
                + " isPublished=" + isPublished);

        getFileMetadataListOnline(isPublished, isDeleted, callback);
        log.fine("getFileMetadataList() <end>");
    }

    /**
     * ファイルのメタデータ一覧取得(オンライン)
     */
    private void getFileMetadataListOnline(boolean isPublished, boolean isDeleted, final NbCallback<List<NbFileMetadata>> callback) {
        //リクエスト作成
        Map<String, String> requestMap = new HashMap<>();
        if (isPublished) {
            requestMap.put(NbKey.PUBLISHED, "1");
        }
        if (isDeleted) {
            requestMap.put(NbKey.DELETE_MARK, "1");
        }
        Request request = getHttpRequestFactory().get(mApiUrl).params(requestMap).build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbFileBucket.getFileMetadataList") {
            @Override
            public void onSuccess(Response response, NbJSONObject json) {
                log.fine("getFileMetadataList() body=" + json);
                NbJSONArray resultList = json.getJSONArray(NbKey.RESULTS);
                List<NbFileMetadata> metadataList = getNotifyMetadataList(resultList);
                callback.onSuccess(metadataList);
            }
        };
        execGetFileMetadataList(request, handler);
    }

    private List<NbFileMetadata> getNotifyMetadataList(NbJSONArray resultList) {
        List<NbFileMetadata> metadataList = new ArrayList<>();

        for (Object result : resultList) {
            metadataList.add(makeFileMetadata((NbJSONObject)result));
        }

        return metadataList;
    }

    /**
     * JSON配列 → FileMetadata型変換(内部IF)。
     * 変換の際、クライアント側のみデータ更新が発生していた場合は、
     * サーバ側データではなくクライアントデータを返却する。
     */
    public List<NbFileMetadata> convertMetadata(NbJSONArray resultList) {
        log.fine("convertMetadata() <start>");

        List<NbFileMetadata> metadataList = new ArrayList<>();

        for (Object json : resultList) {
            NbFileMetadataImpl meta = makeFileMetadata((NbJSONObject)json);
            metadataList.add(meta);
        }
        return metadataList;
    }

    public void execGetFileMetadataList(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /** {@inheritDoc} */
    @Override
    public void getFileMetadata(String fileName, final NbFileMetadataCallback callback) {
        getFileMetadata(fileName, FileMetadataCallbackListToSingleWrapper.wrap(callback));
    }

    @Override
    public void getFileMetadata(@NonNull String fileName, @NonNull final NbCallback<NbFileMetadata> callback) {
        log.fine("getFileMetadata() <start> filename=" + fileName);

        getFileMetadataOnline(fileName, callback);
        log.fine("getFileMetadata() <end>");
    }

    /*
     * ファイルのメタデータ取得(オンライン)
     */
    private void getFileMetadataOnline(String fileName, final NbCallback<NbFileMetadata> callback) {
        //リクエスト作成
        Request request = getHttpRequestFactory().get(mApiUrl)
                .addPathComponent(fileName).addPathComponent(NbConsts.META_PATH_COMPONENT).build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbFileBucket.getFileMetadataOnline") {
            @Override
            public void onSuccess(Response response, NbJSONObject json) {
                NbFileMetadataImpl metadata = makeFileMetadata(json);
                callback.onSuccess(metadata);
            }
        };
        execGetFileMetadata(request, handler);
    }

    protected void execGetFileMetadata(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    @Override
    @Deprecated
    public void enablePublishFile(String fileName, final NbFileMetadataCallback callback) {
        enablePublishFile(fileName, FileMetadataCallbackListToSingleWrapper.wrap(callback));
    }

    /** {@inheritDoc} */
    @Override
    public void enablePublishFile(@NonNull String fileName, @NonNull final NbCallback<NbFileMetadata> callback) {
        log.fine("enablePublishFile() <start> filename=" + fileName);

        if (!NbUtil.checkOnline(mOfflineService, "[NbFileBucket]", callback)) {
            log.severe("enablePublishFile() <end> ERR");
            return;
        }

        //リクエスト作成
        //リクエストパラメータ作成
        Request request = getHttpRequestFactory()
                .put(mApiUrl).addPathComponent(fileName).addPathComponent(PUBLISH_PATH_COMPONENT)
                .body("{}") // put は BODY 必須
                .params(null)
                .build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbFileBucket.enablePublishFile") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                log.fine("enablePublishFile() -> onSuccess()");
                NbFileMetadataImpl metadata = makeFileMetadata(body);
                callback.onSuccess(metadata);
            }

            @Override
            public void onFailure(int status, NbJSONObject json) {
                log.severe("enablePublishFile() -> onFailure() status={0} json={1}", status, json);
                callback.onFailure(status, new NbErrorInfo(String.valueOf(json)));
            }
        };
        execEnablePublishFile(request, handler);

        log.fine("enablePublishFile() <end>");
    }

    protected void execEnablePublishFile(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    @Override
    @Deprecated
    public void disablePublishFile(String fileName, final NbFileMetadataCallback callback) {
        disablePublishFile(fileName, FileMetadataCallbackListToSingleWrapper.wrap(callback));
    }

    /** {@inheritDoc} */
    @Override
    public void disablePublishFile(@NonNull String fileName, @NonNull final NbCallback<NbFileMetadata> callback) {
        log.fine("disablePublishFile() <start> filename=" + fileName);

        if (!NbUtil.checkOnline(mOfflineService, "[NbFileBucket]", callback)) {
            log.severe("disablePublishFile() <end> ERR");
            return;
        }

        //レスポンス作成
        Request request = getHttpRequestFactory()
                .delete(mApiUrl).addPathComponent(fileName).addPathComponent(PUBLISH_PATH_COMPONENT)
                .params(null)
                .build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbFileBucket.disablePublishFile") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                log.fine("disablePublishFile() -> onSuccess()");
                NbFileMetadataImpl metadata = makeFileMetadata(body);
                callback.onSuccess(metadata);
            }
            @Override
            public void onFailure(int status, NbJSONObject json) {
                log.fine("disablePublishFile() -> onFailure()");
                callback.onFailure(status, new NbErrorInfo(String.valueOf(json)));
            }
        };
        execDisablePublishFile(request, handler);

        log.fine("disablePublishFile() <end>");
    }

    protected void execDisablePublishFile(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /** {@inheritDoc} */
    @Override
    public void save(@NonNull final NbCallback<NbFileBucket> callback) {
        log.fine("save() <start>");

        if (!NbUtil.checkOnline(mOfflineService, "[NbFileBucket]", callback)) {
            log.severe("save() <end> ERR");
            return;
        }
        //リクエスト作成
        NbJSONObject bodyJson = getBodyJsonForFileBucket();

        Request request = getHttpRequestFactory()
                .put(NbConsts.FILE_BUCKET_PATH).addPathComponent(mBucketName)
                .body(bodyJson)
                .build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbFileBucket.save") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                NbAcl resultAcl = new NbAcl(body.getJSONObject(NbKey.ACL));
                setAcl(resultAcl);

                NbContentAcl resultContentAcl = new NbContentAcl(body.getJSONObject(NbKey.CONTENT_ACL));
                setContentAcl(resultContentAcl);

                setDescription(body.getString(NbKey.DESCRIPTION));

                callback.onSuccess(NbFileBucketImpl.this);
            }
        };
        execSave(request, handler);

        log.fine("save() <end>");
    }

    protected void execSave(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /**
     * ファイルのメタデータを作成する。<br>
     * 「NbUtil.ACL」で読み書きするデータはMap<String, Object>型である。（Aclではないので注意）
     * @param map メタデータ
     * @return NbFileMetadata
     */
    private NbFileMetadataImpl makeFileMetadata(Map<String, Object> map) {
        String fileName = (String)map.get(NbKey.FILENAME);
        NbFileMetadataImpl meta = newFileMetadata(fileName, null, null, mBucketName);
        meta.setMetadata(map);
        return meta;
    }

    /** {@inheritDoc} */
    public void requestCancel(String fileName) {
        if (mOfflineService == null) {
            throw new IllegalStateException("offline mode is not enabled");
        }
    }
}
