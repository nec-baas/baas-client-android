/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.user.internal;

import com.nec.baas.core.*;
import com.nec.baas.json.*;
import com.nec.baas.util.*;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * グループ情報格納用クラス
 * @since 1.0
 */
@Accessors(chain = true)
public class NbGroupEntity {
    /** グループに所属するユーザIDの一覧 */
    @Getter
    private final List<String> users = new NbStringArrayNoDup();

    /** グループに所属するグループ名の一覧 */
    @Getter
    private final List<String> groups = new NbStringArrayNoDup();

    /** グループの ACL */
    @Getter
    private NbAcl acl = null;

    /** グループの ID */
    @Getter @Setter
    private String id = null;

    /** グループ名 */
    @Getter @Setter
    private String groupName = null;

    /** ETag */
    @Getter @Setter
    private String eTag = null;

    /** グループの作成日時 */
    @Getter @Setter
    private String createdTime = null;

    /** グループの更新日時 */
    @Getter @Setter
    private String updatedTime = null;

    /**
     * デフォルトコンストラクタ
     */
    public NbGroupEntity() {
    }

    /**
     * グループ情報初期化用コンストラクタ。<br>
     * 引数に渡されない値は初期化しない。
     * @param users グループに所属するユーザのリスト
     * @param groups グループに所属するグループのリスト
     * @param acl グループに設定するACL
     */
    public NbGroupEntity(List<String> users, List<String> groups, NbAcl acl) {
        this();
        setUsers(users);
        setGroups(groups);
        setAcl(acl);
    }

    /**
     * グループの JSON 表現から NbGroupEntity を生成する。
     * @param json グループの JSON 表現
     */
    public NbGroupEntity(NbJSONObject json) {
        this();
        set(json);
    }

    /**
     * NbGroupEntity の内容をコピーする。
     * @param entity コピー元
     * @return this
     */
    public NbGroupEntity set(NbGroupEntity entity) {
        setId(entity.getId());
        setGroupName(entity.getGroupName());

        setUsers(entity.getUsers());
        setGroups(entity.getGroups());

        setAcl(entity.getAcl());

        setCreatedTime(entity.getCreatedTime());
        setUpdatedTime(entity.getUpdatedTime());

        setETag(entity.getETag());

        return this;
    }

    /**
     * グループの JSON 表現を NbGroupEntity に設定する。
     * @param json グループの JSON 表現
     */
    public NbGroupEntity set(NbJSONObject json) {
        setId(json.getString(NbKey.ID));
        setGroupName(json.getString(NbKey.NAME));

        setUsers(json.getJSONArray(NbKey.USERS));
        setGroups(json.getJSONArray(NbKey.GROUPS));

        setAcl(json.getJSONObject(NbKey.ACL));

        setCreatedTime(json.getString(NbKey.CREATED_AT));
        setUpdatedTime(json.getString(NbKey.UPDATED_AT));

        setETag(json.getString(NbKey.ETAG));

        return this;
    }

    /**
     * NbGroupEntity を グループの JSON 表現に変換する。
     * @return グループの JSON 表現
     */
    public NbJSONObject toJsonObject() {
        NbJSONObject json = new NbJSONObject();

        // NbAcl#toJsonObject()に合わせ、Stringに対するnullチェックはしない
        // NbAcl#toJsonObject()に合わせ、ListはNbJSONArray型で格納する
        json.put(NbKey.ID, getId());
        json.put(NbKey.NAME, getGroupName());
        json.put(NbKey.USERS, new NbJSONArray<>(getUsers()));
        json.put(NbKey.GROUPS, new NbJSONArray<>(getGroups()));
        if (getAcl() != null) {
            json.put(NbKey.ACL, getAcl().toJsonObject());
        } else {
            json.put(NbKey.ACL, null);
        }
        json.put(NbKey.CREATED_AT, getCreatedTime());
        json.put(NbKey.UPDATED_AT, getUpdatedTime());
        json.put(NbKey.ETAG, getETag());

        return json;
    }

    /**
     * グループに所属するユーザの一覧を設定する。
     * @param users グループに所属するユーザのリスト。null を指定した場合はリストを空にする。
     */
    public void setUsers(List<String> users) {
        this.users.clear();
        if (users != null) {
            for (String user : users) {
                addUser(user);
            }
        }
    }

    /**
     * グループに所属するグループの一覧を設定する。
     * @param groups グループに所属するグループのリスト。null を指定した場合はリストを空にする。
     */
    public void setGroups(List<String> groups) {
        this.groups.clear();
        if (groups != null) {
            for (String group : groups) {
                addGroup(group);
            }
        }
    }

    /**
     * グループのACLを設定する。
     * @param acl グループに設定するACL
     */
    public void setAcl(NbAcl acl) {
        if (acl != null) {
            this.acl = new NbAcl(acl);
        } else {
            this.acl = null;
        }
    }

    private void setAcl(NbJSONObject aclJson) {
        if (aclJson != null) {
            this.acl = new NbAcl(aclJson);
        } else {
            this.acl = null;
        }
    }

    /**
     * グループに所属するユーザを追加する。
     * @param user グループに追加するユーザID
     */
    public void addUser(String user) {
        if (user == null) return;
        users.add(user);
    }

    /**
     * グループに所属するグループを追加する。
     * @param group グループに追加するグループ名
     */
    public void addGroup(String group) {
        if (group == null) return;
        groups.add(group);
    }

    /**
     * グループに所属するユーザを削除する。
     * @param user グループから削除するユーザID
     */
    public void removeUser(String user) {
        if (user != null) {
            users.remove(user);
        }
    }

    /**
     * グループに所属するグループを削除する。
     * @param group グループから削除するグループ名
     */
    public void removeGroup(String group) {
        if (group != null) {
            groups.remove(group);
        }
    }
}
