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
import com.nec.baas.offline.*;
import com.nec.baas.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.NonNull;

/**
 * オブジェクトストレージのオフライン機能を提供するクラス.<br>
 * @since 1.0
 */
public class NbObjectOfflineServiceImpl extends NbBaseOfflineServiceImpl implements NbObjectOfflineService {
    private static final NbLogger log = NbLogger.getLogger(NbObjectOfflineServiceImpl.class);

    private NbObjectSyncManager mSyncManager;
    /*package*/ NbOfflineService mOfflineService;

    private static final String OPERATOR_REGEX = "^\\$.*";

    /**
     * NebulaObjectOfflineServiceコンストラクタ.
     */
    protected NbObjectOfflineServiceImpl(NbOfflineService offlineService, NbObjectSyncManager sync) {
        super(offlineService);
        log.fine("NbOfflineService() : offlineService=" + offlineService + " sync=" + sync);

        mOfflineService = offlineService;
        mSyncManager = sync;
    }

    /**
     * 処理状態フラグ管理クラス
     */
    @Getter
    protected static class ProcessState {
        /** 同期中 */
        private boolean syncing = false;
        /** CRUD中 */
        private boolean crud = false;

        /** 手動オブジェクト・バケット単体同期 */
        synchronized void startSync() {
            syncing = true;
        }

        /** CRUD */
        synchronized void startCrud() {
            crud = true;
        }

        /** 同期終了 */
        synchronized void endSync() {
            syncing = false;
        }

        /** CRUD終了 */
        synchronized void endCrud() {
            crud = false;
        }
    }

    @Getter
    private ProcessState processState = new ProcessState();

    /** ロックオブジェクト */
    protected static final Object sLock = new Object();

    @Override
    protected boolean isFileService() {
        return false;
    }

    @Override
    public int syncBucket(String bucketName) {
        log.fine("syncBucket() <start> bucketName="
                + bucketName);

        //処理開始確認
        if(!tryStartSync()) {
            log.fine("syncBucket() <end> ERR locked");
            return NbStatus.LOCKED;
        }

        int result;
        try {
            result = mSyncManager.syncBucket(bucketName, false);
        }
        finally {
            //処理状態更新
            endSync();
        }

        log.fine("syncBucket() <end>"
                + " result=" + result);
        return result;
    }

    @Override
    public int sync() {
        long startTime = System.currentTimeMillis();
        log.fine("sync() <start>"
                + " currentTimeMillis()==" + startTime);

        //処理開始確認
        if(!tryStartSync()) {
            log.fine("sync() <end> ERR locked");
            return NbStatus.LOCKED;
        }

        //同期範囲の設定に従って同期
        int result;
        try {
            result = mSyncManager.syncScope(false);
        }
        finally {
            endSync();
        }

        log.fine("sync() <end> result=" + result
                + " Elapsed time=" + (System.currentTimeMillis() - startTime));

        return result;
    }

//    /**
//     * 同期保留中（再同期待ち）のオブジェクトID一覧取得。<br>
//     * ネットワーク障害により同期が失敗した場合に同期保留している<br>
//     * オブジェクトのID一覧取得を行う。
//     * @param bucketName バケット名
//     * @return オブジェクトIDリスト
//     */
//    @Override
//    public List<String> getPendingSyncObjectList(String bucketName) {
//        log.fine("getPendingSyncObjectList() <start>"
//                + " bucketName=" + bucketName);
//
//        List<String> resultList =
//                mSyncManager.getPendingSyncObjectList(bucketName);
//
//        return resultList;
//    }

    @Override
    public NbSyncState getSyncState(String objectId, String bucketName) {
        return mSyncManager.getSyncState(objectId, bucketName);
    }

    @Override
    public String getLastSyncTime(String bucketName){
        return mSyncManager.getLastSyncTime(bucketName);
    }

    @Override
    public String getObjectLastSyncTime(String objectId, String bucketName) {
        return mSyncManager.getObjectLastSyncTime(objectId, bucketName);
    }

