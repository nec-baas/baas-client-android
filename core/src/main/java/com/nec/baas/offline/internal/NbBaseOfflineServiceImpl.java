/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.json.*;
import com.nec.baas.offline.*;
import com.nec.baas.util.*;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * オブジェクト / ファイル オフラインサービス共通処理
 */
public abstract class NbBaseOfflineServiceImpl implements NbBaseOfflineService {
    private NbLogger log = NbLogger.getLogger(NbBaseOfflineServiceImpl.class);

    private NbOfflineService mOfflineService;

    protected NbBaseOfflineServiceImpl(@NonNull NbOfflineService offlineService) {

        mOfflineService = offlineService;
    }

    protected abstract boolean isFileService();

    protected NbDatabaseManager databaseManager() {
        return mOfflineService.databaseManager();
    }

    protected NbLoginOfflineService loginService() {
        return mOfflineService.loginService();
    }

    protected SystemManager systemManager() {
        return mOfflineService.systemManager();
    }

    @Override
    public int saveBucketCache(String bucketName, String json, boolean isChecked) {
        if (bucketName == null || json == null) return NbStatus.REQUEST_PARAMETER_ERROR;

        NbBucketEntity readData = null;
        try {
            readData = databaseManager().readBucket(bucketName, isFileService());
        } catch (NbDatabaseException e) {
            return NbStatus.INTERNAL_SERVER_ERROR;
        }

        NbBucketEntity saveData = new NbBucketEntity();
        NbJSONObject jsonObj = NbJSONParser.parse(json);
        if (jsonObj == null) {
            return NbStatus.REQUEST_PARAMETER_ERROR;
        }
        NbAcl acl = NbOfflineUtil.makeAcl(jsonObj.getJSONObject(NbKey.ACL));
        saveData.setAcl(acl);
        NbContentAcl contentAcl = NbOfflineUtil.makeContentAcl(jsonObj.getJSONObject(NbKey.CONTENT_ACL));
        saveData.setContentAcl(contentAcl);
        saveData.setBucketName(bucketName);
        saveData.setJsonData(json);
        NbBucketMode bucketMode = NbBucketMode.fromObject(jsonObj.get(NbKey.BUCKET_MODE));

        long createCount = 0;
        if (readData == null) {
            saveData.setPolicy(NbConflictResolvePolicy.SERVER);
            saveData.setBucketMode(bucketMode);
            try {
                createCount = databaseManager().createBucket(bucketName, saveData, isFileService());
            } catch (NbDatabaseException e) {
                // NbStatus.INTERNAL_SERVER_ERRORが返る
            }
        } else {
            if (bucketMode == readData.getBucketMode()) {
                // モードが一致する場合
                if (isChecked && !isBucketUpdatable(readData, acl, contentAcl)) {
                    // 権限がない場合
                    return NbStatus.FORBIDDEN;
                } else {
                    saveData.setPolicy(readData.getPolicy());
                    saveData.setBucketMode(bucketMode);
                    try {
                        createCount = databaseManager().updateBucket(bucketName, saveData, isFileService());
                    } catch (NbDatabaseException e) {
                        //　NbStatus.INTERNAL_SERVER_ERRORが返る
                    }
                }
            }
        }

        if (createCount == 0 || createCount == NbDatabaseManager.INSERT_ERROR_CODE) {
            return NbStatus.INTERNAL_SERVER_ERROR;
        }

        return NbStatus.OK;
    }

    @Override
    public boolean saveBucketCache(String bucketName, String json) {
        int result = saveBucketCache(bucketName, json, false);

        return NbStatus.isSuccessful(result);
    }

