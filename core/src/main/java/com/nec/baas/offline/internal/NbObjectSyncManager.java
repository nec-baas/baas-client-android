/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.http.*;
import com.nec.baas.json.*;
import com.nec.baas.object.*;
import com.nec.baas.object.internal.*;
import com.nec.baas.offline.*;
import com.nec.baas.util.*;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 同期管理クラス
 * @since 1.0
 */
@Accessors(prefix = "m")
public abstract class NbObjectSyncManager implements NbObjectConflictResolver {
    private static final NbLogger log = NbLogger.getLogger(NbObjectSyncManager.class);

    // 同一インスタンスの追加を避けるため、mapのvalueにはSetInterfaceを採用
    // 非同期でのアクセスを考慮してConcurrentHashMapを使用
    /*package*/ Map<String, Set<NbObjectSyncEventListener>> mSyncEventListeners = new ConcurrentHashMap<>();

    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PACKAGE)
    private NbHttpRequestFactory mHttpRequestFactory;

    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PACKAGE)
    private NbRestExecutorFactory mRestExecutorFactory;

    @Setter(AccessLevel.PACKAGE)
    protected NbDatabaseManager mDatabaseManager;

    @Setter(AccessLevel.PACKAGE)
    private NbOfflineService mOfflineService;

    protected static final String RESULT_OK = "ok";
    protected static final String RESULT_CONFLICT = "conflict";
    protected static final String RESULT_FORBIDDEN = "forbidden";
    protected static final String RESULT_NOT_FOUND = "notFound";
    protected static final String RESULT_BAD_REQUEST = "badRequest";
    protected static final String RESULT_SERVER_ERROR = "serverError";

    /**
     * Pullの分割同期数<br>
     * 同期でObjectをpullする際、1度のqueryで取得するObjectの最大数
     */
    public static final int PULL_SPLIT_OBJECTS_NUM = 1000;

    /**
     * PullUpdate処理のキュー容量<br>
     * Pull時にキュー容量に空きが無い場合、処理がブロックされる
     */
    public static final int PULL_UPDATE_QUEUE_CAPACITY = 1;

    /**
     * 分割同期 PUSH処理 分割オブジェクト数
     */
    public static final int PUSH_SPLIT_OBJECTS_NUM = 100;

    /**
     * pull時に行う待ち合わせ処理のタイムアウト時間
     */
    private static final long BUCKET_PULL_TIMEOUT = 60000;    //1分

    private static final long BUCKET_PUSH_TIMEOUT = 60000;
    private static final int QUERY_ALL_GET = -1;

    /**
     * Pull サーバ時刻オフセット(ミリ秒)。
     * サーバ側データの時刻逆転などに対応するためのマージン。
     */
    public static final long PULL_TIME_OFFSET = 3000;

    /**
     * コンフリクト発生時にサーバ側のデータを一時保存する Map。
     * キーは ObjectID, 値は NbObjectEntity。
     * コンフリクト解消時にデータは削除する。
     */
    private Map<String, NbObjectEntity> mConflictedServerObjects = new HashMap<>();

//    private static Timer mTimer = new Timer() ;
//    private static Timer mRetryTimer = new Timer() ;

//    /**
//     * 自動同期用のタスク管理
//     * キーはbucket名
//     */
//    private Map<String, AutoSyncTask> mAutoSyncTaskMap = new HashMap<>();

    /**
     * Push送信予定オブジェクトの(検索)キャッシュ
     */
    private List<NbObjectEntity> mObjectsCacheToPush;

    /**
     * PULL時のサーバ現在時刻
     * 宣言時以外で初期化はしない。サービス稼動中は前回のPULL時刻を常に保持しておき、
     * 同期後の手動解決するケースで本データを使用し、前回同期時刻を更新できるようにしておく。
     */
    protected String mServerCurrentTime = null;

//    /**
//     * 自動同期時の再送データを保存するための Map。
//     * キーはバケット名、値は Batch Map。
//     */
//    private HashMap<String, BatchArg> mBatchRetransmitPendingMap = new HashMap<>();