    @Override
    public NbOfflineResult createLocalData(String bucketName, NbJSONObject json) {
        log.fine("createLocalData() <start>"
                + " bucketName=" + bucketName);

        NbOfflineResult container = new NbOfflineResult();
        if (bucketName == null || json == null) {
            container.setStatusCode(NbStatus.REQUEST_PARAMETER_ERROR);
            log.severe("createLocalData() <end> ERR param");
            return container;
        }

        //ACLチェック
        try {
            if ( !isBucketContentCreatable(bucketName)) {
                container.setStatusCode(NbStatus.FORBIDDEN);
                return container;
            }
        } catch (IllegalArgumentException e) {
            //格納先バケットが存在しない
            container.setStatusCode(NbStatus.NOT_FOUND);
            log.severe("createLocalData() <end>"
                    + " ERR bucket not found");
            return container;
        }

        String objectId = NbOfflineUtil.makeObjectId();
        json.put(NbKey.ID, objectId);
        NbObjectEntity data = new NbObjectEntity();
        data.setETag((String)json.get(NbKey.ETAG));
        data.setObjectId(objectId);
        if (null == json.get(NbKey.ACL)) {
            // オフライン時のオブジェクト生成のACL設定
            NbAcl acl = new NbAcl();
            if (loginService().isLoggedIn()) {
                // ログイン中のデフォルトACLであるownerのみの設定を行う。
                acl.setOwner(loginService().getLoginUserInfo().get(NbKey.ID).toString());
            } else {
                // 非ログイン状態のデフォルトACLであるr,w権限にanonymousの設定を行う。
                List<String> aclList = new ArrayList<String>();
                aclList.add(NbConsts.GROUP_NAME_ANONYMOUS);
                acl.setRead(aclList);
                acl.setWrite(aclList);
            }
            NbJSONObject aclJson = acl.toJsonObject();
            data.setAclJson(aclJson);
            json.put(NbKey.ACL, aclJson);
        } else {
            data.setAclJson(json.getJSONObject(NbKey.ACL));
        }
        data.setJsonObject(json);
        data.setState(NbSyncState.DIRTY);
        data.setTimestamp((String) json.get(NbKey.UPDATED_AT));

        log.fine("createLocalData()  objectId="
                + objectId);

        long resultCount = 0;
        try {
            resultCount = databaseManager().createObject(objectId, bucketName, data);
        } catch (NbDatabaseException ex) {
            log.severe("createLocalData() <end>"
                    + " ERR container=" + container);
            container.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);
            return container;
        }