    @Override
    public int removeBucketCache(String bucketName, boolean isChecked) {
        if (bucketName == null) return NbStatus.REQUEST_PARAMETER_ERROR;

        if (isChecked) {
            // バケットが存在するかをチェック
            NbBucketEntity readData = null;
            try {
                readData = databaseManager().readBucket(bucketName, isFileService());
            } catch (NbDatabaseException e) {
                return NbStatus.INTERNAL_SERVER_ERROR;
            }

            // バケットが存在しない場合
            if (readData == null) return NbStatus.NOT_FOUND;

            // delete権限がない場合
            if (!isBucketDeletable(readData)) return NbStatus.FORBIDDEN;

            // バケット配下にデータが存在する場合
            boolean isDataExists = false;
            try {
                isDataExists = databaseManager().isDataExistsInBucket(bucketName, isFileService());
            } catch (NbDatabaseException e) {
                // 該当するオブジェクトテーブルが存在しない場合もこのルートに入る
                // データが存在しないものとみなす
            }
            if (isDataExists) return NbStatus.CONFLICT;
        }

        boolean isSuccess = true;
        try {
            mOfflineService.beginTransaction();

            databaseManager().deleteBucket(bucketName, isFileService());
            //前回同期時刻を削除
            databaseManager().removeLastSyncTime(bucketName);
            //前回同期時刻を削除
            databaseManager().removeLatestPullServerTime(bucketName);
        } catch (NbDatabaseException e) {
            isSuccess = false;
        } finally {
            mOfflineService.endTransaction();
        }

        if (isChecked && !isSuccess) return NbStatus.INTERNAL_SERVER_ERROR;

        return NbStatus.OK;
    }

    @Override
    public void removeBucketCache(String bucketName) {
        removeBucketCache(bucketName, false);
    }

    @Override
    public NbOfflineResult readLocalBucket(String bucketName) {
        log.fine("readLocalBucket() <start>"
                + " bucketName=" + bucketName);
        NbOfflineResult container = new NbOfflineResult();
        NbBucketEntity info = null;
        try {
            info =  databaseManager().readBucket(bucketName, isFileService());
        } catch (NbDatabaseException ex) {
            log.severe("readLocalBucket() <end>"
                    + " ERR readBucket() NbDatabaseException");
            container.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);
            return container;
        }

