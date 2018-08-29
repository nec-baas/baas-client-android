/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.user;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.json.*;
import com.nec.baas.user.internal.*;
import com.nec.baas.util.*;

import java.util.ArrayList;
import java.util.List;

import lombok.NonNull;
import okhttp3.Request;
import okhttp3.Response;

/**
 * グループ情報の管理を行うクラス。
 *
 * <p><strong>本クラスのインスタンスはスレッドセーフではない。</strong></p>
 * @since 1.0
 */
public class NbGroup {
    private static final NbLogger log = NbLogger.getLogger(NbGroup.class);

    /*package*/ static NbServiceImpl sNebulaService;

    /*package*/ NbServiceImpl mService;

    /*package*/ NbGroupEntity mGroupEntity = new NbGroupEntity();

    protected static final String GROUP_URL = "/groups";

    private static final String[] GROUP_RESERVED_NAMES = {"authenticated", "anonymous"};

    // 内部 API
    @Deprecated
    protected NbGroup() {
        this(null, null);
    }

    /**
     * グループインスタンスを新規生成する(マルチテナント非対応)
     * @param groupName グループ名
     */
    public NbGroup(String groupName) {
        this(null, groupName);
    }

    /**
     * グループインスタンスを新規生成する
     * @param service サービス
     * @param groupName グループ名
     */
    public NbGroup(NbService service, String groupName) {
        if (service == null) {
            ensureNebulaService();
            mService = sNebulaService;
        } else {
            mService = (NbServiceImpl)service;
        }
        if (groupName != null) {
            setGroupName(groupName);
        }
    }

    private static void executeRequest(NbServiceImpl service, Request request, NbRestResponseHandler handler) {
        service.createRestExecutor().executeRequest(request, handler);
    }

    /**
     * グループの保存を行う。<p>
     *
     * @deprecated {@link #save(NbCallback)} で置き換え
     */
    @Deprecated
    public void save(final NbGroupsCallback callback) {
        save(NbGroupsCallbackWrapper.wrap(callback));
    }

    /**
     * グループの保存を行う。
     * <p>
     * グループ名に使用できる文字は半角英数字とアンダーバーのみとなる。<br>
     * グループ名の文字数上限は100文字となる。<br>
     * 但し、authenticated、anonymousは予約語のため使用できない。<br>
     * グループの作成にはGROUPSバケットに対するcreate権限が必要となる。<br>
     * グループの変更にはGROUPSバケットおよび、対象グループのupdate権限が必要となる。<br>
     * また、ACLの変更を行う場合はオーナ権限もしくはadmin権限も必要となる。
     * @param callback 保存したグループを受け取るコールバック。
     */
    public void save(@NonNull final NbCallback<NbGroup> callback) {
        log.finest("save() <start>");

        if (!NbUtil.checkOnline(mService.getOfflineService(), "[NbGroup]", callback)) {
            log.severe("save() <end> ERR");
            return;
        }
        String errReason = null;
        if (getGroups() == null) {
            errReason = "Null groups";
        } else if (getUsers() == null) {
            errReason = "Null users";
        } else if (!isValidGroupName(getGroupName())) {
            errReason = "Bad group name : " + getGroupName();
        }
        if (errReason != null) {
            log.severe("save() <end> ERR param invalid: " + errReason);
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo(errReason));
            return;
        }

        //リクエスト作成
        NbJSONObject bodyJson = new NbJSONObject();
        bodyJson.put(NbKey.USERS, getUsers());
        bodyJson.put(NbKey.GROUPS, getGroups());
        if (getAcl() != null) {
            bodyJson.put(NbKey.ACL, getAcl().toJsonObject());
        }

