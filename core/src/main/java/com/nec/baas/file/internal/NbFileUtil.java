/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.file.internal;

import com.nec.baas.util.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownServiceException;

import okhttp3.Response;

/**
 * File 関連ユーティリティクラス
 */
public abstract class NbFileUtil {
    private static final NbLogger log = NbLogger.getLogger(NbFileUtil.class);
    
    /**
     * InputStreamのデータをFileへ出力する。
     * 作成に失敗した場合はoutputファイルを削除
     * @param  file 保存先ファイル
     * @param is InputStream
     * @param expectedSize 期待するファイルサイズ。
     */
    public static void writeToFile(File file, InputStream is, long expectedSize)
            throws FileNotFoundException, UnknownServiceException, IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException ex) {
            //ex.printStackTrace();
            log.warning("writeToFile: {0}", ex.getMessage());
            throw ex;
        }

        try {
            writeToStream(fos, is, expectedSize);
        } catch (UnknownServiceException ex) {
            file.delete();    //作成できなかった場合は削除
            throw ex;
        } catch (IOException ex) {
            file.delete();    //作成できなかった場合は削除
            throw ex;         //呼び出し元で例外
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                //ex.printStackTrace();
                log.warning(ex.getMessage());
                throw ex;
            }
        }
    }

    /**
     * InputStreamの内容をOutputStreamに書き込む
     * @param os OutputStream
     * @param is InputStream
     * @param expectedSize 期待するファイルサイズ。負の場合はチェックしない。
     * @throws IOException              ローカル
     * @throws UnknownServiceException  読み込み失敗時に返却
     */
    static public synchronized void writeToStream(OutputStream os, InputStream is, long expectedSize)
            throws IOException, UnknownServiceException {
        log.fine("writeToStream <start> expectedSize=" + expectedSize);

        BufferedOutputStream bos = new BufferedOutputStream(os);
        byte[] readBuffer = new byte[4096];
        long writeSize = 0;
        int readSize;

        while (true) {
            try {   //ストリーム読み込み
                readSize = is.read(readBuffer);
            } catch (IOException ex) {
                log.fine("writeToStream() read error ex=" + ex);

                //ダウンロード時の場合はネットワーク障害にあるので、呼び出し元で判別できるように
                //例外をUnknownServiceExceptionとする。
                throw new UnknownServiceException("read error");
            }

            if (readSize <= 0) {
                break;  //全て読み込んだら終了
            }

            try {   //ストリーム書き込み
                bos.write(readBuffer, 0, readSize);
            } catch (IOException ex) {
                log.fine("writeToStream() write error ex=" + ex);
                throw new IOException("write error");
            }
            writeSize += readSize;  //書き込みサイズ集計
        }

        //バッファのフラッシュ＆クローズ処理
        try {
            bos.flush();
        } catch (IOException ex) {
            log.fine("writeToStream() output flush error ex=" + ex);
            throw ex;
        }

        try {
            bos.close();
        } catch (IOException ex) {
            log.fine("writeToStream() output close error ex=" + ex);
            throw ex;
        }

        if ((expectedSize > -1) && (writeSize != expectedSize)) {
            //ダウンロード時の場合はネットワーク障害にあるので、呼び出し元で判別できるように
            //例外をUnknownServiceExceptionとする。
            log.fine("writeToStream() output expected write size error");
            throw new UnknownServiceException("expected write size error");
        }
        log.fine("writeToStream <end>");
    }

    /**
     * パス作成
     */
    public static String makePath(String ... path) {
        String fullPath = path[0];
        for (int i = 1; i < path.length; i++) {
            fullPath += File.separator + path[i];
        }
        return fullPath;
    }

    /**
     * レスポンスからストリームを取得する。
     * サーバからファイルダウンロードのレスポンスを受け取った時に呼び出される。
     * @param response レスポンス
     * @return InputStream レスポンスから読み込んだストリーム
     */
    public static synchronized InputStream restoreStreamFromResponse(Response response) {
        InputStream is;
        if (response == null) {
            log.fine("restoreStreamFromResponse() error response nothing..");
            return null;
        }
        return response.body().byteStream();
    }

    /**
     * レスポンスからコンテンツデータサイズを取得
     * @return X-Contennt-Lengthの値
     */
    public static synchronized long restoreContentSizeFromResponse(Response response) {
        long length = -1;

        if (response == null) {
            log.fine("restoreContentSizeFromResponse() error response nothing..");
            return length;
        }

        String strLength = response.header(NbConsts.HEADER_X_CONTENT_LENGTH);
        if (strLength == null) {
            log.fine("restoreContentSizeFromResponse() error header nothing..");
            return length;
        }

        log.fine("restoreContentSizeFromResponse() strLength=" + strLength);
        try {
            length = Long.parseLong(strLength);
        } catch ( NumberFormatException e) {
            //e.printStackTrace();
            log.warning("Bad X-Content-Length value: {0}", e.getMessage());
        }

        log.fine("restoreContentSizeFromResponse() length=" + length);
        return length;
    }

    /**
     * レスポンスからETagを取得
     * @return ETagヘッダの値
     */
    public static String restoreFileETagFromResponse(Response response) {
        if (response == null) {
            log.fine("restoreFileETagFromResponse()"
                    + " error response nothing..");
            return null;
        }

        String fileETag = response.header(NbConsts.HEADER_ETAG);
        log.fine("restoreFileETagFromResponse() fileETag=" + fileETag);
        return fileETag;
    }

    /**
     * ファイルアクセスをオンラインですべきか否かを判定する。<br>
     *
     * @param bucketName バケット名
     * @param offlineService　　オフラインサービスのオブジェクト
     * @return オンラインアクセスであればtrue、否であればfalse
     */
    /*
    public static boolean isFileOnlineAccess(String bucketName, NbOfflineService offlineService) {
        // TODO: bucketName 別の判定追加

        //オフラインサービス機能無し、又はオンラインモード中ネットワークアクセス可能
        if ( (offlineService == null || offlineService.isOnline()) ) {
            return true;
        }

        return false;
    }
    */

    /**
     * 衝突発生時の状態更新処理。<br>
     * クライアントデータを取得して同期状態を「CONFLICT」に更新し、<br>
     * アプリ側が衝突したデータとわかるように
     * @param bucketName  バケット名
     * @param json        対象メタデータの（JSON形式）
     * @param offlineService オフラインサービス
     */
    /*
    public static synchronized void updateMetaWithConflict(String bucketName, NbJSONObject json,
                                                           NbFileOfflineService offlineService) {
        if (offlineService == null || json == null) {
            return;//Do Nothing
        }
        String reasonCode = NbUtil.getResponseReasonCode(json);
        log.fine("updateMetaWithConflict() reasonCode=" + reasonCode);

        //バケット毎のポリシーに従い衝突解決
        NbJSONObject clientJson = null;
        //ETagミスマッチ（サーバとクライアントの衝突）なら衝突解決を行う。
        if (NbReason.ETAG_MISMATCH.equals(reasonCode)) {
            NbJSONObject serverJson = NbUtil.getResponseDetail(json);
            log.fine("updateMetaWithConflict()"
                    + " conflicted");
            try {   //ローカルデータ取得
                clientJson = offlineService.readLocalMetadata(
                        serverJson.getString(NbKey.FILENAME), bucketName);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (clientJson != null) {
                switch (NbSyncState.fromObject(clientJson.get(NbKey.META_STATE), NbSyncState.NOSTATE)) {
                    case DIRTY:
                        clientJson.put(NbKey.META_STATE, NbSyncState.CONFLICTED.id);
                        clientJson.put(NbKey.FILE_STATE, NbSyncState.CONFLICTED.id);
                        break;
                    case DIRTY_FULL:
                        clientJson.put(NbKey.META_STATE, NbSyncState.CONFLICTED_FULL.id);
                        clientJson.put(NbKey.FILE_STATE, NbSyncState.CONFLICTED_FULL.id);
                        break;
                    case DELETE:
                        clientJson.put(NbKey.META_STATE, NbSyncState.CONFLICTED_DELETE.id);
                        clientJson.put(NbKey.FILE_STATE, NbSyncState.CONFLICTED_DELETE.id);
                        break;
                    default:
                        break;
                }
                try {   //同期状態を「Conflict」へ更新
                    offlineService.saveCacheMetadata(bucketName, clientJson);
                } catch (IllegalStateException | IOException e) {
                    //キャッシュ時のエラーはエラーとしない。
                }
            }
        }
    }
    */
}
