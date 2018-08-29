/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.util;

import com.nec.baas.core.*;
import com.nec.baas.json.*;
import com.nec.baas.offline.internal.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Nebulaライブラリ ユーティリティクラス
 * @since 1.0
 */
public abstract class NbUtil {
    private static final NbLogger log = NbLogger.getLogger(NbUtil.class);

    /**
     * スレッドプールの最大スレッド数。
     * この最大数までスレッドが生成される。
     */
    private static final int MAX_THREAD_POOL_SIZE = 100;

    /**
     * スレッドプールのコアサイズ。
     * プール内に常時維持されるスレッドの数。
     */
    private static final int CORE_THREAD_POOL_SIZE = 10;

    /**
     * スレッドプールの Keep Alive 時間(秒)。
     * スレッド数がコアサイズを超えると、この時間アイドル状態に入っているスレッドを終了する。
     */
    private static final int THREAD_KEEP_ALIVE_TIME = 60;

    /**
     * Daemon thread factory
     */
    private static class DaemonThreadFactory implements ThreadFactory {
        private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
        private String name;

        DaemonThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = defaultThreadFactory.newThread(runnable);
            thread.setName(String.format("%s-%s", name, thread.getId()));
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * バックグランド処理実行用スレッドプール
     */
    private static ExecutorService sExecutorService =
            new ThreadPoolExecutor(
                    CORE_THREAD_POOL_SIZE,
                    MAX_THREAD_POOL_SIZE,
                    THREAD_KEEP_ALIVE_TIME,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(), // Runnableキュー
                    new DaemonThreadFactory("NbUtilExecutor")
            );

    protected NbUtil() {
        //外部非公開のコンストラクタ定義（checkStyle回避）
    }

    private static boolean sIgnoreExceptionInBackgroundThread = true;

    /**
     * ExecutorService を差し替える
     * @param executorService ExecutorService
     */
    public static synchronized void setExecutorService(ExecutorService executorService) {
        sExecutorService.shutdown();
        sExecutorService = executorService;
    }

    /**
     * ライブラリ内バックグランドスレッドで例外が発生したときに、これを無視するかどうかを指定する。
     *
     * <p>false に設定すると、例外はそのまま throw される (プログラムが終了する)。
     * true に設定すると、例外は無視される。デフォルトは true。
     * <p>いずれの場合も stack trace はログ出力される。
     * @param ignore 無視する場合は true
     */
    public static void setIgnoreExceptionInBackgroundThread(boolean ignore) {
        sIgnoreExceptionInBackgroundThread = ignore;
    }