        if (info != null) {
            //バケットのACLチェック
            if ( !isBucketReadable(info) ) {
                container.setStatusCode(NbStatus.FORBIDDEN);
                return container;
            }

            container.setStatusCode(NbStatus.OK);
            container.setJsonData(NbJSONParser.parse(info.getJsonData()));
        } else {
            container.setStatusCode(NbStatus.NOT_FOUND);
        }
        log.fine("readLocalBucket() <end>"
                + " container=" + container);
        return container;
    }

    @Override
    public NbOfflineResult readLocalBucketList() {
        log.fine("readLocalBucketList() <start>");
        NbOfflineResult container = new NbOfflineResult();
        List<NbBucketEntity> readInfo = null;
        try {
            readInfo = databaseManager().readBucketList(isFileService());
        } catch (NbDatabaseException ex) {
            log.severe("readLocalBucketList() <end> ERR readBucketList() NbDatabaseException");
            container.setStatusCode(NbStatus.INTERNAL_SERVER_ERROR);
            return container;
        }

        if (readInfo != null) {
            NbJSONArray<NbJSONObject> jsonMap = new NbJSONArray<>();
            for (NbBucketEntity info : readInfo) {
                //バケットのACLチェック
                if ( isBucketReadable(info) ) {
                    NbJSONObject json = NbJSONParser.parse(info.getJsonData());
                    if (json != null) {
                        jsonMap.add(json);
                    } else {
                        log.warning("Broken JSON");
                    }
                }
            }

            //ACLチェックで全て除外されたらNOT_FOUND
            if (jsonMap.isEmpty()) {
                container.setStatusCode(NbStatus.NOT_FOUND);
            } else {
                NbJSONObject resultMap = new NbJSONObject();
                resultMap.put(NbKey.RESULTS, jsonMap);
                container.setJsonData(resultMap);
                container.setStatusCode(NbStatus.OK);
            }

        } else {
            container.setStatusCode(NbStatus.NOT_FOUND);
        }
        log.fine("readLocalBucketList() <end>"
                + " container=" + container);
        return container;
    }

   @Override
   public boolean isBucketExists(String bucketName, NbBucketMode bucketMode) {
       return databaseManager().isBucketExists(bucketName, bucketMode, isFileService());
   }

    //---------------------------------------------------------------------------------------------
    // ACLチェック
    //---------------------------------------------------------------------------------------------

    /**
     * バケット内データ作成時のACLチェックを行う。<br>
     * ＜方針＞<br>
     * バケットcontentACLのcreate権限が必要<br>
     * @param bucketName 作成先のバケット名
     * @return trueの場合アクセス可 falseの場合アクセス不可
     */
    protected boolean isBucketContentCreatable(String bucketName) {
        log.fine("isBucketContentCreatable() <start>"
                + " bucketName=" + bucketName);

        NbContentAcl cacheBucketContentAcl = readContentAcl(bucketName);
        if (cacheBucketContentAcl == null) {
            throw new IllegalArgumentException();
        }

        //バケットContentACLチェック
        if (!isContentAclAllowed(cacheBucketContentAcl, NbBaseAcl.KEY_CREATE)) {
            //該当無しのためアクセス不可
            log.finer("isBucketContentCreatable() <end> ERR buket ContentAcl check");
            return false;
        }

        log.fine("isBucketContentCreatable() <end>"
                + " object Acl access ok");
        return true;
    }

    private NbContentAcl readContentAcl(String bucketName) {
        NbBucketEntity cacheBucket;

        //キャッシュ情報取得
        try {
            cacheBucket = databaseManager().readBucket(bucketName, isFileService());
        } catch (NbDatabaseException ex) {
            log.finer("readContentAcl() <end> ERR db access");
            return null;
        }
        //作成先バケットが存在しない場合は、呼び出し元が不正なのでアクセスさせないとする。
        if ( (cacheBucket == null) ) {
            log.finer("readContentAcl() <end> ERR cacheBucket=null");
            return null;
        }

        return cacheBucket.getContentAcl();
    }

    /**
     * データ更新/参照時のACLチェックを行う。<br>
     * ＜方針＞<br>
     * バケットcontentACLと対象オブジェクトのread権限が必要<br>
     * @param bucketName 所属するバケット名
     * @param cacheData 対象のデータ（更新の場合は元データ）
     * @param checkPermission 対象データの属性（ACL変更チェック用）
     * @return trueの場合アクセス可 falseの場合アクセス不可
     */
    protected boolean isBucketContentReadUpdatable(String bucketName, NbAclEntity cacheData,
                                                 String checkPermission) {
        NbContentAcl cacheBucketContentAcl = readContentAcl(bucketName);

        //対象データが存在しない場合は、呼び出し元が不正なのでアクセスさせないとする。
        if ((cacheData == null) || (cacheBucketContentAcl == null) ) {
            log.fine("isBucketContentReadUpdatable() <end> ERR data or bucket is null.");
            return false;
        }

        String cacheDataPermission = cacheData.getAclString();

        NbJSONObject aclJson = NbJSONParser.parse(cacheDataPermission);
        NbAcl cacheDataAcl = NbOfflineUtil.makeAcl(aclJson);
        String checkKey = NbBaseAcl.KEY_READ;

        log.fine("isBucketContentReadUpdatable()"
                + " checkPermission=" + checkPermission + " cachDataPermission=" + cacheDataPermission);

        //属性変更をチェックする
        if (checkPermission != null) {
            //Updateなのでキーを変更
            checkKey = NbBaseAcl.KEY_UPDATE;
            //ACL変更を検出し、権限が無い場合はエラー
            if (!isPermitAclChange(checkPermission, cacheDataPermission, cacheDataAcl)) {
                log.fine("isBucketContentReadUpdatable() <end> ERR acl change check");
                return false;
            }
        }

        //バケットContentACLチェック
        if (!isContentAclAllowed(cacheBucketContentAcl, checkKey)) {
            log.fine("isBucketContentReadUpdatable() <end> ERR bucket ContentAcl check");
            return false;
        }

        //オブジェクトACLチェック
        if (!isAclAllowed(cacheDataAcl, checkKey)) {
            log.fine("isBucketContentReadUpdatable() <end> ERR  Acl check");
            return false;
        }

        //該当無しのためアクセス不可
        return true;
    }

    private boolean isPermitAclChange(String checkPermission, String cacheDataPermission, NbAcl cacheDataAcl) {
        //変更を検知
        if (! NbOfflineUtil.isEqualAclPermission(cacheDataPermission, checkPermission)) {
            //オブジェクトACLチェック(ACL変更時はAdminかOwner権限があればよい)
            if ( !isAclAllowed(cacheDataAcl, NbBaseAcl.KEY_OWNER) &&
                    !isAclAllowed(cacheDataAcl, NbBaseAcl.KEY_ADMIN) ) {
                //どちらの権限もないのでエラー
                log.finer("isPermitAclChange() <end> ERR attribute change object Acl check");
                return false;
            }
        }
        return true;
    }

    /**
     * オブジェクト削除時のACLチェックを行う。<br>
     * ＜方針＞<br>
     * バケットcontentACLと対象オブジェクトのdelete権限が必要<br>
     * @param bucketName 所属するバケット名
     * @param cacheData 対象のデータ
     * @return trueの場合アクセス可 falseの場合アクセス不可
     */
    protected boolean isBucketContentDeletable(String bucketName, NbAclEntity cacheData) {
        log.fine("isBucketContentDeletable() <start>"
                + " bucketName=" + bucketName);

        NbContentAcl cacheBucketContentAcl = readContentAcl(bucketName);

        //削除先データが存在しない場合は、呼び出し元が不正なのでアクセスさせないとする。
        if ((cacheData == null) || (cacheBucketContentAcl == null) ) {
            log.finer("isBucketContentDeletable() <end>"
                    + " ERR cacheData=" + cacheData + " contentAcl=" + cacheBucketContentAcl);
            return false;
        }

        String cachDataPermission = cacheData.getAclString();
        NbJSONObject aclJson = NbJSONParser.parse(cachDataPermission);
        NbAcl cacheDataAcl = NbOfflineUtil.makeAcl(aclJson);

        //バケットContentACLチェック
        if (!isContentAclAllowed(cacheBucketContentAcl, NbBaseAcl.KEY_DELETE)) {
            log.finer("isBucketContentDeletable() <end>"
                    + " ERR bucket ContentAcl check");
            return false;
        }

        //オブジェクトACLチェック
        if (!isAclAllowed(cacheDataAcl, NbBaseAcl.KEY_DELETE)) {
            //該当無しのためアクセス不可
            log.finer("isBucketContentDeletable() <end>"
                    + " ERR object Acl check");
            return false;
        }
        log.fine("isBucketContentDeletable() <end>"
                + " object Acl access ok");
        return true;
    }

    /**
     * オブジェクトクエリ時のバケットACLチェックを行う。<br>
     * ＜方針＞<br>
     * バケットcontentACLと対象オブジェクトのread権限が必要<br>
     * @param bucketName 所属するバケット名
     * @return trueの場合アクセス可 falseの場合アクセス不可
     */
    protected boolean isBucketReadable(String bucketName) {
        log.fine("isBucketReadable() <start>"
                + " bucketName=" + bucketName);

        NbContentAcl cacheBucketContentAcl = readContentAcl(bucketName);
        if (cacheBucketContentAcl == null) {
            return false;
        }

        //バケットContentACLチェック
        if (!isContentAclAllowed(cacheBucketContentAcl, NbBaseAcl.KEY_READ)) {
            //該当無しのためアクセス不可
            log.finer("isBucketReadable() <end>"
                    + " ERR bucket ContentAcl check");
            return false;
        }

        return true;
    }

    /**
     * オブジェクトクエリ時のオブジェクトACLチェックを行う。<br>
     * ＜方針＞<br>
     * バケットcontentACLと対象オブジェクトのread権限が必要<br>
     * @param cacheData 対象のデータ
     * @return trueの場合アクセス可 falseの場合アクセス不可
     */
    protected boolean isObjectReadable(NbAclEntity cacheData) {

        //クエリ対象データがない場合は、呼び出し元が不正なのでアクセスさせないとする。
        if (cacheData == null) {
            log.finer("isBucketReadable() <end> param Error return false.");
            return false;
        }

        String cachDataPermission = cacheData.getAclString();
        NbJSONObject aclJson = NbJSONParser.parse(cachDataPermission);
        NbAcl cacheDataAcl = NbOfflineUtil.makeAcl(aclJson);

        //オブジェクトACLチェック
        if (!isAclAllowed(cacheDataAcl, NbBaseAcl.KEY_READ)) {
            //該当無しのためアクセス不可
            log.finer("isBucketReadable() <end> return false. object Acl check");
            return false;
        }

        return true;
    }



    /**
     * バケット取得時のACLチェックを行う。<br>
     * ＜方針＞<br>
     * 対象バケットのread権限が必要<br>
     * @param cacheBucket 対象のバケット
     * @return trueの場合アクセス可 falseの場合アクセス不可
     */
    private boolean isBucketReadable(NbBucketEntity cacheBucket) {
        log.fine("isBucketReadable() <start>");

        //取得先バケットない場合は、呼び出し元が不正なのでアクセスさせないとする。
        if (cacheBucket == null ) {
            log.finer("isBucketReadable() <end>"
                    + " ERR cacheBucket=" + cacheBucket);
            return false;
        }

        log.finer("isBucketReadable() getBucketName()="
                + cacheBucket.getBucketName());

        NbAcl cacheBucketAcl = cacheBucket.getAcl();

        //オブジェクトACLチェック
        if (!isBucketAclAllowed(cacheBucketAcl, NbBaseAcl.KEY_READ)) {
            //該当無しのためアクセス不可
            log.finer("isBucketReadable() <end>"
                    + " ERR bucket Acl check");
            return false;
        }

        return true;
    }

    /**
     * バケット更新時のACLチェックを行う。<br>
     * ＜方針＞<br>
     * ACL/contentACL以外を変更する際には、対象バケットのupdate権限が必要<br>
     * ACL/contentACLを変更する際には、対象バケットのadmin権限が必要<br>
     * @param cacheBucket 対象のバケット
     * @param updateAcl 更新予定のACL
     * @param updateContentAcl 更新予定のcontentACL
     * @return trueの場合アクセス可 falseの場合アクセス不可
     */
    private boolean isBucketUpdatable(NbBucketEntity cacheBucket, NbAcl updateAcl, NbContentAcl updateContentAcl) {
        // 取得先バケットない場合は、呼び出し元が不正なのでアクセスさせないとする。
        if (cacheBucket == null ) {
            log.finer("isBucketUpdatable() ERR cacheBucket is null.");
            return false;
        }

        NbAcl cacheBucketAcl = cacheBucket.getAcl();
        NbContentAcl cacheBucketContentAcl = cacheBucket.getContentAcl();

        if ((updateAcl != null && !updateAcl.equals(cacheBucketAcl))
                || (updateContentAcl != null && !updateContentAcl.equals(cacheBucketContentAcl))) {
            // バケットのACL/contentACLの変更か
            if (isBucketAclAllowed(cacheBucketAcl, NbBaseAcl.KEY_ADMIN)) {
                // admin権限チェック
                return true;
            }
        } else {
            if (isBucketAclAllowed(cacheBucketAcl, NbBaseAcl.KEY_WRITE) || isBucketAclAllowed(cacheBucketAcl, NbBaseAcl.KEY_UPDATE)) {
                // update権限チェック
                return true;
            }
        }

        return false;
    }

    /**
     * バケット削除時のACLチェックを行う。<br>
     * ＜方針＞<br>
     * 対象バケットのdelete権限が必要<br>
     * @param cacheBucket 対象のバケット
     * @return trueの場合アクセス可 falseの場合アクセス不可
     */
    private boolean isBucketDeletable(NbBucketEntity cacheBucket) {
        // 取得先バケットない場合は、呼び出し元が不正なのでアクセスさせないとする。
        if (cacheBucket == null ) {
            log.finer("isBucketDeletable() ERR cacheBucket is null.");
            return false;
        }

        NbAcl cacheBucketAcl = cacheBucket.getAcl();

        return isBucketAclAllowed(cacheBucketAcl, NbBaseAcl.KEY_WRITE) || isBucketAclAllowed(cacheBucketAcl, NbBaseAcl.KEY_DELETE);
    }

    /**
     * バケットのACLチェックを行う。<br>
     * マスタキー（ACL要否チェック）の判断は行わない。<br>
     * @param acl　 ACL
     * @param attr　属性：対象("owner","admin","r","w","c","u","d")
     * @return trueの場合アクセス可 falseの場合アクセス不可
     */
    private boolean isBucketAclAllowed(NbAcl acl, String attr) {

        //指定した属性が不明の場合はアクセスさせない
        if ( (attr == null) || (attr.isEmpty())) {
            log.finer("isBucketAclAllowed() return false, attr=" + attr);
            return false;
        }

        //ACLがnullのケースはありえないのでアクセスさせない
        if (acl == null) {
            log.finer("isBucketAclAllowed() return false, acl=" + acl);
            return false;
        }

        //ユーザ情報取得
        NbJSONObject userInfo = loginService().getLoginUserInfo();

        log.finer("isBucketAclAllowed() attr=" + attr + " userInfo=" + userInfo);

        //owenr権限はowner自身でしか変更できないため、他と分けてチェックする。
        if ( attr.equals(NbBaseAcl.KEY_OWNER) ) {
            if (!isAclOwnerMatch(userInfo, acl) ) {
                return false;
            }
        } else {
            //owner権限にはadmin権限も含まれているため、owner権限を保有している場合はadmin権限へのアクセス可
            if ( attr.equals(NbBaseAcl.KEY_ADMIN) ) {
                //ownerとowner以外のACLチェック
                if ( !isAclOwnerMatch(userInfo, acl) && (!isAclAllowed(userInfo, acl, attr)) ) {
                    return false;
                }
                //owner権限が無い場合、adminは他の権限と同様の判定処理を行う
            } else {
                //owner以外のACLチェック
                if ( !isAclAllowed(userInfo, acl, attr) ) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * バケットのContentACLチェックを行う。<br>
     * マスタキー（ACL要否チェック）の判断は行わない。<br>
     * @param contentAcl Content ACL
     * @param attr 属性：対象("r","w","c","u","d")
     * @return trueの場合アクセス可 falseの場合アクセス不可
     */
    private boolean isContentAclAllowed(NbContentAcl contentAcl, String attr) {

        //指定した属性が不明の場合はアクセスさせない
        if ((attr == null) || (attr.isEmpty())) {
            return false;
        }

        //ContentACLがnullのケースはありえないのでアクセスさせない
        if (contentAcl == null ) {
            return false;
        }

        //ユーザ情報取得
        NbJSONObject userInfo = loginService().getLoginUserInfo();

        log.finer("isContentAclAllowed() attr=" + attr);

        //owner以外のACLチェック
        if (!isAclAllowed(userInfo, contentAcl, attr) ) {
            //該当無しのためアクセス不可
            return false;
        }

        return true;
    }

    /**
     * オブジェクトのACLチェックを行う。<br>
     * マスタキー（ACL要否チェック）の判断は行わない。<br>
     * @param acl　 ACL
     * @param attr　属性：対象("owner","admin","r","w","c","u","d")
     * @return trueの場合アクセス可 falseの場合アクセス不可
     */
    private boolean isAclAllowed(NbAcl acl, String attr) {

        //指定した属性が不明の場合はアクセスさせない
        if ( (attr == null) || (attr.isEmpty())) {
            log.finer("isAclAllowed() attr err return false");
            return false;
        }

        //ACLがnullのケースはありえないのでアクセスさせない
        if ( acl == null ) {
            log.finer("isAclAllowed() acl err return false");
            return false;
        }

        //ユーザ情報取得
        NbJSONObject userInfo = loginService().getLoginUserInfo();

        //オーナ権限がある場合は、属性に限らずアクセス可
        if (!isAclOwnerMatch(userInfo, acl) ) {

            //owenr権限はowner自身でしか変更できないため、オーナ権限が無い場合はアクセス不可
            if ( attr.equals(NbBaseAcl.KEY_OWNER) ) {
                log.finer("isAclAllowed() attr owner err"
                        + " return false");
                return false;
            }

            //owner以外のACLチェック
            if ( !isAclAllowed(userInfo, acl, attr) ) {
                //該当無しのためアクセス不可
                log.finer("isAclAllowed() attr acl unit err"
                        + " return false");
                return false;
            }
        }
        return true;
    }

    private boolean isAclOwnerMatch(NbJSONObject userInfo, NbAcl acl) {
        String ownerId = acl.getOwner();

        //オーナ情報が無いケースはありえないのでアクセスさせない
        if (ownerId == null) {
            log.finer("isAclOwnerMatch() return false, ownerId=" + ownerId);
            return false;
        }

        String userId = null;

        //ユーザID取得
        if (userInfo != null) {
            userId = userInfo.getString(NbKey.ID);
        }

        //ユーザID＝オーナIDならアクセス可
        if (userId != null && userId.equals(ownerId)) {
            return true;
        }

        log.finer("isAclOwnerMatch() return false, ownerId=" + ownerId + " userId=" + userId);
        return false;
    }

    private boolean isAclAllowed(NbJSONObject userInfo, NbBaseAcl acl, String attr) {
        List<String> list;

        //属性に対応したACLデータを取得
        list = getAclValue(attr, acl);

        //"c","u","d"の場合は包括する"w"の権限もチェックするので"w"のリストを加味する。
        List<String> writeList = null;
        switch (attr) {
            case NbBaseAcl.KEY_CREATE:
            case NbBaseAcl.KEY_UPDATE:
            case NbBaseAcl.KEY_DELETE:
                writeList = getAclValue(NbBaseAcl.KEY_WRITE, acl);
                break;

            default :
                break;
        }

        String userId = null;
        //ユーザID取得
        if (userInfo != null) {
            userId = userInfo.getString(NbKey.ID);
        }
        boolean isLogin = loginService().isLoggedIn();

        for (int i = 0; i < 2; i++) {
            // 一周目で list, 二周目で writeList をチェックする
            if (i == 1) {
                list = writeList;
            }
            if (list == null) continue;

            //log.finer("isAclAllowed() list=" + list);

            // anonymous許可なら無条件でアクセス可
            if (list.contains(NbConsts.GROUP_NAME_ANONYMOUS)) {
                return true;
            }

            // anonymous不許可ならログイン必須なのでuserIdチェック
            if (isLogin) {
                //authenticatedならログイン済みでアクセス可
                if (list.contains(NbConsts.GROUP_NAME_AUTHENTICATED)) {
                    return true;
                }
                if (isUserOrGroupInList(list, userId)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isUserOrGroupInList(List<String> list, String userId) {
        //ユーザIDが含まれているならアクセス可
        if (list.contains(userId)) {
            return true;
        }

        //所属グループ一覧取得
        List<String> groupList = loginService().getGroupList();

        //所属グループが含まれているならアクセス可
        for (String group : groupList) {
            //ACLに記載されいているグループ名には"g:"が付与されているため、
            //比較先の書式を合わせてから比較する。
            if (!group.startsWith("g:")) {
                group = "g:" + group;
            }
            if (list.contains(group)) {
                return true;
            }
        }

        //該当データ無し
        //log.finer("isAclAllowed() return false, attr=" + attr);
        return false;
    }

    private List<String> getAclValue(String attr, NbBaseAcl acl) {
        switch (attr) {
            case NbBaseAcl.KEY_READ:
                return acl.getRead();

            case NbBaseAcl.KEY_WRITE:
                return acl.getWrite();

            case NbBaseAcl.KEY_CREATE:
                return acl.getCreate();

            case NbBaseAcl.KEY_UPDATE:
                return acl.getUpdate();

            case NbBaseAcl.KEY_DELETE:
                return acl.getDelete();

            case NbBaseAcl.KEY_OWNER:
                if (!(acl instanceof NbAcl)) {
                    return null;
                }
                List<String> list = new ArrayList<>();
                list.add(((NbAcl)acl).getOwner());
                return list;

            case NbBaseAcl.KEY_ADMIN:
                if (!(acl instanceof NbAcl)) {
                    return null;
                }
                return ((NbAcl)acl).getAdmin();

            default:
                return null;
        }
    }
}