        if (resultCount == 0 ) {
            container.setStatusCode(NbStatus.NOT_FOUND);
        } else if (resultCount == NbDatabaseManager.INSERT_ERROR_CODE) {
            container.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);
        } else {
            container.setSyncState(data.getState());
            container.setJsonData(data.getJsonObject());
            container.setStatusCode(NbStatus.OK);
        }
        log.fine("createLocalData() <end>"
                + " container=" + container);
        return container;
    }

    @Override
    public NbOfflineResult readLocalData(String objectId, String bucketName) {
        NbOfflineResult container = new NbOfflineResult();
        if (objectId == null || bucketName == null) {
            container.setStatusCode(NbStatus.REQUEST_PARAMETER_ERROR);
            log.fine("readLocalData() <end> ERR PARAMETER");
            return container;
        }

        NbObjectEntity info = null;
        try {
            info = databaseManager().readObject(objectId, bucketName);
        } catch (NbDatabaseException ex) {
            container.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);
            log.severe("readLocalData() <end> ERR readObject() NbDatabaseException");
            return container;
        }

        log.fine("readLocalData() objectId=" + objectId);

        if (info != null && !info.getState().isDeleted()) {
            //ACLチェック
            if ( !isBucketContentReadUpdatable(bucketName, info, null) ) {
                container.setStatusCode(NbStatus.FORBIDDEN);
                log.fine("readLocalData() <end> ERR FORBIDDEN");
                return container;
            }
            container.setSyncState(info.getState());
            container.setJsonData(info.getJsonObject());
            container.setStatusCode(NbStatus.OK);
            log.fine("readLocalData() <end>");
        } else {
            container.setStatusCode(NbStatus.NOT_FOUND);
        }

        return container;
    }

    @Override
    public NbOfflineResult updateLocalData(String objectId, String bucketName,
                                           NbJSONObject json) {
        NbOfflineResult result;
        //処理開始確認
        if (!tryStartCrud()) {
            result = new NbOfflineResult();
            result.setStatusCode(NbStatus.LOCKED);
            return result;
        }

        try {
            result = doUpdateLocalData(objectId, bucketName, json);
        } finally {
            endCrud();
        }

        return result;
    }

    private NbOfflineResult doUpdateLocalData(String objectId, String bucketName,
                                           NbJSONObject json) {
        log.fine("updateLocalData() <start>"
                + " objectId=" + objectId + " bucketName=" + bucketName);
        NbOfflineResult container = new NbOfflineResult();
        if (objectId == null || bucketName == null || json == null) {
            container.setStatusCode(NbStatus.REQUEST_PARAMETER_ERROR);
            return container;
        }

        log.fine("updateLocalData() [JSON]"
                + " checkPermission=" + json.get(NbKey.ACL));
        log.fine("updateLocalData() [JSON]"
                + " null.equals()=" + ("null".equals(json.get(NbKey.ACL))));

        NbObjectEntity info = new NbObjectEntity();
        if (json.containsKey("$full_update")) {
            info.setState(NbSyncState.DIRTY_FULL);
            json = (NbJSONObject)json.get("$full_update");
        } else {
            info.setState(NbSyncState.DIRTY);
        }
        String etag = json.getString(NbKey.ETAG);
        info.setETag(etag);
        info.setJsonObject(json);
        info.setObjectId(objectId);
        info.setTimestamp(json.getString(NbKey.UPDATED_AT));

        //ACLチェックのための必要情報を取得
        NbJSONObject acl = json.getJSONObject(NbKey.ACL);
        if (acl != null){
            info.setAclJson(acl);
        }else{
            container.setStatusCode(NbStatus.REQUEST_PARAMETER_ERROR);
            return container;
        }
        String checkPermission = info.getAclString();

        NbObjectEntity cacheData;
        try {
            cacheData = databaseManager().readObject(objectId, bucketName);
        } catch (NbDatabaseException ex) {
            log.severe("updateLocalData() <end>"
                    + " ERR db access");
            container.setStatusCode(NbStatus.FORBIDDEN);
            return container;
        }
        log.fine("updateLocalData()"
                + " checkPermission=" + checkPermission);

        //ACLチェック
        if ( !isBucketContentReadUpdatable(bucketName, cacheData, checkPermission) ) {
            container.setStatusCode(NbStatus.FORBIDDEN);
            log.fine("updateLocalData() <end> ERR FORBIDDEN");
            return container;
        }

        //jsonデータに更新演算子が含まれる場合は更新しない
        if (!findUpdateOperators(info.getImmutableJsonObject())) {
            int updateCount = 0;
            try {
                //楽観ロックから更新まではトランザクションで囲む
                mOfflineService.beginTransaction();

                //楽観ロック（ETagチェック）
                if( !checkUpdate(cacheData.getETag(), etag) ) {
                    container.setStatusCode(NbStatus.CONFLICT);
                    log.fine("updateLocalData() <end> ERR CONFLICT");
                    return container;
                }

                updateCount = databaseManager().updateObject(bucketName, info);
            } catch (NbDatabaseException ex) {
                log.severe("updateLocalData() <end>"
                        + " ERR updateObject() NbDatabaseException");
                container.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);
                return container;
            } finally {
                mOfflineService.endTransaction();
            }

            if (updateCount == 0) {
                container.setStatusCode(NbStatus.NOT_FOUND);
            } else {
                container.setSyncState(info.getState());
                container.setStatusCode(NbStatus.OK);
                container.setJsonData(info.getJsonObject());
            }
        } else {
            container.setStatusCode(NbStatus.REQUEST_PARAMETER_ERROR);
        }

        log.fine("updateLocalData() <end>"
                + " container=" + container);
        return container;
    }

    /**
     * オブジェクトが更新されているか否かをチェック。<br>
     * 楽観ロックの仕組みとしてキャッシュ上のオブジェクトと更新オブジェクトETagを比較する。<br>
     * ETagが同じの場合、もしくはキャッシュ上のETagが無い場合はtrueを返す。<br>
     * @param cache キャッシュ上のETag
     * @param value 更新するオブジェクトのETag
     * @return 更新されていなければ true
     */
    private boolean checkUpdate(String cache, String value){
        //キャッシュ上のETagが無い場合はtrue
        if(cache == null){
            return true;
        }

        //ETag比較
        if((value != null) && value.equals(cache)) {
            return true;
        }

        return false;
    }

    @Override
    public NbOfflineResult deleteLocalData(String objectId, String bucketName, String etag) {
        NbOfflineResult result;
        //処理開始確認
        if (!tryStartCrud()) {
            result = new NbOfflineResult();
            result.setStatusCode(NbStatus.LOCKED);
            return result;
        }

        try {
            result = doDeleteLocalData(objectId, bucketName, etag);
        } finally {
            endCrud();
        }

        return result;
    }

    private NbOfflineResult doDeleteLocalData(String objectId, String bucketName, String etag) {
        NbOfflineResult container = new NbOfflineResult();
        if (objectId == null || bucketName == null) {
            container.setStatusCode(NbStatus.REQUEST_PARAMETER_ERROR);
            return container;
        }

        NbObjectEntity data = null;

        try {
            data = databaseManager().readObject(objectId, bucketName);
        } catch (NbDatabaseException ex) {
            container.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);
            return container;
        }

        if (data != null) {
            //ACLチェック
            if ( !isBucketContentDeletable(bucketName, data) ) {
                container.setStatusCode(NbStatus.FORBIDDEN);
                log.fine("deleteLocalData() <end> ERR FORBIDDEN");
                return container;
            }

            int count = 0;
            data.setState(NbSyncState.DELETE);
            if (data.getETag() !=  null) {
                try {
                    //楽観ロックから更新まではトランザクションで囲む
                    mOfflineService.beginTransaction();

                    //楽観ロック（ETagチェック）
                    if( !checkUpdate(data.getETag(), etag) ) {
                        container.setStatusCode(NbStatus.CONFLICT);
                        log.fine("deleteLocalData() <end> ERR CONFLICT");
                        return container;
                    }

                    count = databaseManager().updateObject(bucketName, data);
                } catch (NbDatabaseException ex) {
                    log.severe("deleteLocalData() <end>"
                            + " ERR updateObject() NbDatabaseException");
                    container.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);
                    return container;
                } finally {
                    mOfflineService.endTransaction();
                }
            } else {
                //未同期のデータは物理削除（同期不要）
                try {
                    count =  databaseManager().deleteObject(objectId, bucketName);
                } catch (NbDatabaseException e) {
                    log.severe("deleteLocalData() <end>"
                            + " ERR deleteObject() NbDatabaseException");
                }
            }
            if (count == 0) {
                container.setStatusCode(NbStatus.NOT_FOUND);
            } else {
                container.setSyncState(data.getState());
                container.setStatusCode(NbStatus.OK);
            }
        } else {
            container.setStatusCode(NbStatus.NOT_FOUND);
        }
        log.fine("deleteLocalData() <end>"
                + " container=" + container);
        return container;
    }

    @Override
    public NbOfflineResult queryLocalData(NbQuery query, String bucketName) {
        NbOfflineResult container = new NbOfflineResult();
        if (bucketName == null) {
            container.setStatusCode(NbStatus.REQUEST_PARAMETER_ERROR);
            return container;
        }

        //条件からlimitとskipを抜いて、ACLチェック後にlimitとskipの調整を行う。
        NbQuery newQuery = new NbQuery();
        if (query != null) {
            newQuery.setClause(query.getClause());
            newQuery.setCountQuery(query.isCountQuery());
            newQuery.setDeleteMark(query.isDeleteMark());
            LinkedHashMap<String, Boolean> queryMap = query.getSortOrder();
            for (String key: queryMap.keySet()) {
                newQuery.addSortOrder(key, queryMap.get(key));
            }
        }

        NbDatabaseManager.ObjectQueryResults queryResults;
        try {
            queryResults = databaseManager().queryObjects(bucketName, newQuery);
        } catch (NbDatabaseException ex) {
            log.severe("queryLocalData() <end>"
                    + " ERR queryObjects() NbDatabaseException");
            container.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);
            return container;
        }

        NbJSONObject resultJson = new NbJSONObject();
        if (!queryResults.getResults().isEmpty()) {
            //バケットのACLチェック
            if ( !isBucketReadable(bucketName) ) {
                container.setStatusCode(NbStatus.FORBIDDEN);
                return container;
            }

            int limit = NbConsts.DEFAULT_QUERY_LIMIT;
            int skip = 0;
            if (query != null) {
                limit = query.getLimit();
                skip = query.getSkipCount();
            }

            int checkedCount = 0; //チェックした件数

            Iterator<NbObjectEntity> iterator = queryResults.getResults().iterator();
            NbJSONArray<NbJSONObject> resultList = new NbJSONArray<>();
            //返却するJson作成用リストに対象を追加
            while (iterator.hasNext()) {
                NbObjectEntity info = iterator.next();
                log.fine("queryLocalData() loop objectId=" + info.getObjectId());

                boolean isAccessError = false;
                //オブジェクトのACLチェック
                if ( !isObjectReadable(info) ) {
                    isAccessError = true;
                }

                //削除済み、もしくはACLチェックでアクセス不可の場合は除外
                if (isAccessError) {
                    iterator.remove();
                } else {
                    //開始位置→skip、取得上限(-1なら上限無し)→limitに従いリストを作成する。
                    if ((checkedCount >= skip) && (limit < 0 || resultList.size() < limit)) {
                        resultList.add(info.getJsonObject());
                    }
                    checkedCount++; //アクセス可能なデータのみカウント
                }
            }

            //返却するJsonを作成
            resultJson.put(NbKey.RESULTS, resultList);
        }

        //取得データの有無に関わらずカウントは設定する。
        if (query != null && query.getCountQueryAsNum() == 1) {
            resultJson.put(NbKey.COUNT, queryResults.getResults().size());
        } else {
            resultJson.put(NbKey.COUNT, 0);
        }
        container.setJsonData(resultJson);

        container.setStatusCode(NbStatus.OK);
        return container;
    }

    @Override
    public void saveCacheData(String objectId, String bucketName, NbJSONObject json) {
        saveCacheData(objectId, bucketName, json, false);
    }

    @Override
    public void saveCacheData(String objectId, String bucketName, NbJSONObject json, boolean isForce) {
        if (objectId == null || bucketName == null || json == null) return;
        NbObjectEntity readData = null;
        try {
            readData = databaseManager().readObject(objectId, bucketName);
        } catch (NbDatabaseException e) {
            //キャッシュは上位にエラーを通知しないので処理不要
        }

        NbObjectEntity saveData = new NbObjectEntity();
        saveData.setJsonString(json.toJSONString());
        saveData.setETag(json.getString(NbKey.ETAG));
        saveData.setObjectId(objectId);
        saveData.setAclJson(json.getJSONObject(NbKey.ACL));
        saveData.setState(NbSyncState.SYNC);
        saveData.setTimestamp(json.getString(NbKey.UPDATED_AT));

        if (readData == null) {
            try {
              log.fine("saveCacheData() createObject()"
                      + " objectId=" + objectId + " bucketName=" + bucketName);
                databaseManager().createObject(objectId, bucketName, saveData);
            } catch (NbDatabaseException e) {
                //キャッシュは上位にエラーを通知しないので処理不要
            }
        } else {
            if (!isForce) {
                Set<String> syncObjects = new HashSet<>();
                mSyncManager.checkConflict(bucketName, readData, saveData, syncObjects);
                mSyncManager.notifySyncConflictedEvents();
            } else {
                try {
                    log.fine("saveCacheData() updateObject()"
                            + " objectId=" + objectId + " bucketName=" + bucketName);
                    databaseManager().updateObject(objectId, bucketName, saveData);
                } catch (NbDatabaseException e) {
                    //キャッシュは上位にエラーを通知しないので処理不要
                }
            }
        }
    }

    @Override
    public NbOfflineResult readCacheData(String objectId, String bucketName) {
        NbOfflineResult container = new NbOfflineResult();
        if (objectId == null || bucketName == null) {
            container.setStatusCode(NbStatus.REQUEST_PARAMETER_ERROR);
            log.fine("readCacheData() <end> ERR PARAMETER");
            return container;
        }

        NbObjectEntity info;
        try {
            info = databaseManager().readObject(objectId, bucketName);
        } catch (NbDatabaseException ex) {
            container.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);
            log.severe("readCacheData() <end> ERR readObject() NbDatabaseException");
            return container;
        }

        if (info != null && !info.getState().isDeleted()) {
            container.setSyncState(info.getState());
            container.setJsonData(info.getJsonObject());
            container.setStatusCode(NbStatus.OK);
            log.fine("readCacheData() <end>");
        } else {
            container.setStatusCode(NbStatus.NOT_FOUND);
        }

        return container;
    }

    @Override
    public boolean removeCacheData(String objectId, String bucketName) {
        if (objectId == null || bucketName == null) return false;
        int result = 0;
        try {
            result =  databaseManager().deleteObject(objectId, bucketName);
        } catch (NbDatabaseException e) {
            //キャッシュは上位にエラーを通知しないので処理不要
        }
        return result != 0;
    }

    @Override
    public void setResolveConflictPolicy(String bucketName, NbConflictResolvePolicy policy) {
        mSyncManager.setResolveConflictPolicy(bucketName, policy);
    }

    protected void setRequestFactory(NbHttpRequestFactory factory) {
        mSyncManager.setHttpRequestFactory(factory);
    }

    @Override
    public NbConflictResolvePolicy getResolveConflictPolicy(String bucketName) {
        return mSyncManager.getResolveConflictPolicy(bucketName);
    }