    /**
     * Runnable をバックグランド実行する
     * @param runnable Runnable
     */
    public static void runInBackground(final Runnable runnable) {
        sExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Exception ex) {
                    log.severe("Exception occurred in background thread!: {0}", ex.getMessage());
                    //ex.printStackTrace();
                    if (!sIgnoreExceptionInBackgroundThread) {
                        throw new RuntimeException("Exception occurred in runInBackground", ex);
                    }
                } catch (AssertionError error) {
                    NbJunitErrorNotifier.notify(error);
                }
            }
        });
    }

    /**
     * レスポンス(JSON文字列)をJSONObject形式に復元する
     * @param result Response
     * @return JSON Object
     */
    public static NbJSONObject restoreResponse(Response result) {
        String json;
        ResponseBody body = result.body();
        if (body == null) {
            return null;
        }
        try {
            json = body.string();
        } catch (IOException e) {
            //e.printStackTrace();
            log.warning("restoreResponse: {0}", e.getMessage());
            return null;
        }
        return NbJSONParser.parse(json);
    }

    /**
     * レスポンスからreasonCodeを取得する。
     */
    public static String getResponseReasonCode(NbJSONObject json) {
        return json.getString(NbKey.REASON_CODE);
    }

    /**
     * レスポンスからdetailを取得する。
     */
    public static NbJSONObject getResponseDetail(NbJSONObject jsonMap) {
        String reason = getResponseReasonCode(jsonMap);
        log.fine("getResponseDetail() reason=" + reason);

        //Conflict原因がETagMismatchの場合のみ「detail」フィールが存在する
        NbJSONObject detail = null;
        if (NbReason.ETAG_MISMATCH.equals(reason)) {
            detail = jsonMap.getJSONObject(NbKey.DETAIL);
//          log.fine("getResponseDetail() detail=" + map);
        }
        return detail;
    }

    /**
     * statusCode がネットワークエラーである(クライアントエラーでない)かを判定する。<br>
     * ネットワークエラーであればローカルDBへアクセスできる。<br>
     * ＜保守＞<br>
     * ・REST APIに規定外のエラーは全てネットワークエラーのポリシーで
     *   実装しているため、サーバ側でエラーコード追加された場合はここに追加する。<br>
     * ・isScopeSyncRetryNeeded()からも同条件のため呼び出されている。
     *   エラーコードを追加した場合はisRetryScopeSyncCheck()にも影響が無いか確認する。
     * @param statusCode ステータスコード（エラーコード）
     * @return ネットワークエラーならtrue、クライアントエラーであればfalse
     */
    public static boolean isNetworkError(int statusCode) {
        switch(statusCode) {
            case NbStatus.REQUEST_PARAMETER_ERROR:   //400
            case NbStatus.UNAUTHORIZED:              //401
            case NbStatus.FORBIDDEN:                 //403
            case NbStatus.NOT_FOUND:                 //404
            case NbStatus.CONFLICT:                  //409
                return false;

            default:
                return true;
        }
    }

    /**
     * 同期の再送が必要かどうか判定する。<br>
     * 引数のstatusCodeを参照し、ネットワークエラーであれば再送必要と判断する。
     * 現行、判定条件がisLocalAccessCheckと同等のため、isLocalAccessCheckの結果を
     * そのまま返す。
     * @param statusCode ステータスコード（エラーコード）
     * @return 再送必要ならtrue、再送表ならfalse
     */
    public static boolean isScopeSyncRetryNeeded(int statusCode) {
        boolean result = isNetworkError(statusCode);
        return result;
    }

    /**
     * オンライン状態であるか判定する。
     * <p>
     *     オフライン機能が有効、かつオフライン状態の場合はエラーとなり、失敗コールバックを呼び出す。
     *     オフライン機能が無効の場合は、常に成功と判定。
     * </p>
     * @param service オフラインサービス
     * @param tag モジュール名
     * @param callback コールバック
     * @return オンラインであれば true、そうでなければ false
     */
    public static boolean checkOnline(NbOfflineService service, final String tag,
                                  final NbBaseCallback callback) {
        if (service != null && !service.isOnline()) {
            // オフライン機能有効かつオフライン状態
            log.fine(tag + " this API cannot be used when network is offline.");
            callback.onFailure(NbStatus.FORBIDDEN, new NbErrorInfo("Network is offline."));
            return false;
        }
        return true;
    }

    /**
     * 文字列URLエンコード
     * @param fileName エンコード前の文字列（ファイル名）
     * @return エンコード後の文字列（ファイル名）※エンコード失敗時はエンコード前の文字列
     */
    public static String encodeUrl(String fileName) {
        //マルチバイト文字用にURLエンコード
        try {
            return URLEncoder.encode(fileName, NbConsts.DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            // 発生することはない
            throw new IllegalStateException(e);
        }
    }

    /**
     * 文字列比較 (null チェック付き)
     * @param srcString 比較元文字列
     * @param dstString 比較先文字列
     * @return trueの場合一致。falseの場合不一致。
     */
    public static boolean isStringEquals(String srcString, String dstString) {
        if (srcString == null) {
            return dstString == null;
        }

        return srcString.equals(dstString);
    }
}