//    /** バッチ再送データ */
//    static class BatchArg {
//        /** リクエストパラメータ */
//        Map<String, String> param;
//
//        /** Batch リクエスト JSON */
//        NbJSONObject batchJson;
//    }

    /**
     * 未PUSHオブジェクトIDリスト
     */
    protected String[] mPushDirtyObjectIds = null;

    /**
     * 同期時に発生したエラーイベント
     */
    protected SyncErrorInfoContainer mSyncErrorEvent = null;

    /**
     * 同期時に発生した衝突イベント
     */
    protected List<SyncConflictInfoContainer> mSyncConflictEvents = new ArrayList<>();


    /**
     * 同期状態フラグ
     *
     *
     * <ul>
     *     <li>自動同期が実行された場合は、すべてtrueになる。</li>
     *     <li>手動のオブジェクト、バケット単体の同期はsyncのみtrueになる。</li>
     *     <li>手動の範囲同期はsyncとscopeSyncingがtrueになる。</li>
     * </ul>
     */
    @Getter
    @Accessors(prefix = "")
    public static class SyncStates {
        /** 同期中 */
        private boolean syncing = false;
//        /** 自動同期中 */
//        private boolean autoSyncing = false;
        /** 範囲同期中 */
        private boolean scopeSyncing = false;
//        /** 再送中 */
//        private boolean syncRetrying = false;

        /** 手動オブジェクト・バケット単体同期 */
        public void startSync() {
            syncing = true;
        }

        /** 範囲同期 */
        public void startScopeSync(boolean auto) {
            syncing = true;
            scopeSyncing = true;
//            autoSyncing = auto;
        }

//        /** 範囲同期(自動) */
//        public void startScopeAutoSync() {
//            syncing = true;
//            scopeSyncing = true;
//            autoSyncing = true;
//        }

//        /** 再送開始 */
//        public void startSyncRetry() {
//            syncing = true;
//            syncRetrying = true;
//        }

        /** 同期停止 */
        public void stopSync() {
            syncing = false;
//            autoSyncing = false;
            scopeSyncing = false;
//            syncRetrying = false;
        }
    }

    @Getter
    private SyncStates mSyncStates = new SyncStates();

    /**
     * コンストラクタ
     */
    public NbObjectSyncManager() {
    }

    private void executeRequest(Request request, NbRestResponseHandler handler) {
        getRestExecutorFactory().create().executeRequest(request, handler);
    }

    /** {@inheritDoc} */
    @Override
    public void resolveConflict(final String bucketName, final String objectId, final NbConflictResolvePolicy resolve) {
        log.fine("resolveConflict() thread execute.");
        NbUtil.runInBackground(new Runnable() {
            public void run() {
                resolveConflict(bucketName, objectId, resolve, null);
            }
        });
    }

    /**
     * コンフリクト解決処理。
     * @param bucketName バケット名
     * @param objectId 解決対象のオブジェクトID
     * @param resolve 解決方法。RESOLVE_CLIENT または RESOLVE_SERVER。
     * @param syncObjects オブジェクトIDが変更された場合、
     *                    新オブジェクトIDとサーバオブジェクトIDが格納される。
     * @return オブジェクトID。オブジェクトIDは変更される場合がある。
     */
    private String resolveConflict(@NonNull String bucketName, @NonNull String objectId,
                                   @NonNull NbConflictResolvePolicy resolve, Set<String> syncObjects) {
        log.fine("resolveConflict() <start>"
                + " bucketName=" + bucketName + " objectId=" + objectId + "resolve=" + resolve);

        NbOfflineObject resolveObject = null;

        log.fine("resolveConflict() mConflictedServerObjects.size()="
                + mConflictedServerObjects.size());

        NbObjectEntity server = mConflictedServerObjects.get(objectId);

        String newObjectId = objectId;
        switch (resolve) {
            case CLIENT:
                NbObjectEntity client = null;
                try {
                    client = mDatabaseManager.readObject(objectId, bucketName);
                } catch (NbDatabaseException e) {
                    //処理無し
                }
                if (client != null) {
                    if (resolveConflictClient(bucketName, client, server)) {
                        resolveObject = new NbOfflineObject(bucketName);
                        resolveObject.setCurrentParam(client.getImmutableJsonObject());
                        mConflictedServerObjects.remove(objectId);
                        //再送データ削除
//                        deleteRetryData(objectId, bucketName);
                    }
                    //サーバ側がデータ削除した場合は、クライアント側のIDは採番されてpushされる。
                    //その場合は両IDを同期完了として通知する。
                    //また、後続へ新しいオブジェクトIDを流すためnewObjectIdを更新。
                    newObjectId = client.getObjectId();
                    log.fine("resolveConflict()"
                            + " newObjectId=" + newObjectId + " server.getObjectId()="
                            + server.getObjectId() );
                    if ( (syncObjects != null) && (!newObjectId.equals(server.getObjectId())) ) {
                        syncObjects.add(newObjectId);
                        syncObjects.add(server.getObjectId());
                    }
                }
                break;

            case SERVER:
                if (server != null) {
                    if (resolveConflictServer(bucketName, server)) {
                        resolveObject = new NbOfflineObject(bucketName);
                        resolveObject.setCurrentParam(server.getImmutableJsonObject());
                        mConflictedServerObjects.remove(objectId);
                        //再送データ削除
//                        deleteRetryData(objectId, bucketName);
                    }
                }
                break;

            default:
                throw new IllegalArgumentException();
        }

        if (resolveObject != null) {
            //同期中に衝突が発生した場合は前回同期時刻は更新されない。
            //そのため同期終了後に衝突解決した場合は、同期時刻の更新が必要
            log.severe("resolveConflict() sync state=" + getLogSyncState());

//            if (mBatchRetransmitPendingMap.isEmpty()) {
//                //再送停止。再送データが無くなった場合はキューに積まれているタスクを解放
//                stopRetrySync();
//            }

            //同期終了後のコンフリクト解決、且つコンフリクト無し
            if (!mSyncStates.isSyncing()) {
                //バケット単位に更新
                updateLastSyncTimeAndFinishSyncing(bucketName);
                //全体でコンフリクトが存在しない場合はクリア
                clearTemporaryConflictObjects();
            }

            notifyOnResolveConflict(resolveObject, resolve);
        }
        log.fine("resolveConflict() <end> newObjectId=" + newObjectId);
        return newObjectId;
    }

    private void notifyOnResolveConflict(final NbOfflineObject resolveObject, final NbConflictResolvePolicy resolvePolicy) {
        NbServiceImpl.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String bucketName = resolveObject.getBucketName();
                Set<NbObjectSyncEventListener> listeners = getEventListeners(bucketName);
                for (NbObjectSyncEventListener listener : listeners) {
                    listener.onResolveConflict(resolveObject, resolvePolicy);
                }
            }
        });
    }

    /**
     * コンフリクト中のオブジェクトをクリアする。
     * ただし DB 内に衝突データが1件でもある場合はクリアしない。
     */
    /*package*/ void clearTemporaryConflictObjects() {
        if (!isDbConflicting()) {
            mConflictedServerObjects.clear();
        }
    }

    /**
     * バケット内の全同期中(SYNCING)データを同期済み(SYNC)に変更し、
     * 最終更新時刻を更新する。
     * ただし衝突データが存在する場合は更新しない。
     *
     * @param bucketName バケット名
     */
    /*package*/ void updateLastSyncTimeAndFinishSyncing(String bucketName) {
        if (!isDbConflicting(bucketName)) {
            updateDbLastTime(bucketName);         //同期時刻を更新
            //同期状態をSYNCING→SYNCに更新
            try {
                mDatabaseManager.updateSyncingObjects(bucketName);
            } catch (NbDatabaseException e) {
                log.severe("updateLastSyncTimeAndFinishSyncing()"
                        + " updateSyncingObjects Err NbDatabaseException e=" + e);
            }
        }
    }

    /**
     * 同期衝突通知用コンテナ
     */
    @Accessors(prefix = "")
    @Getter
    @Setter
    protected class SyncConflictInfoContainer {
        private NbObjectConflictResolver resolver = null;

        private String targetBucket = null;

        private NbObject clientObject = null;

        private NbObject serverObject = null;
    }

    /**
     * 同期エラー通知用コンテナ
     */
    @Accessors(prefix = "")
    @Getter
    @Setter
    protected  class SyncErrorInfoContainer {
        private String targetBucket = null;

        private NbObjectSyncEventListener.SyncError errorCode = null;

        private NbObject errorObject = null;
    }

    /**
     * 同期処理結果受け渡し用コンテナ
     */
    @Accessors(prefix = "")
    @Getter
    @Setter
    protected class SyncResultContainer {
        String pullObjects;
        private Set<String> pullObjectIds;
        private Set<String> syncObjects;
        int pullResult = NbStatus.OK;
        int pullUpdateResult = NbStatus.OK;
        int pushResult = NbStatus.OK;
        int pushUpdateResult = NbStatus.OK;
    }


    /**
     * オブジェクトバケット同期を実行する。
     * @param bucketName バケット名
     * @param query クエリ
     * @return SyncResult:同期結果。成功時は NbUtil.OK。衝突時は NbUtil.CONFLICT。
     */
    protected int sync(String bucketName, NbQuery query) {
        log.fine("sync() <start> bucketName=" + bucketName);

        //圏外時は同期不可
        if (!mOfflineService.isOnline()) {
            log.severe("sync() <end> ERR not online");
            return NbStatus.UNPROCESSABLE_ENTITY_ERROR;
        }
        if (bucketName == null) {
            log.fine("sync() <end> ERR bucketName==null");
            return NbStatus.REQUEST_PARAMETER_ERROR;
        }
        // nullの場合は条件未設定として扱う
        if(query == null) {
            query = new NbQuery();
        }

        // 同期開始を通知
        notifySyncStart(bucketName);

        // 処理結果格納用のコンテナ
        SyncResultContainer resultContainer = new SyncResultContainer();

        // pull,PullUpdate
        // Pull完了後は最後にPullしたオブジェクトを保持する
        final Set<String> syncObjects = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        final Set<String> pullObjectIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        // Output用のパラメータをコンテナに格納
        resultContainer.setPullObjectIds(pullObjectIds);
        resultContainer.setSyncObjects(syncObjects);

        // Pull/PullUpdate実行
        procPull(bucketName, query, resultContainer);
        
        // Push/PushUpdate実行
        procPush(bucketName, resultContainer);

        int result = NbStatus.OK;
        //アプリへエラー通知済みであれば、戻り値もエラーで返却する。
        if ((NbStatus.isNotSuccessful(resultContainer.getPullResult())) || (NbStatus.isNotSuccessful(resultContainer.getPullUpdateResult()))
                || (NbStatus.isNotSuccessful(resultContainer.getPushResult())) || (NbStatus.isNotSuccessful(resultContainer.getPushUpdateResult())) ) {
            log.severe("sync()"
                    + " pullResult=" + resultContainer.getPullResult() + " pullUpdateResult=" + resultContainer.getPullUpdateResult()
                    + " pushResult=" + resultContainer.getPushResult() + " pushUpdateResult=" + resultContainer.getPushUpdateResult());
            result = NbStatus.INTERNAL_SERVER_ERROR;
        }

        // 同期衝突通知
        notifySyncConflictedEvents();

        // 同期エラー通知
        notifySyncErrorOnce();

        //同期完了通知
        notifySyncCompleted(bucketName, syncObjects);

        if (resultContainer.getPullUpdateResult() == NbStatus.CONFLICT ||
                resultContainer.getPushUpdateResult() == NbStatus.CONFLICT) {
            //結果格納：コンフリクト発生エラー
            result = NbStatus.CONFLICT;
        }

        log.fine("sync() <end> result=" + result);
        return result;
    }

    /**
     * PullUpdate実行用パラメータ格納クラス
     */
    @Accessors(prefix = "")
    @Getter
    @Setter
    protected class PullUpdateRequestContainer {
        //@Getter @Setter
        //private ObjectIdContainer objectIdContainer;

        private String bucketName;
        private NbJSONObject mergeData;
        private Set<String> pullObjectIds;
        private Set<String> syncObjects;
        private Boolean stopRequest;
    }

    /**
     * PullUpdate管理クラス<br>
     */
    @Accessors(prefix = "m")
    protected class PullUpdateManager implements Callable<Boolean> {

        @Setter // for test
        private ExecutorService mExecutor;
        private BlockingQueue<PullUpdateRequestContainer> mRequestsQueue;

        /**
         * コンストラクタ
         *
         * @param deque PullUpdate処理要求用のキュー
         */
        public PullUpdateManager(BlockingQueue<PullUpdateRequestContainer> deque) {
            // リクエスト格納用キュー
            this.mRequestsQueue = deque;
            // PullUpdateタスク実行用スレッド
            this.mExecutor = Executors.newSingleThreadExecutor();
        }

        /**
         * PullUpdateManager実行
         * PullUpdateタスクを逐次実行する
         * 処理に失敗した場合は、後続の要求は実行しない
         * 処理を停止する場合は、PullUpdateRequestContainer#stopRequestにtrueを指定すること
         *
         * @return コンフリクト発生有無
         * @throws InterruptedException
         * @throws ExecutionException
         */
        @Override
        public Boolean call() throws InterruptedException, ExecutionException {
            boolean isConflict = false;

            log.fine("PullUpdateManager(): start");

            // 停止要求があるか、異常発生までループ
            while(true) {
                // データが格納されるまでBlockされる
                PullUpdateRequestContainer container = mRequestsQueue.take();
                // 停止要求を受けた
                if(container.getStopRequest()) {
                    // PullUpdateタスクを終了
                    mExecutor.shutdown();
                    break;
                }

                // PullUpdateタスクの実行
                PullUpdateTask task = createPullUpdateTask(container);
                Future<Boolean> result = mExecutor.submit(task);

                // 処理結果が取得できるまで待機
                try {
                    // 一度でもconflictを検出したらコンフリクト発生とみなす
                    isConflict |= result.get();
                } catch (InterruptedException | ExecutionException e) {
                    // PullUpdateに失敗した場合、以降のPullUpdateは行わない
                    log.severe("PullUpdateManager() PullUpdate failed. " + e);
                    // PullUpdateタスク実行を終了
                    mExecutor.shutdown();
                    // 異常終了
                    throw e;
                }
            }

            log.fine("PullUpdateManager(): finished " + isConflict);

            return isConflict;
        }

        /**
         * PullUpdateTask生成
         *
         * @param container PullUpdateの実行用パラメータ
         * @return 生成したタスク
         */
        public PullUpdateTask createPullUpdateTask(PullUpdateRequestContainer container) {
            return new PullUpdateTask(container);
        }
    }

    /**
     * PullUpdateタスク
     */
    protected class PullUpdateTask implements Callable<Boolean> {
        private PullUpdateRequestContainer mContainer;

        /**
         * コンストラクタ
         * @param container PullUpdate実行用の情報
         */
        public PullUpdateTask(PullUpdateRequestContainer container) {
            this.mContainer = container;

            // パラメータチェック
            if(mContainer == null ||
                    //mContainer.getObjectIdContainer() == null ||
                    mContainer.getBucketName() == null ||
                    mContainer.getMergeData() == null ||
                    mContainer.getPullObjectIds() == null ||
                    mContainer.getSyncObjects() == null) {
                throw new IllegalArgumentException("invalid argument detected");
            }
        }

        /**
         * PullUpdate実行
         * PullUpdate処理を行う
         *
         * @return コンフリクトの発生有無
         */
        @Override
        public Boolean call() {
            //final ObjectIdContainer objectIdContainer = mContainer.getObjectIdContainer();
            final String bucketName = mContainer.getBucketName();
            final NbJSONObject mergeData = mContainer.getMergeData();
            final Set<String> pullObjectIds = mContainer.getPullObjectIds();
            final Set<String> syncObjects = mContainer.getSyncObjects();

            //final String objectId = objectIdContainer.getObjectId();
            boolean isConflict;

            log.fine("PullUpdateTask(): start");

            try {
                isConflict = pullUpdateList(bucketName, mergeData, syncObjects);
            } catch (IllegalStateException e) {
                log.severe("PullUpdateTask failed. " + e);
                throw e;
            }

            // 処理成功したたため、後処理を実行
            // バケット同期
            // 時刻情報の更新、処理結果ObjectIdのリスト追加を行う
            // 処理結果に含まれるオブジェクトのリスト生成
            NbJSONArray<NbJSONObject> dataList = mergeData.getJSONArray(NbKey.RESULTS);

            // ListにObjectが含まれている場合のみPullUpdate実行
            if (dataList != null && !dataList.isEmpty()) {
                // PullUpdate成功
                // Pullで取得したObjectIdをリストに追加
                for (NbJSONObject json : dataList) {
                    String objectIdForList = json.getString(NbKey.ID);
                    pullObjectIds.add(objectIdForList);
                }
            }

            log.fine("PullUpdateTask(): finished " + isConflict);

            return isConflict;
        }
    }

    /**
     * Pull/PullUpdateの処理を行う
     *  @param bucketName bucket名
     * @param baseQuery アプリ指定のクエリ
     * @param resultContainer 処理結果格納用コンテナ
     */
    protected void procPull(String bucketName, NbQuery baseQuery, SyncResultContainer resultContainer) {
        int pullResult = NbStatus.OK;
        int pullUpdateResult = NbStatus.OK;
        String pullObjects = null;

        // コンテナからOutput用のインスタンスを取得
        final Set<String> pullObjectIds = resultContainer.getPullObjectIds();
        final Set<String> syncObjects = resultContainer.getSyncObjects();

        // 初回クエリ条件を取得
        NbQuery pullQuery = createDividePullBaseQuery(baseQuery);
        // Databaseから前回同期時のサーバ時刻を取得し条件に加える
        addPullServerTimeCondition(bucketName, pullQuery);

        // PullUpdateManagerの実行スレッドを生成
        final ExecutorService managerExecutor = createExecutorService();
        // managerタスクを生成
        // 処理要求を渡すキューを生成
        final BlockingQueue<PullUpdateRequestContainer> pullUpdateRequestQueue = new LinkedBlockingDeque<>(PULL_UPDATE_QUEUE_CAPACITY);
        final PullUpdateManager manager = createPullUpdateManager(pullUpdateRequestQueue);
        final Future<Boolean> managerFuture = managerExecutor.submit(manager);

        // 分割同期の初回判定用変数
        boolean isFirstPull = true;

        // 分割同期のPull初回のサーバ時刻
        String serverPullTimeSave = null;

        // Pullを分割実行
        while(true) {
            // Pull実行
            try {
                log.fine("procPull(): pull start");
                // 初回のみバケット情報を取得し、以降分割同期中はバケットの取得はスキップする
                pullObjects = pull(bucketName, pullQuery, syncObjects, isFirstPull);
                log.fine("procPull(): pull finished: " + pullObjects);
            } catch (IllegalStateException e) {
                pullResult = NbStatus.INTERNAL_SERVER_ERROR;
                log.severe("procPull(): pull failed");
            }

            // Pullに失敗した場合、処理を中断する
            if (pullObjects == null || NbStatus.isNotSuccessful(pullResult)) {
                pullResult = NbStatus.INTERNAL_SERVER_ERROR;
                // Objectの取得が正常にできなかった場合、PullUpdateも失敗とする
                pullUpdateResult = NbStatus.INTERNAL_SERVER_ERROR;
                log.severe("procPull(): pull Failed. object not found");
                break;
            }

            // managerタスクが動作しているか判断
            if (managerFuture.isDone()) {
                // Pull側から停止要求を出していないにも関わらずmanagerが停止しているため、
                // PullUpdate失敗と判断
                pullUpdateResult = NbStatus.INTERNAL_SERVER_ERROR;
                log.severe("procPull(): manager unexpectedly stopped.");
                break;
            }

            // 取得したデータをパース
            final NbJSONObject mergeDataJson = NbJSONParser.parse(pullObjects);
            if (mergeDataJson == null) {
                // parse失敗
                pullUpdateResult = NbStatus.INTERNAL_SERVER_ERROR;
                log.severe("procPull(): parseObject failed");
                break;
            }
            mergeDataJson.setImmutable();

            // バケット単位同期の場合にクエリ結果を解析
            NbJSONArray<NbJSONObject> objectsList;
            objectsList = mergeDataJson.getJSONArray(NbKey.RESULTS);

            if (objectsList == null) {
                // JSONの応答が期待しないフォーマット
                pullUpdateResult = NbStatus.INTERNAL_SERVER_ERROR;
                log.severe("procPull(): failed to get Result from Server");
                break;
            }

            // 分割1回目のサーバ時刻を保持しておく
            if (isFirstPull) {
                serverPullTimeSave = mergeDataJson.getString(NbKey.CURRENT_TIME);
                log.fine("procPull():  serverPullTimeSave: " + serverPullTimeSave);
            }
            isFirstPull = false;

            if (objectsList.isEmpty()) {
                // 取得対象のObjectが無い場合(前回すべてのObjectを取得した)はPull完了とみなす
                // 保存するデータが無いためPullUpdateも不要
                break;
            }

            // ManagerへのPullUpdateの実行要求
            PullUpdateRequestContainer container = new PullUpdateRequestContainer();
            //container.setObjectIdContainer(objectIdContainer);
            container.setBucketName(bucketName);
            container.setMergeData(mergeDataJson);
            container.setPullObjectIds(pullObjectIds);
            container.setSyncObjects(syncObjects);
            container.setStopRequest(false);

            log.fine("procPull(): put pullUpdateRequestQueue PullUpdateRequest start " + objectsList.size() + " objects");
            try {
                // キューに空き容量が無い場合は待機
                pullUpdateRequestQueue.put(container);
            } catch (InterruptedException e) {
                // fail safe
                // キューへの投入に失敗したため、PullUpdate失敗と扱う
                pullUpdateResult = NbStatus.INTERNAL_SERVER_ERROR;
                log.severe("procPull(): failed to put PullUpdateContainer" + e);
                break;
            }
            log.fine("procPull(): put pullUpdateRequestQueue PullUpdateRequest finished");

            // 取得が完了しているか判定
            // ObjectListがnull(Object単体同期)、もしくは分割数に満たない(バケット同期)
            // 条件に合うオブジェクトは全て取得したとみなす
            if (objectsList == null || objectsList.size() < PULL_SPLIT_OBJECTS_NUM) {
                // pull完了
                break;
            }

            // 次回pullの条件作成
            // バケット同期の場合のみ
            pullQuery = nextPullCondition(baseQuery, objectsList);
        }

        // Managerタスクへの停止要求を格納
        PullUpdateRequestContainer stopContainer = new PullUpdateRequestContainer();
        stopContainer.setStopRequest(true); // 停止要求のみ設定

        log.fine("procPull(): put pullUpdateRequestQueue StopRequest start");
        try {
            // キューの空きができるまで待機
            pullUpdateRequestQueue.put(stopContainer);
            // Managerの新規タスク受付禁止
            // 停止済みの場合は何もしない
            managerExecutor.shutdown();
        } catch (InterruptedException e) {
            // fail safe
            // 停止要求の登録に失敗したため、異常と判断
            pullUpdateResult = NbStatus.INTERNAL_SERVER_ERROR;
            // managerExecutorの停止契機がなくなるため強制的に停止する
            List<Runnable> tasks = managerExecutor.shutdownNow();
            log.severe("procPull(): failed to put PullUpdateStopRequest. tasks:" +  tasks + e);
        }
        log.fine("procPull(): put pullUpdateRequestQueue StopRequest finished");

        // PullUpdate処理結果取得
        boolean conflicted = false;
        try {
            // 実行中のpullUpdateの処理が存在する場合は完了まで待機する
            conflicted = managerFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            pullUpdateResult = NbStatus.INTERNAL_SERVER_ERROR;
            log.severe("procPull(): getResult failed :" + e);
        }

        // 前回サーバ Pull 時刻保存
        if (NbStatus.isSuccessful(pullUpdateResult) && serverPullTimeSave != null) {
            mDatabaseManager.updateLatestPullServerTime(bucketName, serverPullTimeSave);
            log.fine("procPull():  updateLatestPullServerTime serverPullTimeSave: " + serverPullTimeSave);
        }

        if(conflicted) {
            pullUpdateResult = NbStatus.CONFLICT;
        }

        // 処理結果コンテナに設定
        resultContainer.setPullObjects(pullObjects);
        resultContainer.setPullObjectIds(pullObjectIds);
        resultContainer.setSyncObjects(syncObjects);
        resultContainer.setPullResult(pullResult);
        resultContainer.setPullUpdateResult(pullUpdateResult);
    }

    /**
     * ExecutorServiceの生成を行う
     * @return ExecutorServiceのインスタンス
     */
    protected ExecutorService createExecutorService() {
        return Executors.newSingleThreadExecutor();
    }

    /**
     * PullUpdateManagerの生成を行う
     * @param pullUpdateRequestQueue 情報受け渡しのためのキュー
     * @return 生成したPullUpdateManagerのインスタンス
     */
    protected PullUpdateManager createPullUpdateManager(BlockingQueue<PullUpdateRequestContainer> pullUpdateRequestQueue) {
        return new PullUpdateManager(pullUpdateRequestQueue);
    }

    /**
     * Pullの次回のクエリ条件を生成する<br>
     * アプリの設定した条件に対し、sortOrder,limit,skipCountの上書き、更新日時を加味したwhere条件の付与を行う
     *
     * where条件には以下を加える<br>
     * "最新のUpdatedAtより新しい" || ("最新のUpdatedAtと一致" && "ObjectIdが現在取得した最新のものより大きい")
     *
     * @param pullConditionQuery アプリが設定したベースのクエリ
     * @param objectsList Pullで取得したObjectのリスト
     * @return 分割同期の条件を加えたクエリ
     */
    protected NbQuery nextPullCondition(@NonNull NbQuery pullConditionQuery, @NonNull NbJSONArray<NbJSONObject> objectsList) {

        if (objectsList.isEmpty()) {
            throw new IllegalArgumentException("nextPullCondition() IllegalArgument");
        }

        NbQuery nextQuery = createDividePullBaseQuery(pullConditionQuery);

        // 検索条件を追加
        // リスト末尾の最終オブジェクト更新日時を設定
        int listSize = objectsList.size();
        NbJSONObject lastObject = objectsList.get(listSize - 1);

        // "最新のUpdatedAtより新しい" || ("最新のUpdatedAtと一致" && "ObjectIdが現在取得した最新のものより大きい")条件を生成
        String latestUpdatedAt = lastObject.getString(NbKey.UPDATED_AT);
        String latestObjectId = lastObject.getString(NbKey.ID);

        NbClause dividePullClause = new NbClause().or(
                new NbClause().greaterThan(NbKey.UPDATED_AT, latestUpdatedAt),
                new NbClause().equals(NbKey.UPDATED_AT, latestUpdatedAt).greaterThan(NbKey.ID, latestObjectId));

        // アプリが指定した条件と結合
        NbClause appSetClause = nextQuery.getClause();
        appSetClause.and(dividePullClause);

        return nextQuery;
    }

    /**
     * Push処理を行う
     *  @param bucketName 対象バケット名
     * @param resultContainer 同期結果受け渡し用コンテナ
     */
    protected void procPush(String bucketName, SyncResultContainer resultContainer) {

        NbOfflineResult pushObjects;
        int pushResult = NbStatus.OK;
        int pushUpdateResult = NbStatus.OK;

        //String objectId = objectContainer.getObjectId();
        Set<String> syncObjects = resultContainer.getSyncObjects();
        String pullObjects = resultContainer.getPullObjects();
        Set<String> pullObjectIds = resultContainer.getPullObjectIds();

        ExecutorService pushExecutor = createExecutorService();
        List<Future<Integer>> pushUpdateFutureList = new ArrayList<>();

        while (true) {

            // push実行
            pushObjects = push(null/*objectId*/, bucketName, pullObjects, pullObjectIds);

            // push処理結果格納
            if (pushObjects != null){
                pushResult = (NbStatus.isSuccessful(pushResult)) ? pushObjects.getStatusCode() : pushResult;
            } else {
                pushResult = (NbStatus.isSuccessful(pushResult)) ? NbStatus.INTERNAL_SERVER_ERROR : pushResult;
                pushUpdateResult = (NbStatus.isSuccessful(pushUpdateResult)) ? NbStatus.INTERNAL_SERVER_ERROR : pushUpdateResult;
            }

            // push結果反映
            if (pushObjects != null) {
                PushUpdateTask task = new PushUpdateTask(pushObjects, null/*objectId*/, bucketName, syncObjects);
                Future<Integer> pushUpdateFuture = pushExecutor.submit(task);
                pushUpdateFutureList.add(pushUpdateFuture);
            }

            // オブジェクト同期か未同期オブジェクトがない場合は終了
            if (/*objectId != null ||*/ mPushDirtyObjectIds.length == 0) {
                mPushDirtyObjectIds = null;
                break;
            }

        }

        // pushUpdate処理結果取得
        for (Future<Integer> result : pushUpdateFutureList) {
            int ret = NbStatus.OK;

            try{
                ret = result.get();
            } catch (InterruptedException | ExecutionException e) {
                ret = NbStatus.INTERNAL_SERVER_ERROR;
            }finally {
                pushUpdateResult = (NbStatus.isSuccessful(pushUpdateResult)) ? ret : pushUpdateResult;
            }

        }

        pushExecutor.shutdown();

        // 処理結果をコンテナに格納
        resultContainer.setSyncObjects(syncObjects);
        resultContainer.setPushResult(pushResult);
        resultContainer.setPushUpdateResult(pushUpdateResult);
    }

    /**
     * バケット内に衝突データがあるか確認する
     * @param bucketName バケット名
     * @return 1件でも衝突データがあれば true、1件もなければ false
     */
    private boolean isDbConflicting(String bucketName) {
        boolean isExist = false;
        try {
            isExist = mDatabaseManager.isObjectConflictExists(bucketName);
        } catch (NbDatabaseException e) {
            //処理不要
        }
        log.fine("isDbConflicting(bucketName) isExist=" + isExist);
        return isExist;
    }

    /**
     * DB内の全バケットに衝突データが存在するか確認する
     * @return 1件でも衝突データがあれば true、1件もなければ false
     */
    private boolean isDbConflicting() {
        boolean isExist = false;
        try {
            //バケット一覧取得
            List<NbBucketEntity>bucketList = mDatabaseManager.readBucketList(false);
            log.fine("isDbConflicting()"
                    + " bucketList.size()=" + bucketList.size());
            for (NbBucketEntity bucketInfo: bucketList) {
                String bucketName = bucketInfo.getBucketName();
                //指定バケット内で最新のデータ更新日を取得
                try {
                    isExist = mDatabaseManager.isObjectConflictExists(bucketName);
                } catch (NbDatabaseException e) {
                    isExist = false;
                }
                if (isExist) {
                    break;
                }
            }
        } catch (NbDatabaseException e) {
            //処理不要
        }
        log.fine("isDbConflicting() isExist=" + isExist);
        return isExist;
    }

    /**
     * バケットの前回同期時刻をDB保存する。
     * 本メソッドはバケット単位で同期処理が行われた後に呼び出される。
     * 但し、直前の同期結果が正常のみである。（異常終了や衝突検知時は呼び出してはいけない）
     * 前回同期時刻はバケット単位同期でPUSH時のクライアント時刻をデータとする。
     * @param bucketName バケット名
     */
    private void updateDbLastTime(String bucketName) {
        if (bucketName == null) {
            log.severe("updateDbLastTime() bucketName=null");
            return;
        }

        String time = getCurrentTime();

        if (time == null) {
            log.severe("updateDbLastTime() can not get current time");
            return;
        }

        try {    //今回の同期時刻をDBへ保存する
            mDatabaseManager.updateLastSyncTime(bucketName, time);
        } catch (NbDatabaseException e) {
            //e.printStackTrace();
            log.warning("updateDbLastTime: {0}", e.getMessage());
        } catch (Exception e) {
            //e.printStackTrace();
            log.warning("updateDbLastTime: {0}", e.getMessage());
        }
    }

    /**
     * 現在時刻を取得する。
     * フォーマット："yyyy-MM-dd'T'HH:mm:ss.sss'Z'"
     * @return 現在時刻
     */
    private String getCurrentTime() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        DateFormat df = new SimpleDateFormat(NbConsts.TIMESTAMP_FORMAT);
        df.setTimeZone(cal.getTimeZone());
        return df.format(cal.getTime());
    }

    /**
     * NbOfflineResult を保存する NbRestResponseHandler。
     * <p>
     * statusCode, json は mContainer に自動的に設定される。
     * handleResponse() は実装済み。onSuccess(), onError() を適宜オーバライドすること。
     */
    @Accessors(prefix = "")
    protected static abstract class OfflineResultResponseHandler extends NbAwaitableRestResponseHandler {
        @Getter(AccessLevel.PACKAGE)
        private NbOfflineResult container;
        volatile private String mJson;

        public OfflineResultResponseHandler() {
            container = new NbOfflineResult();
            container.setStatusCode(NbStatus.REQUEST_TIMEOUT); // fallback
        }

        public NbOfflineResult getOfflineResultContainer() {
            return container;
        }

        public String getJson() {
            return mJson;
        }

        @Override
        public int preHandleResponse(Response response) {
            int status = response.code();
            container.setStatusCode(status); // fallback
            log.fine("OfflineResultResponseHandler() preHandleResponse() status=" + status);
            if (NbStatus.isSuccessful(status)) {
                //レスポンス復元処理
                try {
                    mJson = response.body().string();
                } catch (Exception e) { // IOException, ParseException
                    log.severe("ResultResponseHandler ERR " + e.getMessage());
                    e.printStackTrace();
                    status = NbStatus.UNPROCESSABLE_ENTITY_ERROR;
                }

//              log.fine("preHandleResponse() get mJson=" + mJson);
            }
            return status;

        }

        @Override
        public void handleResponse(Response response, int status) {
            log.fine("OfflineResultResponseHandler() handleResponse() status=" + status);
            if (NbStatus.isSuccessful(status)) {
                onSuccess(response);
                log.fine("handleResponse() clear");
                notifyFinish();
            } else {
                onError(response, status);
            }
            mJson = null;   //通知が済んだら解放
        }

        /**
         * 成功時に呼び出されるハンドラ
         * @param result Response
         */
        protected void onSuccess(Response result) { }

        /**
         * 失敗時に呼び出されるハンドラ
         * @param result Response
         * @param statusCode ステータスコード
         */
        protected void onError(Response result, int statusCode) {
            notifyFinish();
        }
    }

    /**
     * JSON 受信機能つき OfflineResultResponseHandler。
     * <p>
     * JSON 文字列は自動的に mContainer にセットされる。
     */
    protected static class JsonOfflineResultResponseHandler extends OfflineResultResponseHandler {
        @Override
        protected void onSuccess(Response result) {
            //レスポンス復元処理
            String json = getJson();
            log.fine("JsonOfflineResultResponseHandler() onSuccess()");
            if (json != null) {
                getContainer().setJson(json);
                onJson();
            } else {
                getContainer().setStatusCode(NbStatus.UNPROCESSABLE_ENTITY_ERROR);
            }
        }

        /**
         * JSON 受信完了時に呼び出される。
         * JSON 文字列は mContainer にセットされている。
         */
        protected void onJson() { }
    }

    /**
     * pull処理を行う。
     * バケット単位 pull を実施する。
     * (オブジェクト単位 Pull は廃止)
     * @param bucketName バケット名
     * @param query Pull範囲を指定するクエリ
     * @param syncObjects PULLしたデータのリスト（Output）
     * @param isBucketSyncRequired バケット情報の取得が必要指定(true: バケット情報を取得する)
     * @return pull結果のJson
     */
    protected String pull(final String bucketName, NbQuery query,
                          Set<String> syncObjects, boolean isBucketSyncRequired) {
        OfflineResultResponseHandler handler;
        NbOfflineResult container;

        // バケット情報の取得指示があった場合のみ実行
        if(isBucketSyncRequired) {
            // Bucket 情報を取得
            handler = pullBucketAsync(bucketName);
            if (handler == null) {
                return null;
            }
            handler.await(BUCKET_PULL_TIMEOUT);
            container = handler.getOfflineResultContainer();
            if (NbStatus.isNotSuccessful(container.getStatusCode())) {
                log.severe("pull() ERR getStatusCode(bucket) ={0}", container.getStatusCode());
                notifySyncError(bucketName, NbObjectSyncEventListener.SyncError.PULL_ERROR, null);
                throw new IllegalStateException();
            }
        }

        // Pull 処理
        handler = pullAsyncList(bucketName, query);
        if (handler == null) {
            return null;
        }
        handler.await(BUCKET_PULL_TIMEOUT);
        container = handler.getOfflineResultContainer();
        if (NbStatus.isNotSuccessful(container.getStatusCode())) {
            log.severe("pull() ERR getStatusCode(object) ="
                    + container.getStatusCode());
            updateLocalData(container.getStatusCode(), null/*objectId*/, bucketName, syncObjects);
            notifySyncError(bucketName, NbObjectSyncEventListener.SyncError.PULL_ERROR, null);
            throw new IllegalStateException();
        }

        return container.getJson();
    }

    /**
     * Pull動作（非同期)を行う。
     *
     * @param bucketName バケット名
     * @param query Pull範囲を指定するクエリ
     * @return OfflineResultResponseHandler ハンドラ
     */
    protected OfflineResultResponseHandler pullAsyncList(final String bucketName, NbQuery query) {
        JsonOfflineResultResponseHandler handler = new JsonOfflineResultResponseHandler();

        //QueryをMapパラメータに変換
        Map<String, String> queryParam;
        queryParam = convertQuery2RequestParam(query);

        //バケットに存在するオブジェクトを取得
        Request requestObject = mHttpRequestFactory
                .get(NbConsts.OBJECTS_PATH).addPathComponent(bucketName)
                .params(queryParam).build();

        execPullGetObjectsInBucket(requestObject, handler);

        return handler;
    }

    /**
     * バケットの Pull (非同期) を行う
     * @param bucketName バケット名
     * @return OfflineResultResponseHandler
     */
    protected OfflineResultResponseHandler pullBucketAsync(final String bucketName) {
        Request request = mHttpRequestFactory.get(NbConsts.OBJECT_BUCKET_PATH).addPathComponent(bucketName).build();

        //バケット情報取得保存処理
        JsonOfflineResultResponseHandler handler = new JsonOfflineResultResponseHandler() {
            @Override
            protected void onJson() {
                NbJSONObject json = NbJSONParser.parse(getContainer().getJson());
                json.put(NbKey.BUCKET_MODE, NbBucketMode.REPLICA.idString());
                mOfflineService.objectService().saveBucketCache(bucketName, json.toJSONString());
            }
        };
        execPullGetBucket(request, handler, bucketName);
        return handler;
    }

    /**
     * Push後のローカルキャッシュ状態更新処理を行う。
     * <p>サーバ側で物理削除されていた場合、オブジェクト衝突が起こった場合の処理。
     *
     * @param status Push 結果のステータスコード
     * @param objectId オブジェクトID
     * @param bucketName バケット名
     * @param syncObjects 変更された ObjectID を格納するための set。
     */
    private void updateLocalData(int status, String objectId, String bucketName,
            Set<String> syncObjects) {

        //push時にサーバ側データが既に物理削除されていた
        if (status == NbStatus.NOT_FOUND) {
            log.fine("updateLocalData() status == NbUtil.NOT_FOUND");
            if (objectId != null) {
                updateNotFoundClientData(objectId, bucketName, syncObjects);
            }
        } else if (status == NbStatus.CONFLICT) {
            log.fine("updateLocalData() status == NbUtil.CONFLICT");
            //オブジェクト単位同期のpushでID重複が発生した場合は、
            //ローカルDBのIDを採番し直して、次回同期で同期させる。
            if (objectId != null) {
                updateConflictClientData(objectId, bucketName);
            }
        }
    }

    private void updateConflictClientData(String objectId, String bucketName) {
        NbObjectEntity client = null;
        try {
            client = mDatabaseManager.readObject(objectId, bucketName);
            if (client != null) {
                NbJSONObject clientData = client.getJsonObject();
                client.setObjectId(NbOfflineUtil.makeObjectId());
                clientData.put(NbKey.ID, client.getObjectId());
                client.setJsonObjectAsImmutable(clientData);
                client.setState(NbSyncState.DIRTY);
                mDatabaseManager.createObject(client.getObjectId(), bucketName, client);
                //過去のデータは削除
                mDatabaseManager.deleteObject(objectId, bucketName);

                NbOfflineObject clientObj =
                        new NbOfflineObject(bucketName);
                clientObj.setCurrentParam(clientData);

                notifySyncError(bucketName, NbObjectSyncEventListener.SyncError.ID_CONFLICTED, clientObj);
            }
        } catch (NbDatabaseException e) {
            log.severe("updateConflictClientData() database readObject()"
                    + " NbDatabaseException e=" + e);
        }
    }

    private void updateNotFoundClientData(String objectId, String bucketName, Set<String> syncObjects) {
        NbObjectEntity clientData = null;
        try {
            clientData = mDatabaseManager.readObject(objectId, bucketName);
            if (clientData != null) {
                //クライアント側も論理削除状態であれば物理的に削除して整合を取る。
                log.fine("updateNotFoundClientData()"
                        + " info.getState()=" + clientData.getState());
                if ( !clientData.getState().isDeleted()) {
                    log.fine("updateNotFoundClientData() called db.deleteObject()");
                    mDatabaseManager.deleteObject(objectId, bucketName);
                    syncObjects.add(objectId);
                } else {
                    //次回同期時に新規データとして扱う。
                    clientData.setETag(null);
                    clientData.setTimestamp(null);
                    NbJSONObject jsonMap = clientData.getJsonObject();
                    //オフラインで新規作成したデータと同じくサーバ側データを削除
                    jsonMap.remove(NbKey.ETAG);
                    jsonMap.remove(NbKey.UPDATED_AT);
                    jsonMap.remove(NbKey.CREATED_AT);
                    clientData.setJsonObjectAsImmutable(jsonMap);

                    clientData.setState(NbSyncState.DIRTY);
                    log.fine("updateNotFoundClientData()"
                            + " called db.updateObject()");
                    mDatabaseManager.updateObject(bucketName, clientData);
                }
            }
        } catch (NbDatabaseException e) {
            log.severe("updateNotFoundClientData() database access error"
                    + " NbDatabaseException e=" + e);
        }
    }

    protected void execPullGetBucket(Request request,
                                     NbRestResponseHandler handler, String bucketName) {
        executeRequest(request, handler);
    }

    protected void execPullGetObject(Request request,
            NbRestResponseHandler handler, String bucketName, String objectId) {
        executeRequest(request, handler);
    }

    protected void execPullGetObjectsInBucket(Request request, NbRestResponseHandler handler) {
        executeRequest(request, handler);
    }

    /**
     * Query型→リクエストMAP型変換
     * @param query Pull範囲を指定するクエリ
     * @return 変換結果
     */
    private Map<String, String> convertQuery2RequestParam(NbQuery query) {
        if (query == null) {
            return null;
        }
        log.fine("convertQuery2RequestParam()");

        Map<String, String> param = new HashMap<>();
        if (query.getClause() != null && query.getClause().getConditions() != null) {
            param.put(NbKey.WHERE, query.getClause().getConditions().toJSONString());
        }
        log.fine("convertQuery2RequestParam()"
                + " query.getSortOrder()=" + query.getSortOrder());
        if ( (query.getSortOrder() != null) && (!query.getSortOrder().isEmpty()) ) {
            LinkedHashMap<String, Boolean> orders = query.getSortOrder();
            StringBuilder order = new StringBuilder();
            int count = 0;
            for (Map.Entry<String,Boolean> entry : orders.entrySet()) {
                if (count > 0)  order.append(",");
                if (!entry.getValue()) {
                    order.append("-");
                }
                order.append(entry.getKey());
                count++;
            }
            param.put(NbKey.ORDER, order.toString());
        }
        log.fine("convertQuery2RequestParam()"
                + " query.getSkipCount()=" + query.getSkipCount());
        param.put(NbKey.SKIP, String.valueOf(query.getSkipCount()));

        log.fine("convertQuery2RequestParam()"
                + " query.getLimit()=" + query.getLimit());
        param.put(NbKey.LIMIT, String.valueOf(query.getLimit()));

        log.fine("convertQuery2RequestParam()"
                + " query.getCountQueryAsNum()=" + query.getCountQueryAsNum());
        param.put(NbKey.COUNT, String.valueOf(query.getCountQueryAsNum()));

        log.fine("convertQuery2RequestParam()"
                + " query.isDeleteMark()=" + query.isDeleteMark());
        if (query.isDeleteMark()) {
            param.put(NbKey.DELETE_MARK, "1");
        }
        return param ;
    }

    /**
     * push処理を行う。
     * <p>
     * バケット名のみ渡された場合バケットのpush。
     * オブジェクトID、バケット名が渡された場合オブジェクトのpush。
     * バケット名がnullの場合は処理しない。
     * @param objectId オブジェクトID。null の場合はバケットのPush。
     * @param bucketName バケット名
     * @param serverObject サーバ側データ(オブジェクトPush)
     * @param serverObjectIds サーバ側オブジェクトID一覧(バケットPush)
     * @return push結果のJson
     */
    protected NbOfflineResult push(String objectId, final String bucketName,
                                        String serverObject, Set<String> serverObjectIds) {

        OfflineResultResponseHandler handler;
        NbOfflineResult container;
        try {
            handler = pushAsync(objectId, bucketName, serverObject, serverObjectIds);
            //push不要だった場合
            if (handler == null) {
                container = new NbOfflineResult();
                container.setStatusCode(NbStatus.OK);
                return container;
            }
        } catch (IllegalArgumentException e) {
            //push発行に失敗した場合
            container = new NbOfflineResult();
            container.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);
            return container;
        }

        if (!handler.await(BUCKET_PUSH_TIMEOUT)) {
            log.fine("push() <end> ERR Timeout/InterruptedException");
            return null;
        }

        return handler.getOfflineResultContainer();
    }

    /**
     * Push処理(非同期)
     * @param objectId オブジェクトID。null の場合はバケットPush。
     * @param bucketName バケット名
     * @param serverObject サーバ側データ(オブジェクトPush)
     * @param serverObjectIds サーバ側オブジェクトID一覧(バケットPush)
     * @return OfflineResultResponseHandler
     */
    protected OfflineResultResponseHandler pushAsync(String objectId, final String bucketName,
                                        String serverObject, Set<String> serverObjectIds) {

        JsonOfflineResultResponseHandler handler = new JsonOfflineResultResponseHandler() {
            @Override
            protected void onSuccess(Response result) {
                //再送対象データから削除
//                mBatchRetransmitPendingMap.remove(bucketName);
                super.onSuccess(result);
            }
            @Override
            protected void onError(Response result, int statusCode) {
                //サーバからのエラーコードを判定して、再送必要かチェック
                log.fine("pushAsync() onError() statusCode={0} sync state={1}"
                        , statusCode, getLogSyncState());
//                if (syncStates.isAutoSyncing() && NbUtil.isScopeSyncRetryNeeded(statusCode)) {
//                    //再送用のタイマーを起動
//                    //(pushUpdateで通知すると再送中に再送開始してしまうのでここで通知)
//                    startRetrySync();
//                } else {
//                    log.fine("pushAsync() handleResponse()"
//                            + " failure mBatchRetransmitPendingMap.remove(" + bucketName + ")");
//                    //再送対象データから削除
//                    mBatchRetransmitPendingMap.remove(bucketName);
//                }
                super.onError(result, statusCode);
            }
        };

        NbOfflineResult pushContainer = handler.getOfflineResultContainer();

        Request request;

        if (objectId != null) {
            NbObjectEntity info = null;
            try {
                info = mDatabaseManager.readObject(objectId, bucketName);
            } catch (NbDatabaseException e) {
                log.severe("pushAsync()"
                        + " readObject Err NbDatabaseException e=" + e);
            }
            //クライアントデータが存在しない（pushできないのでリターン終了）
            if (info == null) {
                log.fine("pushAsync() <end> not exist data");
                return null;
            }
            request = makeRequestForSyncPush(objectId, bucketName, info, serverObject, pushContainer);
            //リクエスト生成できなかった or 作成不要だった
            if (request == null) {
                log.fine("pushAsync() <end> not create request."
                        + " status=" + pushContainer.getStatusCode());
                return null;
            }
            //モック用JSON
            NbJSONObject mockJson = info.getJsonObject();

            execPush(request, handler, bucketName, objectId, mockJson);

        } else {

            // 今回対象ObjectIDだけ読み出し
            List<NbObjectEntity> dirtyList = readDirtyObjects(bucketName);

            if (dirtyList.isEmpty()) {
                log.fine("pushAsync() <end>"
                        + " bucketName=" + bucketName + " objectId=" + objectId);
                //dirtyなデータが無いのでpush不要
                return null;
            }

            // バッチリクエストに格納する Batch List
            NbJSONArray<NbJSONObject> batchList = new NbJSONArray<>();
            for (NbObjectEntity info : dirtyList) {
                NbSyncState state = info.getState();

                //Pullしたデータに含まれるConflictedなデータは、ユーザ側へ衝突通知済みなため
                //Push対象から外す。（そのままPushすると衝突エラーとなるアプリへエラー通知が行われる）
                //
                //PullしたデータにConflictedなデータが含まれない場合は、サーバ側で物理削除されているか、
                //Pull条件に当てはまっていない（サーバで更新が無い）可能性がある。
                //
                //どちらか判断できないため、Pullに含まれないデータは衝突検知を行う意味でもPush処理を継続する。
                if (state.isConflicted()) {
                    boolean isExist = false;
                    if (serverObjectIds != null) {
                        isExist = checkPullDataForPush(serverObjectIds, info);
                    }
                    if (isExist) {
                        log.fine("pushAsync() skip objectId=" + info.getObjectId());
                        continue;  //ユーザへ衝突通知済みのためPush対象から外す.
                    }
                }

                if (state.isDirtyNotFull()) {    //更新
                    updateDirtyClientBatchList(batchList, info);
                } else if (state.isDirtyFull()) {    //全上書き更新
                    updateDirtyFullClientBatchList(batchList, info);
                } else if (state.isDeleted()) {  //削除
                    updateDeleteClientBatchList(batchList, info);
                }
            }

            if (batchList.isEmpty()) {
                log.fine("pushAsync() <end> batchList.isEmpty()."
                        + " bucketName=" + bucketName + " objectId=" + objectId);
                return null;
            }

            NbObjectBucketImpl bucket = (NbObjectBucketImpl)NbServiceImpl.getInstance().
                    objectBucketManager().getBucket(bucketName,NbBucketMode.REPLICA);
            bucket.executeBatchOperation(batchList, null, handler);
        }

        return handler;
    }

    /**
     * ダーティなデータを取得する。
     * @param bucketName バケット名
     * @return ダーティデータのリスト
     */
    protected List<NbObjectEntity> readDirtyObjects(String bucketName) {

        // 分割同期初回時はPush対象リストを取得する
        if (mPushDirtyObjectIds == null) {
            mPushDirtyObjectIds = mDatabaseManager.readDirtyObjectIds(bucketName);
        }

        NbWhere conditions = null;

        if (mPushDirtyObjectIds.length != 0) {
            // Query用条件作成とPush対象リストから今回Push分オブジェクトIDを削除
            List<String> list = new ArrayList<>(Arrays.asList(mPushDirtyObjectIds));

            Iterator<String> iterator = list.iterator();
            int readSize = (list.size() > PUSH_SPLIT_OBJECTS_NUM) ? PUSH_SPLIT_OBJECTS_NUM : list.size();

            conditions = new NbWhere();
            conditions.getWhere().append("objectId").append(" IN (");

            for (int i = 0; i < readSize; i++) {
                if (i > 0) conditions.getWhere().append(", ");
                conditions.getWhere().append("?");
                conditions.getWhereArgs().add(iterator.next());
                iterator.remove();
            }

            conditions.getWhere().append(")");
            mPushDirtyObjectIds = list.toArray(new String[list.size()]);
        }

        return mDatabaseManager.readDirtyObjects(bucketName, conditions);
    }

    /**
     * PULLしたデータリストの中にPUSH対象データが含まれるか判定する。
     * @param pulledDataList pullしたデータのリスト
     * @param info push対象のオブジェクトデータ
     * @return true:pullしたデータの中にpush対象データあり、false：対象データ無し
     */
    private boolean checkPullDataForPush(Set<String> pulledDataList, NbObjectEntity info) {
        boolean isExist = false;
        for (String updateObjectId : pulledDataList) {

            if (updateObjectId.equals(info.getObjectId())) {
                isExist = true;
                break;
            }
        }
        return isExist;
    }

    private void updateDeleteClientBatchList(NbJSONArray<NbJSONObject> batchList, NbObjectEntity info) {
        //オブジェクト削除
        NbJSONObject delete = new NbJSONObject();
        delete.put(NbKey.OP, NbConsts.DELETE_OP);
        delete.put(NbKey.ID, info.getObjectId());
        delete.put(NbKey.ETAG, info.getETag());
        batchList.add(delete);
    }

    /**
     * バッチ処理リスト(JSON)にオブジェクト上書き操作(Full Update)を追加する
     * @param batchList バッチ処理リスト
     * @param info 上書き対象のオブジェクト
     */
    private void updateDirtyFullClientBatchList(NbJSONArray<NbJSONObject> batchList, NbObjectEntity info) {
        //オブジェクト完全上書き
        NbJSONObject fullUpdate = new NbJSONObject();
        NbJSONObject update = new NbJSONObject();
        NbJSONObject data = info.getJsonObject();

        update.put(NbKey.OP, NbConsts.UPDATE_OP);
        update.put(NbKey.ID, info.getObjectId());
        update.put(NbKey.ETAG, info.getETag());

        data.remove(NbKey.ID);
        data.remove(NbKey.ETAG);

        fullUpdate.put("$full_update", data);
        update.put(NbKey.DATA, fullUpdate);
        batchList.add(update);
    }

    /**
     * バッチ処理リスト(JSON)にオブジェクト更新操作を追加する。
     * ETag がなければ追加、ETag があれば更新。
     * @param batchList バッチ処理リスト
     * @param info 更新対象のオブジェクト
     */
    private void updateDirtyClientBatchList(NbJSONArray<NbJSONObject> batchList, NbObjectEntity info) {
        NbJSONObject data = info.getJsonObject();
        if (info.getETag() == null) {
            //オブジェクト追加
            NbJSONObject insert = new NbJSONObject();

            insert.put(NbKey.OP, NbConsts.INSERT_OP);
            insert.put(NbKey.ID, info.getObjectId());

            //insert時にETagは存在しないためETagフィールドはputしない。
            data.remove(NbKey.ETAG);

            insert.put(NbKey.DATA, data);
            batchList.add(insert);
        } else {
            //オブジェクト更新
            NbJSONObject update = new NbJSONObject();

            update.put(NbKey.OP, NbConsts.UPDATE_OP);
            update.put(NbKey.ID, info.getObjectId());
            update.put(NbKey.ETAG, info.getETag());

            data.remove(NbKey.ID);
            data.remove(NbKey.ETAG);
            //部分更新の場合、権限チェックに掛からないように不要なACLは送らない
            data.remove(NbKey.ACL);
            data.remove(NbKey.CONTENT_ACL);

            update.put(NbKey.DATA, data);
            batchList.add(update);
        }
    }

    protected void execPush(Request request, NbRestResponseHandler handler,
            String bucketName, String objectId, NbJSONObject mockJson) {
        executeRequest(request, handler);
    }

    /**
     * Push用の HTTP リクエストを作成する。
     * クライアントデータの status, ETag に応じて動作が変わる。
     * @param objectId オブジェクトID
     * @param bucketName バケット名
     * @param info クライアントデータ
     * @param serverData サーバデータ
     * @param pushContainer
     * @return Request
     */
    private Request makeRequestForSyncPush(String objectId, String bucketName,
            NbObjectEntity info, String serverData, final NbOfflineResult pushContainer) {
        log.fine("makeRequestForSyncPush() <start>"
                + " objectId=" + objectId + " bucketName=" + bucketName);

        //pull時点で削除した場合はinfoがnullになるのでここでリターン
        if (info == null) {
            log.severe("makeRequestForSyncPush() (info == null) return null");
            return null;
        }

        String objUrl = NbConsts.OBJECTS_PATH + "/" + bucketName;
        NbSyncState state = info.getState();

        log.fine("makeRequestForSyncPush() state=" + state);
        log.fine("makeRequestForSyncPush() objUrl=" + objUrl);

        //共通エラーに設定。最後まで流れたらSUCCESSに変更
        pushContainer.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);

        Request request = null;
        //サーバと同期済み？
        if (info.getETag() != null) {
            request = getRequestOnServerSynced(objectId, info, serverData, pushContainer, objUrl, state);
        } else {
            //サーバと未同期（オフラインのみで作成したデータ）
            log.fine("makeRequestForSyncPush() ETag == null");
            request = getRequestOnServerNoSynced(objectId, bucketName, info, objUrl, state);
        }

        if (request == null) {
            return null;
        }
        //正常
        pushContainer.setStatusCode(NbStatus.OK);

        log.fine("makeRequestForSyncPush() <end>");
        return request;
    }

    /**
     * Push用HTTPリクエスト作成。まだサーバと SYNC していない(ローカルのみ)データ用。
     * @param objectId オブジェクトID
     * @param bucketName バケット名
     * @param info オブジェクトデータ
     * @param objUrl 接続先URL
     * @param state オブジェクトデータの状態
     * @return Request。リクエスト不要な場合は null。
     */
    private Request getRequestOnServerNoSynced(String objectId, String bucketName,
            NbObjectEntity info, String objUrl, NbSyncState state) {
        Request request = null;
        int ret;
        //削除済みデータ
        if (state.isDeleted()) {
            //同期不要のため、サーバ側には同期せず、ローカル内から物理削除する。
            int deleteCnt = 0;
            try {
                deleteCnt = mDatabaseManager.deleteObject(objectId, bucketName);
            } catch (NbDatabaseException e) {
                log.severe("getRequestOnServerNoSynced()"
                        + " database deleteObject() NbDatabaseException e=" + e);
            }
            if (deleteCnt <= 0) {
                //削除失敗
                log.severe("getRequestOnServerNoSynced()"
                        + " database deleteObject() faile deleteCnt=" + deleteCnt);
                return null;
            }
            //リクエストは生成しない（ステータスは正常で返却）
        } else {
            //新規データ
            request = mHttpRequestFactory.post(objUrl).body(info.getJsonString()).build();
            log.fine("getRequestOnServerNoSynced() create request=" + request);
        }
        return request;
    }

    /**
     * Push用HTTPリクエスト作成。サーバと同期したことがあるデータ用。
     * @param objectId オブジェクトID
     * @param info オブジェクトデータ
     * @param serverData サーバ側メタデータ
     * @param pushContainer PUSH結果格納用コンテナ
     * @param objUrl 接続先URL
     * @param state オブジェクトデータの状態
     * @return Request
     */
    private Request getRequestOnServerSynced(String objectId, NbObjectEntity info,
            String serverData, final NbOfflineResult pushContainer, String objUrl, NbSyncState state) {
        Request request = null;
        int ret;
        log.fine("getRequestOnServerSynced() objectId=" + objectId + " state=" + state);

        String body = null;  //リクエスト用ボディ
        //削除済み or 衝突した削除データをリクエストする場合はDELETE文を用意
        if (state.isDeleted()) {
            Map<String, String> param = new HashMap<>();
            param.put(NbKey.DELETE_MARK, "1");
            request = mHttpRequestFactory.delete(objUrl).addPathComponent(objectId).params(param).build();
            log.fine("getRequestOnServerSynced() delete request=" + request);
        } else {
            //作成・更新データ
            NbJSONObject updateMap = info.getImmutableJsonObject();
//          log.fine("getRequestOnServerSynced() updateMap =" + updateMap);

            //コンフリクト解消時(クライアント優先)のpushの場合も対象
            if (state.isDirtyNotFull()) {
                //ACL変更無しの場合はupdateMapから対象データ削除
                removeMapOnAclNoChanged(info, serverData, updateMap);
                body = updateMap.toJSONString();
            } else if (state.isDirtyFull()) {
                //オフラインで完全上書きされたので、requestに$full_updateを付ける。
                NbJSONObject fullUpdateMap = new NbJSONObject();
                fullUpdateMap.put("$full_update", updateMap);
                body = fullUpdateMap.toJSONString();
            } else {
                //同期対象ではないが同期指示された
                log.fine("getRequestOnServerSynced() <end>"
                       + " return null. reason state=" + state);

                //リクエストは生成しないが、REST API発行せずに同期自体は正常終了させる。
                pushContainer.setStatusCode(NbStatus.OK);
                return null;
            }
            log.fine("getRequestOnServerSynced() body=" + body);
            //リクエスト生成
            request = mHttpRequestFactory
                    .put(objUrl).addPathComponent(objectId)
                    .body(body)
                    .build();
            log.fine("getRequestOnServerSynced() dirty request=" + request);
        }
        return request;
    }

    private void removeMapOnAclNoChanged(NbObjectEntity info, String serverData, NbJSONObject updateMap) {
        //サーバデータがPULLエラーの場合は、判定できないので「ACL変更あり」として扱う。
        if (serverData != null) {
            NbJSONObject data = NbJSONParser.parse(serverData);
            String serverPermission = data.getJSONObject(NbKey.ACL).toJSONString();
            String clientPermission = info.getAclString();

            //差分無し（ACL変更無し）ならPUTコマンドからACLキーを削除
            //（サーバ側で「ACL変更あり」と判断されるため）
            if ( NbOfflineUtil.isEqualAclPermission(clientPermission, serverPermission) ) {
                updateMap.remove(NbKey.ACL);
                updateMap.remove(NbKey.CONTENT_ACL);
            }
        }
    }

    /**
     * Pull時のデータマージを行う。
     * dataList で指定された全データについて pullUpdate を実行。
     * @param bucketName バケット名
     * @param mergeData データリスト(JSON文字列)
     * @param syncObjects 同期オブジェクトIDのセット (out)
     * @return 衝突データが1件でもあれば true、なければ false。
     */
    private boolean pullUpdateList(String bucketName, NbJSONObject mergeData, Set<String> syncObjects) {
        boolean hasConflictData = false;

        NbJSONArray<NbJSONObject> dataList = mergeData.getJSONArray(NbKey.RESULTS);

        //オブジェクト単体同期はpullUpdateSingle()がコールされるのでここは通らない。バケット単位の同期のみ。
        mServerCurrentTime = mergeData.getString(NbKey.CURRENT_TIME);

        try {
            mOfflineService.beginTransaction();

            for (NbJSONObject json : dataList) {
                json.setImmutable();
                String updateObjectId = json.getString(NbKey.ID);

                if (updateObjectId != null) {
                    if (pullUpdateSingle(updateObjectId, bucketName,
                            json, syncObjects)) {
                        hasConflictData = true;
                    }
                }
            }
        } finally {
            mOfflineService.endTransaction();
        }

        return hasConflictData;
    }

    // オブジェクトの更新処理 (1件)
    private boolean pullUpdateSingle(String objectId, String bucketName,
                                     NbJSONObject data, Set<String> syncObjects) {

        //クライアントのデータ取得
        NbObjectEntity clientData;
        try {
            clientData = mDatabaseManager.readObject(objectId, bucketName);
        } catch (NbDatabaseException e) {
            notifySyncError(bucketName, NbObjectSyncEventListener.SyncError.PULL_ERROR, null);
            throw new IllegalStateException("database read error id=" + objectId);
        }

        //サーバのデータ復元
        NbObjectEntity serverData = new NbObjectEntity();
        serverData.setETag(data.getString(NbKey.ETAG));
        serverData.setJsonObject(data);
        serverData.setObjectId(data.getString(NbKey.ID));
        serverData.setAclJson(data.getJSONObject(NbKey.ACL));
        serverData.setTimestamp(data.getString(NbKey.UPDATED_AT));
        Boolean isDeleted = data.getBoolean(NbKey.DELETED);

        //指定が無い場合はfalseと等価
        isDeleted = (isDeleted == null) ? false : isDeleted;
        if (isDeleted) {
            serverData.setState(NbSyncState.SYNCING_DELETE);
        } else {
            serverData.setState(NbSyncState.SYNCING);
        }

        boolean isConflict = false;
        if (clientData == null) {
            //クライアントに存在しないデータがサーバで削除された場合は同期不要
            addServerData(bucketName, syncObjects, objectId, serverData, isDeleted);
        } else {
            isConflict = checkConflict(bucketName, clientData, serverData, syncObjects);
            //オブジェクトID重複で更新されている可能性があるため設定
            //objectIdContainer.setObjectId(clientData.getObjectId());
        }

        return isConflict;
    }

    private void addServerData(String bucketName, Set<String> syncObjects, String objectId,
            NbObjectEntity serverData, Boolean isDeleted) {
        if (isDeleted) {
            // do nothing
        } else {
            try {
                mDatabaseManager.createObject(serverData.getObjectId(), bucketName, serverData);
                syncObjects.add(serverData.getObjectId());
            } catch (NbDatabaseException e) {
                notifySyncError(bucketName, NbObjectSyncEventListener.SyncError.PULL_ERROR, null);
                throw new IllegalStateException("database create error id="
                + serverData.getObjectId());
            }
        }
    }

    /**
     * push処理後のデータベース更新を行う
     * @param pushResult PUSH処理結果コンテナ
     * @param objectId オブジェクトID
     * @param bucketName バケット名
     * @param syncObjects 同期対象データリスト（Output）
     */
    protected int pushUpdate(NbOfflineResult pushResult, String objectId,
            String bucketName, Set<String> syncObjects) {
        log.fine("pushUpdate() <start> objectId="
                + objectId + " bucketName=" + bucketName);
        log.fine("pushUpdate() pushResult.getStatusCode()="
                + pushResult.getStatusCode());

        int result;
        if (NbStatus.isSuccessful(pushResult.getStatusCode())) {
            result = pushUpdateSuccess(pushResult, objectId, bucketName, syncObjects);
        } else {
            result = pushUpdateError(pushResult, objectId, bucketName, syncObjects);
        }

        log.fine("pushUpdate() <end>");
        return result;
    }

    private int pushUpdateSuccess(NbOfflineResult pushResult, String objectId,
            String bucketName, Set<String> syncObjects) {

        //前段処理でpush不要と判断された場合、及び物理削除後はJSONデータが返却されないため、
        //ここでは何もせずにリターン（正常終了）
        log.fine("pushUpdateSuccess() start");
        if ((pushResult.getJson() == null) || (pushResult.getJson().isEmpty())) {
            log.fine("pushUpdateSuccess() objectId=" + objectId);
            if (objectId != null) {
                syncObjects.add(objectId);
            }
            return NbStatus.OK;
        }

        //push結果を判定
        NbJSONObject results = NbJSONParser.parse(pushResult.getJson());
        if (objectId != null) {
            return pushUpdateSuccessForObject(results, objectId, bucketName, syncObjects);
        } else {
            int ret = NbStatus.OK;
            try {
                log.info("pushUpdateSuccess() beginTransaction");
                mOfflineService.beginTransaction();
                ret = pushUpdateSuccessForBucket(results, bucketName, syncObjects);
            } finally {
                mOfflineService.endTransaction();
                log.info("pushUpdateSuccess() endTransaction");
            }
            return ret;
        }
    }

    private int pushUpdateSuccessForObject(NbJSONObject results, String objectId,
                                           String bucketName, Set<String> syncObjects) {
        //オブジェクト指定pushの場合
        NbObjectEntity info = null;
        try {
            info = mDatabaseManager.readObject(objectId, bucketName);
        } catch (NbDatabaseException e) {
            log.fine("pushUpdateSuccessForObject() "
                    + " database readObject() exception=" + e);
        }

        if (info != null) {
            NbSyncState state = info.getState();

            log.fine("pushUpdateSuccessForObject() read object state=" + state);
            if (NbSyncState.isDirty(state)) {
                log.fine("pushUpdateSuccessForObject() object dirty");
                NbObjectEntity save = new NbObjectEntity();
                save.setETag(results.getString(NbKey.ETAG));
                save.setJsonObject(results);
                save.setObjectId(results.getString(NbKey.ID));
                save.setAclString(results.getJSONObject(NbKey.ACL).toJSONString());
                save.setState(NbSyncState.SYNC);
                save.setTimestamp(results.getString(NbKey.UPDATED_AT));
                try {
                    mDatabaseManager.updateObject(save.getObjectId(), bucketName, save);
                } catch (NbDatabaseException e) {
                    log.severe("pushUpdateSuccessForObject()"
                            + " database updateObject() NbDatabaseException e=" + e);
                }
                syncObjects.add(save.getObjectId());
            } else if (state.isDeleted()) {
                log.fine("pushUpdateSuccessForObject() object delete");
                //サーバ側でも削除扱いになったのでキャッシュ上物理的に削除
                try {
                    mDatabaseManager.deleteObject(objectId, bucketName);
                } catch (NbDatabaseException e) {
                    log.severe("pushUpdateSuccessForObject()"
                            + " database deleteObject() NbDatabaseException e=" + e);
                }
                syncObjects.add((String) results.get(NbKey.ID));
            }
        }

        log.fine("pushUpdateSuccessForObject() return success.");

        return NbStatus.OK;
    }

    /**
     * Push（バッチ送信)成功後の処理
     * @param results
     * @param bucketName
     * @param syncObjects
     * @return NbStatus。通常はOK。
     * 衝突が１個以上発生した場合はCONFLICT、その他エラー発生時はINTERNAL_SERVER_ERROR。
     */
    private int pushUpdateSuccessForBucket(NbJSONObject results, String bucketName,
            Set<String> syncObjects) {
        int localResult = NbStatus.OK;

        //バケット指定pushの場合
        NbJSONArray<NbJSONObject> resultList = results.getJSONArray(NbKey.RESULTS);
        //同期範囲対象キャッシュデータ使用フラグ
        boolean isFirst = true;
        for (NbJSONObject result : resultList) {
            String resultCode = (String) result.get(NbKey.RESULT);

            NbJSONObject data = result.getJSONObject(NbKey.DATA);
            log.fine("pushUpdateSuccessForBucket() objctId=" + result.get(NbKey.ID));
            if (data != null) {
                data.put(NbKey.ID, result.get(NbKey.ID));
                data.put(NbKey.ETAG, result.get(NbKey.ETAG));
                data.put(NbKey.UPDATED_AT, result.get(NbKey.UPDATED_AT));
            } else {
                //単独でエラーになった場合は_idフィールド以外設定されていないため、
                //エラー返却用のデータはローカルDBから取得する。
                NbObjectEntity info = null;
                try {
                    info = mDatabaseManager.readObject(
                            (String) result.get(NbKey.ID), bucketName);
                } catch (NbDatabaseException e) {
                    log.fine("pushUpdateSuccessForBucket() "
                            + " database readObject() exception=" + e);
                }
                if (info != null) {
                    data = info.getJsonObject();
                    data.put(NbKey.ID, info.getObjectId());
                    data.put(NbKey.ETAG, info.getETag());
                    data.put(NbKey.UPDATED_AT, info.getTimestamp());
                } else {
                    data = new NbJSONObject();
                    data.put(NbKey.ID, result.get(NbKey.ID));
                }
            }

            log.fine("pushUpdateSuccessForBucket()"
                    + " resultCode=" + resultCode);

            switch (resultCode) {
                case RESULT_OK:
                    pushUpdateSuccessForBucketOk(bucketName, syncObjects, result, data);
                    break;

                case RESULT_NOT_FOUND:
                    int res = pushUpdateSuccessForBucketNotFound(bucketName, syncObjects,
                            isFirst, result);
                    if (NbStatus.isSuccessful(res)) {
                        break;    //衝突解決が完了したらエラー通知しないのでbreak
                    }
                    //エラー通知するためfall through
                case RESULT_BAD_REQUEST:
                case RESULT_FORBIDDEN:
                case RESULT_SERVER_ERROR:
                default: // unknown
                    log.warning("pushUpdateSuccessForBucket() : error={0}", result);
                    NbOfflineObject otherError = new NbOfflineObject(bucketName);
                    otherError.setCurrentParam(data);
                    notifySyncError(bucketName, NbObjectSyncEventListener.SyncError.PUSH_ERROR, otherError);
                    localResult = NbStatus.INTERNAL_SERVER_ERROR;
                    break;

                case RESULT_CONFLICT:
                    log.fine("pushUpdateSuccessForBucket() RESULT_CONFLICT");
                    String targetId = (String) result.get(NbKey.ID);
                    if (isObjectInPushScope(bucketName, targetId, isFirst)) {
                        //クライアントデータ取得
                        NbObjectEntity clientData = null;
                        try {
                            clientData = mDatabaseManager.readObject(targetId, bucketName);
                        } catch (NbDatabaseException e) {
                            break;
                        }
                        //サーバデータ取得
                        NbObjectEntity serverData = new NbObjectEntity();
                        serverData.setETag(data.getString(NbKey.ETAG));
                        serverData.setJsonObject(data);
                        serverData.setObjectId(data.getString(NbKey.ID));
                        serverData.setAclString(data.getJSONObject(NbKey.ACL).toJSONString());
                        serverData.setState(NbSyncState.SYNC);
                        serverData.setTimestamp(data.getString(NbKey.UPDATED_AT));
                        //コンフリクト解消
                        boolean isConflict = checkConflict(bucketName, clientData, serverData, syncObjects);
                        if (isConflict && localResult != NbStatus.INTERNAL_SERVER_ERROR) {
                            // CONFLICTエラーセット。ただしINTERNAL_SERVER_ERROR は上書きしない。
                            localResult = NbStatus.CONFLICT;
                        }
                    } else {
                        log.fine("pushUpdateSuccessForBucket() out of sync scope.");
                        //同期範囲外データは要因に合わせてエラー通知のみ
                        NbOfflineObject idError = new NbOfflineObject(bucketName);
                        idError.setCurrentParam(data);

                        String reasonCode = (String) result.get(NbKey.REASON_CODE);
                        log.fine("pushUpdateSuccessForBucket()"
                                + " reasonCode=" + reasonCode);
                        //エラーコードに合わせてアプリ側へエラー通知
                        notifySyncErrorForReasonCode(bucketName, idError, reasonCode);
                        localResult = NbStatus.INTERNAL_SERVER_ERROR;
                    }
                    isFirst = false;
                    break;
            }
        }

        return localResult;
    }

    private void notifySyncErrorForReasonCode(String targetBucket, NbOfflineObject idError, String reasonCode) {
        switch (reasonCode) {
            case NbReason.ETAG_MISMATCH:
                //ETag比較エラー。（REQEST_CONFLICTEDとほぼ同等）
                //fall through
            case NbReason.UNSPECIFIED:
                //不明。発生することはまずない。
                //fall through
            case NbReason.DUPLICATE_KEY:
                //ユニーク制約エラー。
                //（ID重複以外のユニーク制約が掛かっているフィールドでデータ重複が発生）
                //fall through
            case NbReason.REQUEST_CONFLICTED:
                //更新・削除処理衝突。
                //（サーバ側で更新/削除処理中に裏でデータが更新された）

                //上記エラーについては、ローカルでは一旦未更新状態とし、
                //次回同期のタイミングで再同期する。ここではエラー通知のみ。
                //（状態もDIRTYのまま、syncObjectsも追加しない）
                //PUSH時エラーの通知
                notifySyncError(targetBucket, NbObjectSyncEventListener.SyncError.PUSH_ERROR, idError);
                break;
            case NbReason.DUPLICATE_ID:
                //ID重複
                notifySyncError(targetBucket, NbObjectSyncEventListener.SyncError.ID_CONFLICTED, idError);
                break;
            default:
                log.fine("pushUpdateSuccessForBucket() ERR"
                        + " invalid reason code =" + reasonCode);
                break;
        }
    }

    /**
     * Push: サーバ物理削除時の処理。更新-削除衝突の場合は衝突通知を実施。
     * @param bucketName
     * @param syncObjects
     * @param isFirst
     * @param result
     * @return NbStatus。通常は OK。オブジェクトが同期範囲外の場合のみ UNPROCESSABLE_ENTITY_ERROR。
     */
    private int pushUpdateSuccessForBucketNotFound(String bucketName, Set<String> syncObjects,
            boolean isFirst, NbJSONObject result) {
        NbObjectEntity localData = null;
        String serverId = result.getString(NbKey.ID);
        int statusCode = NbStatus.UNPROCESSABLE_ENTITY_ERROR;

        try {
            localData = mDatabaseManager.readObject(serverId, bucketName);
        } catch (NbDatabaseException e) {
            log.severe("pushUpdateSuccessForBucketNotFound() error readObject() serverId=" + serverId);
        }
        log.fine("pushUpdateSuccessForBucketNotFound() RESULT NOT FOUND.");
        if (localData == null) {
            return NbStatus.OK;
        }

        log.fine("pushUpdateSuccessForBucketNotFound() state=" + localData.getState());
            //クライアントが削除データ以外の場合は衝突が発生しているので解決する。
        if (isObjectInPushScope(bucketName, serverId, isFirst)) {
            //以下のようなケースでここのルートを通ることを想定
            //1)同期済みオブジェクトをオフラインモードでクライアントが編集。
            //2)その後、別端末からサーバ上のオブジェクトを物理的に削除。
            //3)その後、オンラインモードで自動同期を実行。（サーバ側へのpushはnotfoundになる）

            log.fine("pushUpdateSuccessForBucketNotFound()"
                    + " conflict check!! objectId=" + localData.getObjectId());

            //サーバデータが無いため必要項目のみ疑似的に作成
            NbObjectEntity serverData = new NbObjectEntity();
            serverData.setETag(null);
            serverData.setObjectId(localData.getObjectId());
            serverData.setAclString(null);
            serverData.setState(NbSyncState.SYNCING_DELETE);    //削除
            serverData.setTimestamp(null);

            //コンフリクト解消通知でJsonData内のオブジェクトIDを使用するため
            //ダミーのJSONデータを作成
            NbJSONObject dummyJson = new NbJSONObject();
            dummyJson.put(NbKey.ID, localData.getObjectId());
            serverData.setJsonObjectAsImmutable(dummyJson);

            //コンフリクト解消
            //衝突解決。解決ポリシーがマニュアルの場合はtrueが返るが、特に処理することも無いので結果は参照不要。
            checkConflict(bucketName, localData, serverData, syncObjects);
            //同期完了。呼び出し元でエラー通知させたくないのでSUCCESSで返す
            statusCode = NbStatus.OK;
        }

        return statusCode ;
    }

    private void pushUpdateSuccessForBucketOk(String bucketName, Set<String> syncObjects,
            NbJSONObject result, NbJSONObject data) {
        NbObjectEntity info;
        try {
            info = mDatabaseManager.readObject(
                    result.getString(NbKey.ID), bucketName);
        } catch (NbDatabaseException e) {
            log.fine("pushUpdateSuccessForBucketOk() "
                    + " database readObject() result ok exception=" + e);
            return;
        }
        //フェールセーフ
        //サーバ側から想定外のIDが返却された時、DBからデータが取得できないので
        //その場合は何もせずに処理継続させる。ローカルDBのデータはstateが変わらないので、
        //次回同期時に同期対象になる。
        if (info == null) {
            log.severe("pushUpdateSuccessForBucket()"
                    + " responsed unknown objectId=" + result.getString(NbKey.ID));
            return;
        }

        NbSyncState state = info.getState();
        //pushが成功したらSYNC状態に戻す
        if (NbSyncState.isDirty(state)) {
            NbObjectEntity save = new NbObjectEntity();
            save.setETag(data.getString(NbKey.ETAG));
            save.setJsonObject(data);
            save.setObjectId(data.getString(NbKey.ID));
            save.setAclJson(data.getJSONObject(NbKey.ACL));
            save.setState(NbSyncState.SYNC);
            save.setTimestamp(data.getString(NbKey.UPDATED_AT));

            try {
                mDatabaseManager.updateObject(bucketName, save);
            } catch (NbDatabaseException e) {
                log.fine("pushUpdateSuccessForBucketOk() "
                        + " database updateObject() result ok exception=" + e);
            }
            syncObjects.add(save.getObjectId());
        } else if (state.isDeleted()) {
            try {
                mDatabaseManager.deleteObject(data.getString(NbKey.ID), bucketName);
            } catch (NbDatabaseException e) {
                log.fine("pushUpdateSuccessForBucketOk() "
                        + " database deleteObject() result ok exception=" + e);
            }
            syncObjects.add((String) data.get(NbKey.ID));
        }
    }

    private int pushUpdateError(NbOfflineResult pushResult, String objectId, String bucketName,
            Set<String> syncObjects) {
        if (objectId != null) {
            NbObjectEntity info = null;
            try {
                info = mDatabaseManager.readObject(objectId, bucketName);
            } catch (NbDatabaseException e) {
                log.severe("pushUpdateError()"
                        + " database readObject() NbDatabaseException e=" + e);
            }
            if (info != null) {
                updateLocalData(pushResult.getStatusCode(), objectId, bucketName, syncObjects);
                NbOfflineObject errObj =
                        new NbOfflineObject(bucketName);
                errObj.setCurrentParam(info.getJsonObject());
                notifySyncError(bucketName, NbObjectSyncEventListener.SyncError.PUSH_ERROR, errObj);
                return NbStatus.INTERNAL_SERVER_ERROR;
            }
        } else {    //バケット単位の同期
            log.severe("pushUpdateError() sync state=" + getLogSyncState());

            //サーバからのエラーコードを判定して、再送必要かチェック
//            if ((syncStates.isAutoSyncing() || syncStates.isSyncRetrying())
//                    && NbUtil.isScopeSyncRetryNeeded(pushResult.getStatusCode())) {
//                //再送開始、もしくは再送中のエラーを示すエラーコードを返却
//                notifySyncError(bucketName, NbObjectSyncEventListener.SyncError.SYNC_RETRYING, null);
//            } else {
                if (mSyncErrorEvent == null) {
                    mSyncErrorEvent = new SyncErrorInfoContainer();
                    mSyncErrorEvent.setTargetBucket(bucketName);
                    mSyncErrorEvent.setErrorCode(NbObjectSyncEventListener.SyncError.PUSH_ERROR);
                    mSyncErrorEvent.setErrorObject(null);
                }
//            }
            return NbStatus.INTERNAL_SERVER_ERROR;
        }

        return NbStatus.OK;
    }

    /**
     * 同期開始を通知する
     * @param targetBucket バケット
     */
    private void notifySyncStart(final String targetBucket) {
        log.fine("notifySyncStart() targetBucket=" + targetBucket);
        NbServiceImpl.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Set<NbObjectSyncEventListener> listeners = getEventListeners(targetBucket);
                for (NbObjectSyncEventListener listener : listeners) {
                    listener.onSyncStart(targetBucket);
                }
            }
        });
    }

    /**
     * 同期完了を通知し、SYNCING 状態のデータをすべて SYNC に更新する
     *
     * @param targetBucket バケット
     * @param syncObjectIds 同期されたオブジェクトIDのセット (通知用)
     */
    private void notifySyncCompleted(final String targetBucket, Set<String> syncObjectIds) {
        //同期状態をSYNCING→SYNCに更新
        try {
            log.info("updateSyncingObjects beginTransaction");
            mOfflineService.beginTransaction();
            mDatabaseManager.updateSyncingObjects(targetBucket);
        } catch (NbDatabaseException e) {
            log.severe("notifySyncCompleted()"
                    + " updateSyncingObjects() NbDatabaseException e=" + e);
        } finally {
            mOfflineService.endTransaction();
            log.info("updateSyncingObjects endTransaction");
        }

        //Set→ArrayList変換
        final List<String> notifyList = new ArrayList<>(syncObjectIds);
        log.fine("notifySyncCompleted() notifyList=" + notifyList);

        NbServiceImpl.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Set<NbObjectSyncEventListener> listeners = getEventListeners(targetBucket);
                for (NbObjectSyncEventListener listener : listeners) {
                    listener.onSyncCompleted(targetBucket, notifyList);
                }
            }
        });
    }

    private void notifySyncError(final String targetBucket, final NbObjectSyncEventListener.SyncError errorCode, final NbObject errorObject) {
        log.fine("notifySyncError() targetBucket=" + targetBucket + "errorCode=" + errorCode
                + " errorObject=" + errorObject);
        NbServiceImpl.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Set<NbObjectSyncEventListener> listeners = getEventListeners(targetBucket);
                for (NbObjectSyncEventListener listener : listeners) {
                    listener.onSyncError(errorCode, errorObject);
                }
            }
        });
    }

    /**
     * アプリケーションへ衝突通知を行う。（listener指定あり）
     * メインスレッド上で衝突通知リスナーによる通知を行う。
     * @param resolver 衝突解決メソッド
     * @param listener 同期結果通知リスナ
     * @param targetBucket 同期対象バケット
     * @param client クライアントメタデータ
     * @param server サーバメタデータ
     */
    private void notifySyncConflicted(final NbObjectConflictResolver resolver, final NbObjectSyncEventListener listener,
            final String targetBucket, final NbObject client, final NbObject server)  {

        NbServiceImpl.runOnUiThread(new Runnable() {
            public void run() {
                log.fine("notifySyncConflicted() run(unit) <start>"
                        + " targetBucket=" + targetBucket);

                listener.onSyncConflicted(resolver, targetBucket, client, server);

                log.fine("notifySyncConflicted() run(unit) <end>");
            }
        });
    }

    /**
     * アプリケーションへ衝突通知を行う。（全通知）
     * メインスレッド上で衝突通知リスナーによる通知を行う。
     * @param resolver 衝突解決メソッド
     * @param targetBucket 同期対象バケット
     * @param client クライアントメタデータ
     * @param server サーバメタデータ
     */
    private void notifySyncConflicted(final NbObjectConflictResolver resolver, final String targetBucket,
            final NbObject client, final NbObject server)  {

        NbServiceImpl.runOnUiThread(new Runnable() {
            public void run() {
                log.fine("notifySyncConflicted() run(all) <start>"
                        + " targetBucket=" + targetBucket);
                Set<NbObjectSyncEventListener> listeners = getEventListeners(targetBucket);
                for (NbObjectSyncEventListener listener : listeners) {
                    listener.onSyncConflicted(resolver, targetBucket, client, server);
                }
                log.fine("notifySyncConflicted() run(all) <end>");
            }
        });
    }

    /**
     * バケット単位の同期を行う
     * @param bucketName バケット名
     * @param isAuto 呼び出し元が自動同期かどうか（状態管理用）
     * @return 同期結果
     */
    int syncBucket(@NonNull String bucketName, boolean isAuto) {
        int result = NbStatus.OK;
        //再送停止
//        stopRetrySync();       //他の同期が始まった時点で再送は止める。
//        if(isAuto) {
//            // 自動同期
//            syncStates.startScopeAutoSync();
//        } else {
        // 手動同期中に更新
        mSyncStates.startSync();
//        }
        //同期範囲を取得
        NbQuery query = null;
        try {
            query = mDatabaseManager.readSyncScope(bucketName);
        } catch (NbDatabaseException ex) {
            log.severe("syncBucket() readSyncScope ERR " + ex);
            result = NbStatus.INTERNAL_SERVER_ERROR;
        }

        // SyncScopeの取得に失敗した場合、同期は実行しない
        if (NbStatus.isSuccessful(result)) {
            // バケット同期では同期条件の設定が必須となる
            result = sync(bucketName, query);

            //DB上の各データ状態を参照し、コンフリクト状態のデータが無いかチェック
            if ((NbStatus.isSuccessful(result)) && !isDbConflicting(bucketName)) {
                updateDbLastTime(bucketName);         //同期時刻を更新
            }
        }

        //同期解除
        mSyncStates.stopSync();
        return result;
    }

    /**
     * 分割同期実行用のQueryを生成する
     * limit, sortOrder, skipCount、deleteMarkはアプリの設定を無効とし、上書きする
     * @param baseQuery ベースQuery
     * @return 編集済みQuery
     */
    protected NbQuery createDividePullBaseQuery(@NonNull NbQuery baseQuery) {

        NbQuery query = new NbQuery();

        // 件数取得は同期に不要なので取得しない(ユーザ設定は無視)
        query.setCountQuery(false);
        // 既存のdeleteMark上書き処理を移動(ユーザ設定は無視)
        query.setDeleteMark(true);

        // limitはユーザ設定は無視
        query.setLimit(PULL_SPLIT_OBJECTS_NUM);

        // sortOrderはユーザ設定を無視
        // 第一優先: オブジェクトの更新日時(昇順)、第二優先: ObjectId(昇順)
        query.setSortOrders(NbKey.UPDATED_AT, NbKey.ID);

        // skipCountはユーザ設定を無視する
        query.setSkipCount(0);

        // Clauseのコピー
        NbClause baseClause = baseQuery.getClause();
        NbClause clause;
        if (baseClause != null ) {
            // ベースの条件を引き継いだをClauseを生成
            NbJSONObject json = baseClause.toJSONObject();
            clause = NbClause.fromJSONObject(json);
        } else {
            // 空のClauseを生成
            clause = new NbClause();
        }
        query.setClause(clause);

        return query;
    }

    /**
     * 前回Pull時のサーバ時刻から、オフセットを引いた値をクエリに付与する<br>
     * DBに未保存の場合は何もしない
     *
     * @param bucketName バケット名
     * @param query 条件を付与するクエリ
     */
    protected void addPullServerTimeCondition(String bucketName, NbQuery query) {
        String time;
        // Pull最終時刻が保存されていれば取得する
        time = mDatabaseManager.getLatestPullServerTime(bucketName);

        // 時刻が保存されていればされていれば、オフセットを付けてClauseに時刻の条件を付与
        if (time != null) {
            final DateFormat df = new SimpleDateFormat(NbConsts.TIMESTAMP_FORMAT);
            // フォーマットに対する厳密な判定を行う
            df.setLenient(false);
            try {
                Date d = df.parse(time);
                d.setTime(d.getTime() - PULL_TIME_OFFSET);
                time = df.format(d);
                NbClause clause = query.getClause();
                clause.and(new NbClause().greaterThanOrEqual(NbKey.UPDATED_AT, time));
                log.fine("addPullServerTimeCondition() time: " + time + " + newClause: " + clause.toJSONObject());
            } catch (ParseException e) {
                //パース失敗した場合は、条件付与せず無視する
                //e.printStackTrace();
                log.warning("addPullServerTimeCondition() time: " + time);
            }
        }
    }

    /**
     * 範囲同期を行う。
     * 自動同期時に呼び出される。手動同期時にも呼び出される。
     * @param isAuto  呼び出し元が自動同期かどうか（状態管理用）
     * @return 同期結果
     */
    int syncScope(boolean isAuto) {
        log.fine("syncScope() <start>");
        //再送停止
//        stopRetrySync();       //他の同期が始まった時点で再送は止める。

        // 範囲同期中
        mSyncStates.startScopeSync(isAuto);

        Map<String, NbQuery> syncScope = null;
        try {
            syncScope = mDatabaseManager.readSyncScope();
        } catch (NbDatabaseException ex) {
            log.severe("syncScope() ERR " + ex);
            throw ex;
        }

        int finalResult = NbStatus.OK;
        //バケット単位でループ
        for (String bucketName : syncScope.keySet()) {
            //Query取得

            log.finer("syncScope() bucketName=" + bucketName);
            //バケットの同期範囲取得
            NbQuery pullQuery = syncScope.get(bucketName);
            log.finer("syncScope() pullQuery=" + pullQuery);
            //バケット単位の同期実行
            int result = sync(bucketName, pullQuery);
            log.finer("syncScope() sync() result=" + result);
            if (NbStatus.isSuccessful(result)) {
                //コンフリクト無しで正常に同期終了した場合は、当該バケットの同期時刻を更新
                if (!isDbConflicting(bucketName)) {
                    updateDbLastTime(bucketName);         //同期時刻を更新
                }
            } else {
                //エラーの場合は、最初のエラーを戻り値とする。
                if (NbStatus.isSuccessful(finalResult)) {
                    finalResult = result;
                }
            }
        }

        log.fine("syncScope() finalResult=" + finalResult);

        //自動同期終了（手動同期と並行して動作することはないので無条件でfalse）
        mSyncStates.stopSync();

        log.fine("syncScope() <end> finalResult=" + finalResult);
        return finalResult;
    }

    /**
     * 同期範囲（Query）に一致し、且つ変更ありデータのリストを返却する。<br>
     * @param bucketName：同期対象バケット名
     * @param query：同期範囲
     * @return 同期push対象データリスト(null返却しない)
     */
    private List<NbObjectEntity> readObjectsToPush(String bucketName, NbQuery query) {
        NbDatabaseManager.ObjectQueryResults queryResults;
        List<NbObjectEntity> targetList = new ArrayList<>();

        //TODO:limit指定がなければ limitに-1を指定して全件取得する
        if (query == null) {
            query = new NbQuery();
        }

        query.setDeleteMark(true);
        try {
            queryResults = mDatabaseManager.queryObjects(bucketName, query);
        } catch (NbDatabaseException ex) {
            log.severe("readObjectsToPush() <end> ERR"
                    + " queryObjects() NbDatabaseException ex=" + ex);
            return targetList;
        }

        log.fine("readObjectsToPush() results.size()="
                + queryResults.getResults().size());

        for (NbObjectEntity info: queryResults.getResults()) {
            log.fine("readObjectsToPush() state="
                    + info.getState() + " objectId=" + info.getObjectId());
            //DIRTYなデータであれば対象（CONFLICTEDなデータもPushでNotFoundが返された場合にここを通るため対象にする）
            if (info.getState().isDirty() || info.getState().isDeleted()) {
                targetList.add(info);
            }
        }
        return targetList;
    }

    /**
     * 指定されたオブジェクトが Push 対象オブジェクトかどうか調べる。
     * 具体的には、オブジェクトが同期範囲内、かつローカル変更されている(dirtyなど)状態
     * であるか調べる。
     *
     * <p>DBに対し、同期範囲内かつローカル変更されたオブジェクト一覧が検索される。
     * 高速化のため、一覧はキャッシュされる。
     *
     * <p>TODO: objectId が１つに確定しているので、キャッシュなどせず SELECT で一件検索すべきである。
     *
     * @param bucketName バケット名
     * @param objectId オブジェクトID
     * @param isFirst trueにするとキャッシュをクリアする。
     * @return 対象ならば true
     */
    /*package*/ boolean isObjectInPushScope(String bucketName, String objectId, boolean isFirst) {
        boolean isTarget = false;
        if (objectId == null) {
            log.fine("isObjectInPushScope() return false"
                    + " (objectId == null)");
            return isTarget;
        }

        log.fine("isObjectInPushScope() bucketName="
                + bucketName + " objectId=" + objectId + " isFirst=" + isFirst);

        List<NbObjectEntity> list;
        //性能面を考慮し、キャッシュしていない場合のみDBから取得
        if ( isFirst || (mObjectsCacheToPush == null) ) {
              //同期範囲を取得
              NbQuery query;
              try {
                  query = mDatabaseManager.readSyncScope(bucketName);
              } catch (NbDatabaseException ex) {
                  log.severe("isObjectInPushScope() ERR " + ex);
                  throw ex;
              }
              //同期対象データ取得
              mObjectsCacheToPush = readObjectsToPush(bucketName, query);
        }

        list = mObjectsCacheToPush;

        if ( (list == null) || (list.isEmpty())) {
            log.fine("isObjectInPushScope() return false"
                    + " (list == null) || (list.isEmpty())");
            return false;
        }

        for (NbObjectEntity od: list) {
            if (objectId.equals(od.getObjectId())) {
                isTarget = true;
                break;
            }
        }

        log.fine("isObjectInPushScope() return " + isTarget);
        return isTarget;
    }

    /**
     * 同期範囲を設定する
     * @param bucketName バケット名
     * @param scope 同期範囲(NbQuery)
     */
    void setSyncScope(@NonNull String bucketName, NbQuery scope) {
        log.fine("setSyncScope() bucketName=" + bucketName + "scope=" + scope);
        try {
            mDatabaseManager.saveSyncScope(bucketName, scope);
        } catch (NbDatabaseException ex) {
            log.severe("setSyncScope() ERR " + ex);
            throw ex;
        }
    }

    /**
     * 同期範囲を取得する
     * @param  bucketName バケット名
     * @return 同期範囲(NbQuery)
     */
    NbQuery getSyncScope(@NonNull String bucketName) {
        NbQuery data = null;
        try {
            data = mDatabaseManager.readSyncScope(bucketName);
        } catch (NbDatabaseException ex) {
            log.severe("getSyncScope() ERR " + ex);
            throw ex;
        }
        return data;
    }

    /**
     * 全同期範囲を取得する
     * @return バケット名-同期範囲のマップ
     */
    Map<String, NbQuery> getSyncScope() {
        return mDatabaseManager.readSyncScope();
    }

    /**
     * 同期範囲の削除を行う
     * @param bucketName バケット名
     */
    void removeSyncScope(@NonNull String bucketName) {
        try {
            mDatabaseManager.removeSyncScope(bucketName);
        } catch (NbDatabaseException ex) {
            log.severe("removeSyncScope() ERR " + ex);
            throw ex;
        }
    }

    NbSyncState getSyncState(@NonNull String objectId, @NonNull String bucketName) {
        NbSyncState state = NbSyncState.NOSTATE;
        try {
            NbObjectEntity data = mDatabaseManager.readObject(objectId, bucketName);

            if (data != null) {
                state = data.getState();
            }
        } catch (NbDatabaseException e) {
            //キャッシュ中にデータが未登録の場合、
            //Exceptionが発生した場合はSyncState.NOSTATEを返す
        }
        return state;
    }

    /**
     * オブジェクトの最終同期日時を取得する。
     * ローカルキャッシュのオブジェクトの updatedAt を最終同期日時とする。
     * @param objectId オブジェクトID
     * @param bucketName バケット名
     * @return 日時文字列
     */
    String getObjectLastSyncTime(@NonNull String objectId, @NonNull String bucketName) {
        String time = null;
        try {
            NbObjectEntity data = mDatabaseManager.readObject(objectId, bucketName);
            if (data != null) {
                time = data.getTimestamp();
            }
        } catch (NbDatabaseException e) {
            //キャッシュ中にデータが未登録の場合、
            //Exceptionが発生した場合はnullを返す
        }
        return time;
    }

    /**
     * バケットの最終同期日時を取得する。
     * @param bucketName バケット名
     * @return バケットの最終同期日時
     */
    String getLastSyncTime(@NonNull String bucketName) {
        return mDatabaseManager.getLastSyncTime(bucketName);
    }

    /**
     * 衝突解決ポリシを設定する
     * @param bucketName バケット名
     * @param policy 衝突解決ポリシ
     */
    void setResolveConflictPolicy(@NonNull String bucketName, @NonNull NbConflictResolvePolicy policy) {
        log.fine("setResolveConflictPolicy() <start>"
                + "bucketName=" + bucketName + " policy=" + policy);
        NbBucketEntity targetBucket = null;
        try {
            targetBucket = mDatabaseManager.readBucket(bucketName, false);
        } catch (NbDatabaseException e) {
            //処理不要
        }
        long count = 0;
        if (targetBucket == null) {
            log.severe("setResolveConflictPolicy() read ERR"
                    + " IllegalArgumentException");
            throw new IllegalArgumentException();
        }
        targetBucket.setPolicy(policy);

        try {
            count = mDatabaseManager.updateBucket(bucketName, targetBucket, false);
        } catch (NbDatabaseException e) {
            //処理不要
        }
        log.fine("setResolveConflictPolicy() <end> count=" + count);
    }

    /**
     * 衝突解決ポリシを取得する
     * @param bucketName バケット名
     * @return 衝突解決ポリシ
     */
    NbConflictResolvePolicy getResolveConflictPolicy(@NonNull String bucketName) {
        log.fine("getResolveConflictPolicy() <start>"
                + "bucketName=" + bucketName);
        NbBucketEntity targetBucket = null;
        try {
            targetBucket = mDatabaseManager.readBucket(bucketName, false);
        } catch (NbDatabaseException e) {
            //処理不要
        }
        if (targetBucket == null) {
            log.severe("getResolveConflictPolicy() read ERR"
                    + " IllegalArgumentException");
            throw new IllegalArgumentException();
        }
        NbConflictResolvePolicy policy = targetBucket.getPolicy();

        log.fine("getResolveConflictPolicy() <end> policy=" + policy);
        return policy;
    }

    void registerSyncEventListener(@NonNull String bucketName, @NonNull NbObjectSyncEventListener listener) {
        log.fine("registerSyncEventListener<start> bucketName=" + bucketName);

        synchronized (mSyncEventListeners) {
            Set<NbObjectSyncEventListener> bucketListeners = mSyncEventListeners.get(bucketName);
            // 指定bucketに対するListener初回登録の場合、null
            if (bucketListeners == null) {
                // 初回登録
                log.fine("first registration for [" + bucketName + "]");
                // ループ処理で問題が発生しないよう、ConcurrentHashMapベースでSetを生成
                bucketListeners = Collections.newSetFromMap(new ConcurrentHashMap<NbObjectSyncEventListener, Boolean>());
            }
            bucketListeners.add(listener);
            mSyncEventListeners.put(bucketName, bucketListeners);
        }
        log.fine("registerSyncEventListener<end>");
    }

    void unregisterSyncEventListener(@NonNull String bucketName, @NonNull NbObjectSyncEventListener listener) {
        log.fine("unregisterSyncEventListener<start> bucketName=" + bucketName);

        synchronized (mSyncEventListeners) {
            Set<NbObjectSyncEventListener> bucketListeners = mSyncEventListeners.get(bucketName);
            if (bucketListeners != null) {
                // bucketに対する登録が行われている場合、登録解除
                log.fine("unregister Listener");
                bucketListeners.remove(listener);

                // 登録済みリスナーがなくなった場合は、Setインスタンスを破棄
                if (bucketListeners.isEmpty()) {
                    log.fine("remove Set instance from SyncEventListeners");
                    mSyncEventListeners.remove(bucketName);
                }
            }
            // 未登録の場合は何もしない
        }

        log.fine("unregisterSyncEventListener<end>");
    }