//    @Override
//    public void setAutoSyncInterval(String bucketName, long interval) {
//        mSyncManager.setAutoSyncInterval(bucketName, interval);
//    }
//
//    @Override
//    public long getAutoSyncInterval(String bucketName) {
//        long interval = 0;
//        try {
//            interval = databaseManager().readAutoSyncInterval(bucketName);
//        } catch (NbDatabaseException ex) {
//            log.severe("getAutoSyncInterval() bucketName=" +  bucketName
//                    + " ERR " + ex);
//            throw ex;
//        }
//        return interval;
//    }

    @Override
    public void setSyncScope(String bucketName, NbQuery scope) {

        // 同期範囲が変化するため、前回同期時刻を削除
        removeLastSyncTime(bucketName);

        //同期範囲を設定
        mSyncManager.setSyncScope(bucketName, scope);
    }

    protected void removeLastSyncTime(String bucketName) {
        try {
            // 同期範囲が変化するため、前回同期時刻を削除
            // バケット情報取得
            NbBucketEntity bucketEntity = databaseManager().readBucket(bucketName, false);

            if (bucketEntity != null) {
                log.finest("removeLastSyncTime() bucketName={0} delete lastSyncTime()", bucketName);
                //前回同期時刻を削除
                databaseManager().removeLastSyncTime(bucketName);
                // サーバPull時刻を削除
                databaseManager().removeLatestPullServerTime(bucketName);
            }
        } catch (NbDatabaseException e) {
            //e.printStackTrace();
            log.warning("removeLastSyncTime: {0}", e.getMessage());
            //キャッシュは上位にエラーを通知しないので処理不要
        }
    }

    @Override
    public NbQuery getSyncScope(String bucketName) {
        return mSyncManager.getSyncScope(bucketName);
    }

    @Override
    public Map<String, NbQuery> getSyncScope() {
        return mSyncManager.getSyncScope();
    }

    @Override
    public void removeSyncScope(String bucketName) {
        // 同期範囲が変化するため、前回同期時刻を削除
        removeLastSyncTime(bucketName);

        mSyncManager.removeSyncScope(bucketName);
    }

    @Override
    public void registerSyncEventListener(String bucketName, NbObjectSyncEventListener listener) {
        mSyncManager.registerSyncEventListener(bucketName, listener);
    }

    @Override
    public void unregisterSyncEventListener(String bucketName, NbObjectSyncEventListener listener) {
        mSyncManager.unregisterSyncEventListener(bucketName, listener);
    }

    @Override
    public void notifyConflict(String bucketName, NbObject client,
            NbObject server) {
        mSyncManager.notifyConflict(bucketName, client, server);
    }

    @Override
    public NbOfflineResult setIndexToLocalData(@NonNull Map<String, NbIndexType> indexKeys, @NonNull String bucketName) {
        log.fine("setIndexToLocalData() <start>"
                + "indexKeys=" + indexKeys + " bucketName=" + bucketName);
        NbOfflineResult container = new NbOfflineResult();

        try {
            databaseManager().setIndex(bucketName, indexKeys);
        } catch (NbDatabaseException e) {
            log.severe("setIndexToLocalData() <end>"
                    + " ERR container=" + container);
            container.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);
            return container;
        }

        container.setStatusCode(NbStatus.OK);
        log.fine("setIndexToLocalData() <end>");
        return container;
    }

    @Override
    public Map<String, NbIndexType> getIndexFromLocalData(String bucketName) {
        log.fine("getIndexFromLocalData() <start>");
        Map<String, NbIndexType> result = new HashMap<>();

        try {
            result = databaseManager().getIndex(bucketName);
        } catch (NbDatabaseException e) {
            log.severe("getIndexFromLocalData() <end>"
                    + " ERR " + e);
            throw new IllegalStateException("getIndexFromLocalData() db select error.");
        }

        log.fine("getIndexFromLocalData() <end>");
        return result;
    }

    /**
     * フィールド名に"$"が無いか探す。<br>
     * 見つかればtrueを返す。
     * @param check チェックする JSON Object
     * @return 見つかったら true
     */
    private boolean findUpdateOperators(NbJSONObject check) {
        boolean result = false;
        if (check == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : check.entrySet()) {
            String key = entry.getKey();
            result = analyzeUpdateOperators(result, entry, key);
            if (result) break;
        }
        return result;
    }

    private boolean analyzeUpdateOperators(boolean result, Map.Entry<String, Object> entry, String key) {
        if (key.matches(OPERATOR_REGEX)) {
            result = true;
        }
        else if (entry.getValue() instanceof Map) {
            result = findUpdateOperators((NbJSONObject)entry.getValue());
        }
        else if (entry.getValue() instanceof List) {
            List<Object> list = (List<Object>)entry.getValue();
            for (Object obj : list) {
                if (obj instanceof Map) {
                    result = findUpdateOperators((NbJSONObject)obj);
                }
                if (result) break;
            }
        }
        return result;
    }

    private boolean tryStartSync(){
        //処理状態更新
        synchronized (sLock) {
            //CRUD中は待ち
            while(processState.isCrud()) {
                try {
                    sLock.wait();
                }
                catch (InterruptedException ie) {
                    //nothing to do
                }
            }

            //同期中はエラー
            if(processState.isSyncing()) {
                sLock.notifyAll();
                return false;
            }

            processState.startSync();
            sLock.notifyAll();
        }

        return true;
    }

    private void endSync(){
        //処理状態更新
        synchronized (sLock) {
            processState.endSync();
            sLock.notifyAll();
        }
    }

    private boolean tryStartCrud(){
        //処理状態更新
        synchronized (sLock) {
            //CRUD中は待ち
            while(processState.isCrud()) {
                try {
                    sLock.wait();
                }
                catch (InterruptedException ie) {
                    //nothing to do
                }
            }

            //同期中はエラー
            if(processState.isSyncing()) {
                sLock.notifyAll();
                return false;
            }

            processState.startCrud();
            sLock.notifyAll();
        }

        return true;
    }

    private void endCrud(){
        //処理状態更新
        synchronized (sLock) {
            processState.endCrud();
            sLock.notifyAll();
        }
    }
}