        Request request;
        if (getETag() != null) {
            request = mService.getHttpRequestFactory()
                    .put(GROUP_URL).addPathComponent(getGroupName())
                    .param(NbKey.ETAG, getETag())
                    .body(bodyJson)
                    .build();
        } else {
            request = mService.getHttpRequestFactory()
                    .put(GROUP_URL).addPathComponent(getGroupName())
                    .body(bodyJson)
                    .build();
        }

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbGroup.save()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                mGroupEntity.set(body);
                callback.onSuccess(NbGroup.this);
            }
        };
        execSave(request, handler);

        log.finest("save() <end>");
    }

    protected void execSave(Request request, NbRestResponseHandler handler) {
        executeRequest(mService, request, handler);
    }

    private static class NbGroupsCallbackWrapper extends NbListToSingleCallbackWrapper<NbGroup> implements NbCallback<NbGroup> {
        public static NbGroupsCallbackWrapper wrap(NbGroupsCallback callback) {
            if (callback == null) return null;
            return new NbGroupsCallbackWrapper(callback);
        }

        private NbGroupsCallbackWrapper(NbGroupsCallback callback) {
            super(callback);
        }
    }

    /**
     * グループ情報の一覧を取得する(マルチテナント非対応)。<br>
     * Groupバケットおよび対象グループのread権限が必要。
     * @param callback グループ一覧を取得するコールバック
     * @see com.nec.baas.user.NbGroupsCallback
     */
    public static void query(final NbCallback<List<NbGroup>> callback) {
        ensureNebulaService();
        query(sNebulaService, callback);
    }

    /**
     * グループ情報の一覧を取得する(マルチテナント対応)。
     */
    public static void query(@NonNull final NbService service, @NonNull final NbCallback<List<NbGroup>> callback) {
        log.finest("query() <start>");
        final NbServiceImpl _service = (NbServiceImpl)service;

        if (!NbUtil.checkOnline(_service.getOfflineService(), "[NbGroup]", callback)) {
            log.severe("query() <end> ERR");
            return;
        }
        //リクエスト作成
        Request request = _service.getHttpRequestFactory().get(GROUP_URL).build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbGroup.query") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                //グループ情報一覧を返す処理
                List<NbGroup> list = new ArrayList<>();
                NbJSONArray<NbJSONObject> results = body.getJSONArray(NbKey.RESULTS);
                if (results != null) {
                    for (NbJSONObject json : results) {
                        NbGroup group = new NbGroup(_service, null);
                        group.mGroupEntity.set(json);
                        list.add(group);
                    }
                }
                callback.onSuccess(list);
            }
        };
        execQuery(_service, request, handler);

        log.finest("query() <end>");
    }

    protected static void execQuery(NbServiceImpl service, Request request, NbRestResponseHandler handler) {
        executeRequest(service, request, handler);
    }

    /**
     * 指定したグループの情報を取得する(マルチテナント非対応)。
     * <p>
     * @deprecated {@link #getGroup(String, NbCallback)} で置き換え
     * @since 1.0
     */
    @Deprecated
    public static void getGroup(String groupName, final NbGroupsCallback callback) {
        getGroup(groupName, NbGroupsCallbackWrapper.wrap(callback));
    }

    /**
     * 指定したグループの情報を取得する(マルチテナント非対応)。
     * <p>
     * Groupバケットおよび対象グループのread権限が必要。
     * @param groupName グループ情報取得対象のグループ名
     * @param callback グループ情報を取得するコールバック
     * @since 6.5.0
     */
    public static void getGroup(String groupName, final NbCallback<NbGroup> callback) {
        ensureNebulaService();
        getGroup(sNebulaService, groupName, callback);
    }

    /**
     * 指定したグループの情報を取得する(マルチテナント対応)。
     * <p>
     * @deprecated {@link #getGroup(NbService, String, NbCallback)} で置き換え
     * @since 3.0
     */
    @Deprecated
    public static void getGroup(NbService service, String groupName, final NbGroupsCallback callback) {
        getGroup(service, groupName, NbGroupsCallbackWrapper.wrap(callback));
    }

    /**
     * 指定したグループの情報を取得する(マルチテナント非対応)。
     * <p>
     * Groupバケットおよび対象グループのread権限が必要。
     * @param service NbService
     * @param groupName グループ情報取得対象のグループ名
     * @param callback グループ情報を取得するコールバック
     * @since 6.5.0
     */
    public static void getGroup(@NonNull NbService service, @NonNull String groupName, @NonNull final NbCallback<NbGroup> callback) {
        log.finest("getGroup() <start> groupname=" + groupName);
        final NbServiceImpl _service = (NbServiceImpl)service;

        if (!isValidGroupName(groupName)) {
            throw new IllegalArgumentException("groupname is invalid");
        }

        if (!NbUtil.checkOnline(_service.getOfflineService(), "[NbGroup]", callback)) {
            log.severe("getGroup() <end> ERR");
            return;
        }

        //レスポンス作成
        Request request = _service.getHttpRequestFactory().get(GROUP_URL).addPathComponent(groupName).build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbGroup.getGroup") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                //グループ情報を返す処理
                NbGroup group = new NbGroup(_service, null);
                group.mGroupEntity.set(body);

                callback.onSuccess(group);
            }
        };
        execGetGroup(_service, request, handler);

        log.finest("getGroup() <end>");
    }

    protected static void execGetGroup(NbServiceImpl service, Request request, NbRestResponseHandler handler) {
        executeRequest(service, request, handler);
    }

    /**
     * グループの削除を行う。<br>
     * Groupバケットおよび対象グループのdelete権限が必要。
     * @param callback APIの実行結果を取得するコールバック
     * @see NbResultCallback
     */
    public void delete(@NonNull final NbResultCallback callback) {
        log.finest("delete() <start>");

        if (!NbUtil.checkOnline(mService.getOfflineService(), "[NbGroup]", callback)) {
            log.severe("delete() <end> ERR");
            return;
        }
        if (!isValidGroupName(getGroupName())) {
            log.severe("delete() <end> ERR param invalid");
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("Bad groupname."));
            return;
        }
        //リクエスト作成
        Request request;
        if (getETag() != null) {
            request = mService.getHttpRequestFactory()
                    .delete(GROUP_URL).addPathComponent(getGroupName())
                    .param(NbKey.ETAG, getETag())
                    .build();
        } else {
            request = mService.getHttpRequestFactory()
                    .delete(GROUP_URL).addPathComponent(getGroupName())
                    .build();
        }

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbGroup.deleteGroup") {
            @Override
            public void onSuccess(Response response) {
                callback.onSuccess();
            }
        };
        execDelete(request, handler);

        log.finest("delete() <end>");
    }

    protected void execDelete(Request request, NbRestResponseHandler handler) {
        executeRequest(mService, request, handler);
    }

    private synchronized static NbServiceImpl ensureNebulaService() {
        if (sNebulaService == null) {
            sNebulaService = (NbServiceImpl) NbService.getInstance();
        }
        return sNebulaService;
    }

    public synchronized static void __resetNebulaService() {
        sNebulaService = null;
    }

    /**
     * グループに所属するユーザIDの一覧を取得する。
     * @return グループに所属するユーザIDのリスト
     */
    public List<String> getUsers() {
        return mGroupEntity.getUsers();
    }

    /**
     * グループに所属するユーザIDの一覧を設定する。<br>
     * save()を実行するまで設定値は保存されない。
     * @param users グループに所属するユーザIDのリスト
     * @see NbGroup#save(NbCallback)
     */
    public NbGroup setUsers(List<String> users) {
        mGroupEntity.setUsers(users);
        return this;
    }

    /**
     * グループに所属するユーザを追加する。
     * ユーザの指定にはユーザIDを用いる (ユーザ名ではない)
     * @param userId グループに追加するユーザID
     * @see NbGroup#save(NbCallback)
     */
    public NbGroup addUser(String userId) {
        mGroupEntity.addUser(userId);
        return this;
    }

    /**
     * グループに所属するユーザを追加する。
     * @param user ユーザ
     * @see NbGroup#save(NbCallback)
     */
    public NbGroup addUser(NbUser user) {
        addUser(user.getUserId());
        return this;
    }

    /**
     * グループに所属するユーザを削除する。
     * ユーザの指定にはユーザIDを用いる (ユーザ名ではない)
     * @param userId グループから削除するユーザID
     */
    public NbGroup removeUser(String userId) {
        mGroupEntity.removeUser(userId);
        return this;
    }
    /**
     * グループに所属するユーザを削除する。
     * @param user グループから削除するユーザ
     */
    public NbGroup removeUser(NbUser user) {
        removeUser(user.getUserId());
        return this;
    }

    /**
     * グループに所属するグループの一覧を取得する。
     * @return グループに所属するグループ名のリスト
     */
    public List<String> getGroups() {
        return mGroupEntity.getGroups();
    }

    /**
     * グループに所属するグループの一覧を設定する。<br>
     * @param groups グループに所属するグループ名のリスト
     * @see NbGroup#save(NbCallback)
     */
    public NbGroup setGroups(List<String> groups) {
        mGroupEntity.setGroups(groups);
        return this;
    }

    /**
     * グループに所属するグループを追加する。
     * @param group グループに追加するグループ名
     * @see NbGroup#save(NbCallback)
     */
    public NbGroup addGroup(String group) {
        mGroupEntity.addGroup(group);
        return this;
    }

    /**
     * グループに所属するグループを追加する。
     * @param group グループに追加するグループ
     * @see NbGroup#save(NbCallback)
     */
    public NbGroup addGroup(NbGroup group) {
        addGroup(group.getGroupName());
        return this;
    }

    /**
     * グループに所属するグループを削除する。
     * @param group グループから削除するグループ名
     * @see NbGroup#save(NbCallback)
     */
    public NbGroup removeGroup(String group) {
        mGroupEntity.removeGroup(group);
        return this;
    }

    /**
     * グループに所属するグループを削除する。
     * @param group グループから削除するグループ
     * @see NbGroup#save(NbCallback)
     */
    public NbGroup removeGroup(NbGroup group) {
        removeGroup(group.getGroupName());
        return this;
    }

    /**
     * グループのACLを取得する。
     * @return グループのACL
     */
    public NbAcl getAcl() {
        return mGroupEntity.getAcl();
    }

    /**
     * グループのACLを設定する。<br>
     * @param acl グループに設定するACL
     * @see NbGroup#save(NbCallback)
     */
    public NbGroup setAcl(NbAcl acl) {
        mGroupEntity.setAcl(acl);
        return this;
    }

    /**
     * グループ名を設定する。<br>
     * @param groupName 設定するグループ名。
     * @see NbGroup#save(NbCallback)
     */
    protected NbGroup setGroupName(String groupName) {
        mGroupEntity.setGroupName(groupName);
        return this;
    }

    /**
     * グループ名を取得する。<br>
     * @return グループの名前。
     */
    public String getGroupName() {
        return mGroupEntity.getGroupName();
    }

    /**
     * グループのIDを取得する。
     * @return グループのID
     */
    public String getId() {
        return mGroupEntity.getId();
    }

    protected NbGroup setId(String id) {
        mGroupEntity.setId(id);
        return this;
    }

    /**
     * グループのETagを取得する。
     * @return グループのETag
     */
    public String getETag() {
        return mGroupEntity.getETag();
    }

    public NbGroup setETag(String eTag) {
        mGroupEntity.setETag(eTag);
        return this;
    }

    /**
     * グループが作成された時間を取得する。
     * @return グループの作成時間
     */
    public String getCreatedTime() {
        return mGroupEntity.getCreatedTime();
    }

    protected NbGroup setCreatedTime(String createdTime) {
        mGroupEntity.setCreatedTime(createdTime);
        return this;
    }

    /**
     * グループが更新された時間を取得する。
     * @return グループの更新時間
     */
    public String getUpdatedTime() {
        return mGroupEntity.getUpdatedTime();
    }

    protected NbGroup setUpdatedTime(String updatedTime) {
        mGroupEntity.setUpdatedTime(updatedTime);
        return this;
    }

    /**
     * グループ情報を設定する。<br>
     * @param entity 設定するグループ情報。
     * @see NbGroup#save(NbCallback)
     */
    protected NbGroup setGroupEntity(NbGroupEntity entity) {
        if (entity == null) {
            mGroupEntity = new NbGroupEntity();
        } else {
            mGroupEntity.set(entity);
        }
        return this;
    }

    /**
     * NbGroup を グループの JSON 表現に変換する。
     * @return グループの JSON 表現
     */
     public NbJSONObject toJsonObject() {
         return mGroupEntity.toJsonObject();
     }
    
    /**
     * グループ名が使用できるかチェックする。<br>
     * チェック項目はNullチェック、文字数チェック、使用不可文字チェック、予約語チェックを行う。
     * @param groupName グループ名
     * @return 使用可能であればtrue、使用不可であればfalse
     */
    private static boolean isValidGroupName(String groupName) {
        //グループ名は必須なのでnullチェックを行う
        if (groupName == null) {
            return false;
        }

        //文字数チェック
        if (groupName.isEmpty()) {
            return false;
        }

        //予約語チェック
        for (String reserved : GROUP_RESERVED_NAMES) {
            if (groupName.equals(reserved)) {
                return false;
            }
        }
        return true;
    }

    // for test
    public NbService _getService() {
        return mService;
    }
}