//    /**
//     * 自動同期間隔を設定する
//     * @param bucketName バケット名
//     * @param interval 同期間隔。単位は秒。0以下に設定すると自動同期を停止する。
//     */
//    void setAutoSyncInterval(String bucketName, long interval) {
//        log.fine("setAutoSyncInterval() interval=" + interval);
//        if (bucketName == null || interval == 0) {
//            log.severe("setAutoSyncInterval() ERR"
//                    + " IllegalArgumentException");
//            throw new IllegalArgumentException();
//        }
//        try {
//            mDatabaseManager.saveAutoSyncInterval(bucketName, interval);
//        } catch (NbDatabaseException ex) {
//            log.severe("setAutoSyncInterval() ERR " + ex);
//            throw ex;
//        }
//
//        //自動同期開始/停止
//        if (0 < interval) {
//            startAutoSync(bucketName, interval);
//        } else {
//            stopAutoSync(bucketName);
//        }
//    }
//
//    /**
//     * 自動同期間隔を取得する
//     * @param bucketName バケット名
//     * @return 自動同期間隔。単位は秒。
//     */
//    protected long getAutoSyncInterval(String bucketName) {
//        long time = -1;
//        try {
//          time = mDatabaseManager.readAutoSyncInterval(bucketName);
//        } catch (NbDatabaseException e) {
//            log.fine("getAutoSyncInterval() readAutoSyncInterval Err"
//                    + " NbDatabaseException e=" + e);
//        }
//        return time;
//    }
//
//    class AutoSyncTask extends TimerTask {
//
//        private String mAutoSyncBucket;
//
//        public AutoSyncTask(String bucketName) {
//            if(bucketName == null) {
//                throw new IllegalArgumentException("bucketName is null");
//            }
//            log.fine("AutoSyncTask() created. bucketName=" + bucketName);
//
//            mAutoSyncBucket = bucketName;
//        }
//
//        public void run() {
//            long startTime = System.currentTimeMillis();
//            log.fine("AutoSyncTask#run() <start>"
//                    + " currentTimeMillis()==" + startTime);
//            //圏外時は同期不可
//            if (!mOfflineService.isOnline()) {
//                log.severe("AutoSyncTask#run() <end> ERR not online");
//                return;
//            }
//
//            // バケット単位の同期を実行
//            int result = syncBucket(mAutoSyncBucket, true);
//            log.fine("AutoSyncTask#run()->syncBucket() result=" + result);
//
//            long stopTime = System.currentTimeMillis();
//            log.fine("AutoSyncTask#run() <end>"
//                    + " currentTimeMillis()==" + stopTime + " cost time=" + (stopTime - startTime));
//
//            mockCountDown();
//        }
//    }
//
//    /**
//     * 自動同期を開始する
//     * @param interval 同期間隔。単位は秒。
//     */
//    private void startAutoSync(String bucketName, long interval) {
//        log.fine("startAutoSync() <start> bucketName= " + bucketName + " interval=" + interval);
//
//        //再送を止める
//        mRetryTimer.cancel();
//
//        //インターバル時刻生成（秒→ミリ秒変換）
//        long msecInterval = 0;
//        //変換後にMAX値を超える場合は、最大値で繰り返す
//        if ((Long.MAX_VALUE / 1000) <= interval) {
//            msecInterval = Long.MAX_VALUE;
//        } else {
//            msecInterval = interval * 1000;
//        }
//        log.fine("startAutoSync() currentTimeMillis()="
//                + System.currentTimeMillis());
//        log.fine("startAutoSync() msecInterval="
//                + msecInterval);
//
//        // 新規にタスクを生成
//        AutoSyncTask newTask = new AutoSyncTask(bucketName);
//
//        AutoSyncTask oldTask;
//        // 管理テーブル登録済みの旧タスクの削除、新タスクの追加
//        synchronized (mAutoSyncTaskMap) {
//            oldTask = mAutoSyncTaskMap.remove(bucketName);
//            // タスクの管理テーブルに追加
//            mAutoSyncTaskMap.put(bucketName, newTask);
//        }
//        // 動作中タスクが存在する場合は停止
//        if (oldTask != null) {
//            log.fine("startAutoSync() cancel oldTask. bucketName=" + bucketName);
//            oldTask.cancel();
//        }
//
//        // タイマ開始（初回は直後（0秒後）に実行する）
//        mTimer.scheduleAtFixedRate(newTask, 0, msecInterval);
//
//        // cancel済みタスクをcleanup
//        int purgeTaskNum = mTimer.purge();
//        int purgeRetryTaskNum = mRetryTimer.purge();
//        log.fine("startAutoSync() purgeTaskNum=" + purgeTaskNum);
//        log.fine("startAutoSync() purgeRetryTaskNum=" + purgeRetryTaskNum);
//
//        log.fine("startAutoSync() <end>");
//    }

    protected void mockSetLatch(int count) {
        ;   //処理無し
    }

    protected void mockCountDown() {
        ;   //処理無し
    }

    protected void mockWaitLatch() {
        ;   //処理無し
    }


//    private void stopAutoSync(String bucketName) {
//        log.fine("stopAutoSync() <start> bucketName=" + bucketName);
//        log.fine("stopAutoSync() currentTimeMillis()="
//                + System.currentTimeMillis());
//
//        AutoSyncTask oldTask;
//        synchronized (mAutoSyncTaskMap) {
//            // 管理テーブル登録済みの旧タスクを削除
//            oldTask = mAutoSyncTaskMap.remove(bucketName);
//        }
//        // タスクが存在した場合はcancel
//        if (oldTask != null) {
//            log.fine("stopAutoSync() cancel oldTask. bucketName=" + bucketName);
//            oldTask.cancel();
//        }
//
//        int purgeTaskNum = mTimer.purge();
//        log.fine("stopAutoSync() purgeTaskNum=" + purgeTaskNum);
//
//
//        log.fine("stopAutoSync() <end>");
//    }
//
//    private class RetrySyncTask extends TimerTask {
//        public void run() {
//            log.fine("RetrySyncTask#run() <start>"
//                    + " currentTimeMillis()==" + System.currentTimeMillis());
//
//            //圏外時は同期不可
//            if (!mOfflineService.isOnline()) {
//                log.severe("RetrySyncTask()#run() <end> ERR not online");
//                return ;
//            }
//
//            //再同期中に更新
//            syncStates.startSyncRetry();
//
//            final Set<String> bucketNames = new HashSet<>(mBatchRetransmitPendingMap.keySet());
//            for (final String bucketName: bucketNames) {
//                syncBucket(bucketName);
//            }
//
//            //全バケットの同期が完了したら再送停止
//            if (mBatchRetransmitPendingMap.isEmpty()) {
//                //再送停止（キュー上のスケジュールを削除）
//                stopRetrySync();
//            }
//
//            syncStates.stopSync();
//
//            log.fine("RetrySyncTask#run() <end>"
//                    + " currentTimeMillis()=" + System.currentTimeMillis());
//            //モック用ラッチ
//            mockCountDown();
//        }
//
//        // バケット１つを同期
//        private void syncBucket(String bucketName) {
//            log.severe("RetrySyncTask()#run()"
//                    + " loop bucketName=" + bucketName);
//
//            //同期開始通知
//            notifySyncStart(bucketName);
//
//            String batchURL = NbConsts.OBJECTS_PATH + "/" + bucketName + BATCH_URL;
//
//            //既に作られたバッチMAPを取得
//            NbJSONObject batchMap = mBatchRetransmitPendingMap.get(bucketName).batchJson;
//            Map<String, String> param = mBatchRetransmitPendingMap.get(bucketName).param;
//            Request requestPost = mHttpRequestFactory
//                    .post(batchURL)
//                    .body(batchMap)
//                    .params(param)
//                    .build();
//
//            SyncTaskHandler handler = new SyncTaskHandler(bucketName);
//
//            execRetrySyncTaskRun(requestPost, handler, bucketName);
//
//            handler.await(BUCKET_PUSH_TIMEOUT);
//
//            int result = handler.getOfflineResultContainer().getStatusCode();
//            log.fine("RetrySyncTask()#run() status=" + result);
//
//            //pushデータで更新開始
//            Set<String> syncObjects = new HashSet<>();
//            int localResult = pushUpdate(handler.getOfflineResultContainer(), null, bucketName, syncObjects);
//            if (localResult != NbStatus.OK) {
//                if (result == NbStatus.OK) {
//                    result = NbStatus.INTERNAL_SERVER_ERROR;
//                }
//            }
//            //正常終了
//            if (result == NbStatus.OK) {
//                //リトライ対象から消す
//                mBatchRetransmitPendingMap.remove(bucketName);
//                //バケット毎に、SYNC済みデータの最終更新日を設定
//                updateDbLastTime(bucketName);
//            }
//            //同期完了通知
//            notifySyncCompleted(bucketName, syncObjects);
//            log.fine("RetrySyncTask#run()"
//                    + " bucketName=" + bucketName + " result=" + result);
//        }
//
//        private class SyncTaskHandler extends JsonOfflineResultResponseHandler {
//            private String mBucketName;
//
//            public SyncTaskHandler(String bucketName) {
//                mBucketName = bucketName;
//            }
//
//            @Override
//            protected void onSuccess(Response result) {
//                //再送不要なデータは削除
//                mBatchRetransmitPendingMap.remove(mBucketName);
//                super.onSuccess(result);
//            }
//
//            @Override
//            protected void onError(Response result, int statusCode) {
//                //再送不要なエラーの場合は再送データを削除
//                if (!NbUtil.isScopeSyncRetryNeeded(statusCode)) {
//                    log.fine("RetrySyncTask()#run()"
//                            + " handleResponse() failure mBatchRetransmitPendingMap.remove(" + mBucketName + ")");
//                    //再送不要なデータは削除
//                    mBatchRetransmitPendingMap.remove(mBucketName);
//                }
//            }
//        }
//    }

//    protected void execRetrySyncTaskRun(Request request, NbRestResponseHandler handler, String bucketName) {
//        mRestExecutorFactory.build().executeRequest(request, handler);
//    }


//    /**
//     * 自動同期の再送を行う
//     */
//    private void startRetrySync() {
//        log.fine("startRetrySync() <start>");
//
//        //前回実行中だった場合はキャンセルする。
//        mRetryTimer.cancel();
//        int purgeRetryTaskNum = mRetryTimer.purge();
//        log.fine("startRetrySync() purgeRetryTaskNum="
//                + purgeRetryTaskNum);
//
//        mRetryTimer = new Timer();
//
//        long intervalTime = NbOfflineUtil.RETRY_BASE_TIME_MSEC;    //基数
//        long startTime = 0;
//        for (int i = 0; i < NbOfflineUtil.RETRY_COUNT; i++, intervalTime = intervalTime * 2) {
//            startTime = startTime  + intervalTime;
//            log.fine("startRetrySync() startTime=" + startTime);
//            mRetryTimer.schedule(new RetrySyncTask(), Long.valueOf(startTime));
//        }
//
//        log.fine("startRetrySync() <end>");
//    }

//    private void stopRetrySync() {
//        log.fine("stopRetrySync() <start>");
//
//        log.fine("stopRetrySync() currentTimeMillis()="
//                + System.currentTimeMillis());
//        //再送を止める
//        mRetryTimer.cancel();
//        int purgeRetryTaskNum = mRetryTimer.purge();
//        log.fine("stopRetrySync() purgeRetryTaskNum=" + purgeRetryTaskNum);
//
//        log.fine("stopRetrySync mBatchRetransmitPendingMap.clear()");
//        //再送データをクリア
//        mBatchRetransmitPendingMap.clear();
//
//        log.fine("stopRetrySync() <end>");
//    }

//    private void deleteRetryData(String objectId, String bucketName) {
//        BatchArg arg = mBatchRetransmitPendingMap.get(bucketName);
//        if (arg == null) {
//            return;
//        }
//        NbJSONObject map = arg.batchJson;
//        if (map == null) {
//            return;
//        }
//        NbJSONArray<NbJSONObject> batchList = map.getJSONArray(NbKey.REQUESTS);
//
//        for (NbJSONObject data : batchList) {
//            if (objectId.equals(data.get(NbKey.ID))) {
//                //リストからオブジェクトごと削除
//                batchList.remove(data);
//                break;
//            }
//        }
//
//        //リストが空なら元のMAPからも削除
//        if (batchList.isEmpty()) {
//            map.remove(NbKey.REQUESTS);
//            log.fine("deleteRetryData()"
//                    + " mBatchRetransmitPendingMap.remove(" + bucketName + ")");
//            mBatchRetransmitPendingMap.remove(bucketName);
//        }
//    }

//    /**
//     * 同期保留中（再同期待ち）のオブジェクトID一覧取得。<br>
//     * ネットワーク障害により同期が失敗した場合に同期保留している<br>
//     * オブジェクトのID一覧取得を行う。
//     * @param bucketName バケット名
//     */
//    protected List<String> getPendingSyncObjectList(String bucketName) {
//        log.fine("getPendingSyncObjectList() <start>"
//                + " bucketName=" + bucketName);
//        if (mBatchRetransmitPendingMap.isEmpty()) {
//            log.fine("getPendingSyncObjectList() <end>"
//                    + " mBatchRetransmitPendingMap.isEmpty()=" + mBatchRetransmitPendingMap.isEmpty());
//            return null;
//        }
//        BatchArg arg = mBatchRetransmitPendingMap.get(bucketName);
//        if (arg == null) {
//            return null;
//        }
//        NbJSONObject map = arg.batchJson;
////      log.fine("getPendingSyncObjectList() map=" + map);
//        List<String> resultList = new ArrayList<String>();
//        if (map != null) {
//            NbJSONArray<NbJSONObject> batchList = map.getJSONArray(NbKey.REQUESTS);
//
////          log.fine("getPendingSyncObjectList() batchList=" + batchList);
//            for (NbJSONObject data : batchList) {
//                String objectId = data.getString(NbKey.ID);
//                log.fine("getPendingSyncObjectList() "
//                        + " pendingId=" + objectId);
//                resultList.add(objectId) ;
//            }
//        }
//
//        log.fine("getPendingSyncObjectList() <end>"
//                + " resultList=" + resultList);
//        return resultList;
//    }

    /**
     * オブジェクト１個分のコンフリクトのチェックを行う。<br>
     * コンフリクトが発生した場合通知を行う。
     *
     * @param bucketName バケット名
     * @param client クライアントデータ
     * @param server サーバデータ
     * @param syncObjects 同期されたオブジェクトIDのセット (in/out)
     * @return コンフリクトが発生した場合true 発生しない場合false
     */
    boolean checkConflict(String bucketName, NbObjectEntity client, NbObjectEntity server,
            Set<String> syncObjects) {

        boolean isConflict = false;
        NbSyncState clientState = client.getState();
        //本メソッドはpullUpdate、pushUpdateBucketOk、pushUpdateBucketNotFoundから呼び出される。
        //CONFLICTED系の状態は前回衝突を検知しているが、同期要求を受けた時にはユーザへ認知させる意味でも
        //衝突検知・解決を行い、アプリへ通知する。
        if (clientState.isDirty() || clientState.isDeleted()) {
            isConflict = resolveConflictForClientDirty(bucketName, client, server, syncObjects,
                    clientState);
        } else {
            //ローカル側は変更無しのためサーバ側の内容をDBに反映
            resolveConflictForClientNotDirty(bucketName, client, server, syncObjects);
        }

        if (isConflict) {
            log.info("checkConflict(): Conflicted. objectId={}", client.getObjectId());
        }

        return isConflict;
    }

    /**
     * 衝突解決。クライアントが DIRTY でない(=SYNC)場合の処理。
     * サーバ側を選択する。
     * @param bucketName バケット名
     * @param client クライアントデータ
     * @param server サーバデータ
     * @param syncObjects 同期されたオブジェクトIDのセット (in/out)
     */
    private void resolveConflictForClientNotDirty(String bucketName, NbObjectEntity client,
            NbObjectEntity server, Set<String> syncObjects) {
        try {
            if (server.getState() == NbSyncState.SYNCING_DELETE) {
                //サーバで削除されたデータはローカルも物理削除
                mDatabaseManager.deleteObject(server.getObjectId(), bucketName);
                syncObjects.add(server.getObjectId());
            } else {
                //ETag不一致の場合はサーバ側のみ更新が発生したことを示すため、ローカルDBへ反映
                if (!NbUtil.isStringEquals(client.getETag(), server.getETag())) {
                    mDatabaseManager.updateObject(bucketName, server);
                    syncObjects.add(server.getObjectId());
                }
            }
        } catch (NbDatabaseException e) {
            log.fine("resolveConflictForClientNotDirty() NbDatabaseException e=" + e);
        }
    }

    /**
     * 衝突解決。クライアントが DIRTY の場合の処理。
     * @param bucketName バケット名
     * @param client クライアントデータ
     * @param server サーバデータ
     * @param syncObjects 同期されたオブジェクトIDのセット (in/out)
     * @param clientState クライアント側メタの同期状態
     * @return 衝突していれば true。自動解決した場合は false。
     */
    private boolean resolveConflictForClientDirty(String bucketName, NbObjectEntity client,
            NbObjectEntity server, Set<String> syncObjects, NbSyncState clientState) {
        log.fine("resolveConflictForClientDirty() client.getETag()="
                + client.getETag() + " objectId=" + client.getObjectId());
        log.fine("resolveConflictForClientDirty() server.getETag()="
                + server.getETag() + " objectId=" + server.getObjectId());

        boolean isConflict = false;

        if (client.getETag() == null) {
            NbOfflineObject clientObj =
                    new NbOfflineObject(bucketName);
            NbJSONObject clientData = client.getJsonObject();
            //ID重複のためクライアントのデータのIDを振りなおす
            client.setObjectId(NbOfflineUtil.makeObjectId());
            clientData.put(NbKey.ID, client.getObjectId());
            client.setJsonObjectAsImmutable(clientData);
            client.setState(NbSyncState.DIRTY);
            try {
                mDatabaseManager.createObject(client.getObjectId(), bucketName, client);

                if (server.getState() == NbSyncState.SYNCING_DELETE) {
                    //サーバで削除されたデータはローカルも物理削除
                    mDatabaseManager.deleteObject(server.getObjectId(), bucketName);
                } else {
                    //サーバ側のデータは保存
                    mDatabaseManager.updateObject(bucketName, server);
                }
                syncObjects.add(server.getObjectId());

                clientObj.setCurrentParam(clientData);

                notifySyncError(bucketName, NbObjectSyncEventListener.SyncError.ID_CONFLICTED, clientObj);
            } catch (NbDatabaseException e) {
                log.fine("resolveConflictForClientDirty() Err"
                        + " NbDatabaseException e=" + e);
            }
            //ID重複はコンフリクト通知を上げないのでisConflictはfalseのまま
        } else if (!client.getETag().equals(server.getETag())) {
            //自動解決の場合でもresolveConflictで参照するので、mTempConflictObjectにputする。
            mConflictedServerObjects.put(server.getObjectId(), server);
            //ETagが異なるので衝突
            NbBucketEntity bucket = null;
            try {
                bucket = mDatabaseManager.readBucket(bucketName, false);
            } catch (NbDatabaseException e) {
                log.fine("resolveConflictForClientDirty() readBucket()"
                        + "Err NbDatabaseException e=" + e);
            }
            NbConflictResolvePolicy policy = null;
            if (bucket != null) {
                policy = bucket.getPolicy();
            }
            log.fine("resolveConflictForClientDirty() policy=" + policy);

            if (policy == null) {
                //DBにバケットが存在しない
                mConflictedServerObjects.remove(server.getObjectId());
            }
            else switch (policy) {
                case CLIENT:
                    //pull時にサーバデータ削除済みの場合やpush時にNotfoundで返却された場合は
                    //下記のresolveConflict()でオブジェクトIDの変更（ID重複）が発生する。
                    String objectId = resolveConflict(bucketName, client.getObjectId(),
                            NbConflictResolvePolicy.CLIENT, syncObjects);
                    log.fine("resolveConflictForClientDirty()"
                            + " resolveConflict -> objectId=" + objectId);
                    client.setObjectId(objectId);
                    log.fine("resolveConflictForClientDirty()"
                            + " server.getETag()=" + server.getETag() + " server.getState()="
                            + server.getState());
                    break;

                case SERVER:
                    resolveConflict(bucketName, server.getObjectId(), NbConflictResolvePolicy.SERVER, null);
                    syncObjects.add(server.getObjectId());
                    break;

                case MANUAL:
                    NbOfflineObject clientObj = new NbOfflineObject(bucketName);
                    clientObj.setCurrentParam(client.getImmutableJsonObject());
                    NbOfflineObject serverObj = new NbOfflineObject(bucketName);
                    serverObj.setCurrentParam(server.getImmutableJsonObject());
                    if (clientState.isDirtyNotFull()) {
                        client.setState(NbSyncState.CONFLICTED);
                    } else if (clientState.isDirtyFull())  {
                        client.setState(NbSyncState.CONFLICTED_FULL);
                    } else {
                        client.setState(NbSyncState.CONFLICTED_DELETE);
                    }
                    try {
                        mDatabaseManager.updateObject(bucketName, client);


                        //notifySyncConflicted(this, bucketName, clientObj, serverObj);

                        SyncConflictInfoContainer tmpContainer = new SyncConflictInfoContainer();
                        tmpContainer.setResolver(this);
                        tmpContainer.setTargetBucket(bucketName);
                        tmpContainer.setClientObject(clientObj);
                        tmpContainer.setServerObject(serverObj);
                        mSyncConflictEvents.add(tmpContainer);


                        isConflict = true;
                    } catch (NbDatabaseException e) {
                        log.fine("resolveConflictForClientDirty() updateObject()"
                                + "Err NbDatabaseException e=" + e);
                    }
                    break;
            }
        }
        return isConflict;
    }

    protected void notifySyncConflictedEvents() {
        Iterator<SyncConflictInfoContainer> i = mSyncConflictEvents.iterator();
        while (i.hasNext()){

            SyncConflictInfoContainer tmp = i.next();
            notifySyncConflicted(
                    tmp.getResolver(),
                    tmp.getTargetBucket(),
                    tmp.getClientObject(),
                    tmp.getServerObject()
            );
            i.remove();
        }
    }

    protected void notifySyncErrorOnce() {
        if (mSyncErrorEvent != null) {
            notifySyncError(
                    mSyncErrorEvent.getTargetBucket(),
                    mSyncErrorEvent.getErrorCode(),
                    mSyncErrorEvent.getErrorObject());
            mSyncErrorEvent = null;
        }
    }

    protected void notifyConflict(@NonNull String bucketName, @NonNull NbObject client, @NonNull NbObject server) {

        Set<NbObjectSyncEventListener> listeners = getEventListeners(bucketName);
        for (NbObjectSyncEventListener listener : listeners) {
            NbObjectEntity serverInfo = new NbObjectEntity();
            serverInfo.setETag(server.getETag());
            serverInfo.setObjectId(server.getObjectId());
            serverInfo.setAclString(server.getAcl().toJsonString());
            serverInfo.setState(NbSyncState.SYNC);
            serverInfo.setTimestamp(server.getUpdatedTime());

            NbJSONObject jsonData = new NbJSONObject();

            jsonData.put(NbKey.ID, server.getObjectId());
            jsonData.put(NbKey.UPDATED_AT, server.getUpdatedTime());
            jsonData.put(NbKey.CREATED_AT, server.getCreatedTime());
            jsonData.put(NbKey.ETAG, server.getETag());
            jsonData.put(NbKey.ACL, server.getAcl().toJsonObject());

            NbJSONObject data = server.getObjectData();
            for (String key : data.keySet()) {
                jsonData.put(key, data.get(key));
            }
            serverInfo.setJsonObjectAsImmutable(jsonData);
            mConflictedServerObjects.put(server.getObjectId(), serverInfo);
            log.fine("notifyConflict() call onSyncConflicted() objectId="
                            + server.getObjectId() + " bucketName=" + bucketName);
            notifySyncConflicted(this, listener, bucketName, client, server);
        }
    }

    abstract protected void forceAutoSync();

    static class NbOfflineObject extends NbObject {
        protected NbOfflineObject(String bucketName) {
            // 同期を行うのはレプリカモードのみ
            super(bucketName, NbBucketMode.REPLICA);
        }
        protected void setCurrentParam(NbJSONObject json) {
            //呼び出し元は可ならNew NebulaObjectで生成した直後に本APIを呼び出しているため、
            //isInit=trueでも問題無いが既存機能への影響度を下げるためにfalse(既存動作)としておく。
            super.setCurrentParam(json, false);
        }
    }

    protected class PushUpdateTask implements Callable<Integer> {

        NbOfflineResult mPushObjects = null;
        String mObjectId = null;
        String mBucketName = null;
        Set<String> mSyncObjects = null;

        PushUpdateTask(NbOfflineResult pushObjects, String objectId, String bucketName, Set<String> syncObjects) {
            mPushObjects = pushObjects;
            mObjectId = objectId;
            mBucketName = bucketName;
            mSyncObjects = syncObjects;
        }

        @Override
        public Integer call() {
            return pushUpdate(mPushObjects, mObjectId, mBucketName, mSyncObjects);
        }
    }

    /**
     * サーバ優先で衝突を解決する。
     * infoをDBに保存する。
     * @param bucketName バケット名
     * @param info サーバ側データ
     * @return 成功時は true、失敗時は false
     */
    private boolean resolveConflictServer(String bucketName, NbObjectEntity info) {
        NbObjectEntity local = null;
        log.fine("resolveConflictServer() <start>"
                + "save server objectId=" + info.getObjectId());

        try {
            local = mDatabaseManager.readObject(info.getObjectId(), bucketName);
        } catch (NbDatabaseException e) {
            //処理不要
        }

        log.fine("resolveConflictServer() local=" + local);

        boolean result = true;
        long syncCount = 0;
        log.fine("resolveConflictServer() server state="
                + info.getState());
        //サーバ側で削除されたデータでサーバ優先で解決する場合は、ローカルDBを削除
        if (info.getState() == NbSyncState.SYNCING_DELETE) {
            //サーバで削除されたデータはローカルも物理削除
            try {
                syncCount = mDatabaseManager.deleteObject(info.getObjectId(), bucketName);
            } catch (NbDatabaseException e) {
                log.severe("resolveConflictServer() <end>"
                        + " ERR delete e=" + e);
            }
        } else {
            info.setState(NbSyncState.SYNCING);
            if (local == null) {
                try {
                    syncCount = mDatabaseManager.createObject(info.getObjectId(), bucketName, info);
                } catch (NbDatabaseException e) {
                    log.severe("resolveConflictServer() <end>"
                            + " ERR create e=" + e);
                }
            } else {
                try {
                    syncCount = mDatabaseManager.updateObject(bucketName, info);
                } catch (NbDatabaseException e) {
                    log.severe("resolveConflictServer() <end>"
                            + " ERR update e=" + e);
                }
            }
        }
        if (syncCount == 0 || syncCount == NbDatabaseManager.INSERT_ERROR_CODE) {
            result = false;
        }

        log.fine("resolveConflictServer() <end> result=" + result);
        return result;
    }

    /**
     * クライアント優先で衝突を解決する。
     * 同期終了後の解決の場合は、infoをサーバにpushする
     * @param bucketName バケット名
     * @param client クライアントデータ
     * @param server サーバデータ
     * @return 成功時は true、失敗時は false
     */
    private boolean resolveConflictClient(String bucketName,
            NbObjectEntity client, NbObjectEntity server) {
        boolean isResult = true;
        log.fine("resolveConflictClient()"
                + " sync state=" + mSyncStates.isSyncing());

        //クライアントを優先する場合、サーバ側へpush時にETag不一致を検出させないために
        //ETagをサーバ側のETagに置き換えて送る。
        //以降、push()でDBから状態がdirtyなデータが読み込まれ当該データをpushする。

        String objectId = client.getObjectId();
        NbJSONObject jsonMap = client.getJsonObject();

        log.fine("resolveConflictClient()"
                + " client.getState()=" + client.getState() + " server.getState()=" + server.getState());

        //サーバ側にデータが存在する場合はサーバ側のETagに合わせる
        if (client.getState().isDeleted()) {
            log.fine("resolveConflictClient() delete local data objectId=" + objectId);
            //クラサバで両削除の場合は、PUSHせずローカルDBのみ削除して終了。
            if (server.getState() == NbSyncState.SYNCING_DELETE) {
                try {
                    //PULLしたサーバデータが削除ならPUSH不要。ローカルDBは物理削除で整理。
                    mDatabaseManager.deleteObject(objectId, bucketName);
                } catch (NbDatabaseException e) {
                    log.severe("resolveConflictClient()"
                            + " database deleteObject() objectId=" + objectId + " NbDatabaseException e=" + e);
                }
                return true;
            }
        } else {
            //削除以外の場合はETagを一致させて全上書き
            client.setState(NbSyncState.DIRTY_FULL);    //←この状態を見て後続でリクエストに$full_updateを付与
        }

        //↑サーバリクエスト時は、ETagを一致させてコンフリクトを回避
        client.setETag(server.getETag());
        jsonMap.put(NbKey.ETAG, server.getETag());

//      log.fine("resolveConflictClient() client getJsonString=" + client.getJsonString());

        //JSONデータを反映
        client.setJsonObjectAsImmutable(jsonMap);

        try {
            mDatabaseManager.updateObject(objectId, bucketName, client);
        } catch (NbDatabaseException e) {
            log.severe("resolveConflictClient()"
                    + " database updateObject error NbDatabaseException e=" + e);
        }

        log.fine("resolveConflictClient()"
                + " bucketName=" + bucketName + " objectId=" + objectId);

        //ID変更が発生した？
        if (!client.getObjectId().equals(server.getObjectId())) {
            log.fine("resolveConflictClient() "
                    + "clientID=" + client.getObjectId() + " serverID=" + server.getObjectId());

            NbOfflineObject clientObj =
                    new NbOfflineObject(bucketName);
            clientObj.setCurrentParam(client.getImmutableJsonObject());

            //変更後のIDでID重複通知
            notifySyncError(bucketName, NbObjectSyncEventListener.SyncError.ID_CONFLICTED, clientObj);
        }

        int result = NbStatus.OK;
        //push開始
        NbOfflineResult pushResult = push(client.getObjectId(), bucketName,
                client.getJsonString(), null);
        log.fine("resolveConflictClient() pushResult=" + pushResult);
        if (pushResult != null) {
            result = pushResult.getStatusCode();
            //pushデータで更新開始
            Set<String> syncObjects = new  HashSet<>();
            int localResult = pushUpdate(pushResult, client.getObjectId(), bucketName, syncObjects);
            if ((NbStatus.isNotSuccessful(localResult)) && (NbStatus.isSuccessful(result)) ) {
                result = NbStatus.INTERNAL_SERVER_ERROR;
            }
        }   //push対象が無い場合はpushResult=null

        if (NbStatus.isNotSuccessful(result)) {

            //本メソッドでtureを返すとmTempConflictObjectから当該データがクリアされる。
            //エラーが発生した場合、mTempConflictObjectをクリアしてはならないのでfalse
            //コンフリクトが発生した場合は、pushUpdateにてpush後に同じオブジェクトIDで
            //mTempConflictObject内の同データを上書きしているため、これもfalseを返す。

            isResult = false;
        }

        log.fine("resolveConflictClient() isResult=" + isResult);
        return isResult;
    }

    /**
     * 指定したバケットに対するイベントリスナーのSetを取得する
     * 未登録の場合、空のSetを返却する
     * @param bucketName バケット名
     * @return 該当bucketに対するイベントリスナーのSet。未登録の場合は空のSetを返却する(nullでないことを保証)
     */
    private Set<NbObjectSyncEventListener> getEventListeners(String bucketName) {
        Set<NbObjectSyncEventListener> eventListenerSet = mSyncEventListeners.get(bucketName);
        if(eventListenerSet == null) {
            eventListenerSet = Collections.emptySet();
        }
        return eventListenerSet;
    }

    private String getLogSyncState() {
        return "[NbObjectSyncManager] printSyncState()"
                + " sync=" + mSyncStates.isSyncing()
//                + " sync_auto=" + syncStates.isAutoSyncing()
                + " sync_scope=" + mSyncStates.isScopeSyncing();
    }

}
