/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import com.nec.baas.json.*;
import com.nec.baas.user.*;
import com.nec.baas.util.*;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * ACL基底クラス。
 * @see NbAcl
 * @see NbContentAcl
 * @since 1.0
 */
public abstract class NbBaseAcl implements Serializable {
    public static final String KEY_OWNER = "owner";
    public static final String KEY_READ = "r";
    public static final String KEY_WRITE = "w";
    public static final String KEY_CREATE = "c";
    public static final String KEY_UPDATE = "u";
    public static final String KEY_DELETE = "d";
    public static final String KEY_ADMIN = "admin";

    private List<String> mRead   = new NbStringArrayNoDup();
    private List<String> mWrite  = new NbStringArrayNoDup();
    private List<String> mCreate = new NbStringArrayNoDup();
    private List<String> mUpdate = new NbStringArrayNoDup();
    private List<String> mDelete = new NbStringArrayNoDup();

    /**
     * デフォルトコンストラクタ
     */
    public NbBaseAcl() {

    }

    /**
     * ディープコピー用コンストラクタ
     * @param acl ACL
     */
    public NbBaseAcl(NbBaseAcl acl) {
        _deepCopyFrom(acl);
    }

    protected void _deepCopyFrom(NbBaseAcl acl) {
        if (acl != null) {
            setCreate(acl.getCreate());
            setDelete(acl.getDelete());
            setRead(acl.getRead());
            setUpdate(acl.getUpdate());
            setWrite(acl.getWrite());
        }
    }

    /**
     * コンストラクタ。ACL の JSON Object 表現から生成。
     * @param json JSON Object
     */
    public NbBaseAcl(Map<String, Object> json) {
        _setJson(json);
    }

    /**
     * ACLの各パラメータ初期化用コンストラクタ。
     * 引数に渡されないパラメータ(=null)は初期化しない。
     *
     * <p>パラメータリストは、ユーザIDおよびグループ名('g:'プレフィクス付き)を
     * 含めることができる。
     *
     * @param read readを許可するユーザID/グループ名のリスト
     * @param write writeを許可するユーザID/グループ名のリスト
     * @param create createを許可するユーザID/グループ名のリスト
     * @param update updateを許可するユーザID/グループ名のリスト
     * @param delete deleteを許可するユーザID/グループ名のリスト
     */
    public NbBaseAcl(Collection<String> read, Collection<String> write,
                     Collection<String> create, Collection<String> update, Collection<String> delete) {
        if (read != null) {
            setRead(read);
        }
        if (write != null) {
            setWrite(write);
        }
        if (create != null) {
            setCreate(create);
        }
        if (update != null) {
            setUpdate(update);
        }
        if (delete != null) {
            setDelete(delete);
        }
    }

    /**
     * readが許可されたユーザ・グループの一覧を取得する。
     * @return readが許可されたユーザID/グループ名('g:'プレフィクス付き)のリスト
     */
    public List<String> getRead() {
        return mRead;
    }

    /**
     * readを許可するユーザ・グループの一覧を設定する。<br>
     * nullが引数に渡された場合空の一覧が設定される。
     * @param read readを許可するユーザID/グループ名('g:'プレフィクス付き)のリスト
     */
    public void setRead(Collection<String> read) {
        List<String> newList = new NbStringArrayNoDup(read);
        mRead = newList;
    }

    /**
     * writeが許可されたユーザ・グループの一覧を取得する。
     * @return witeが許可されたユーザID/グループ名('g:'プレフィクス付き)のリスト
     */
    public List<String> getWrite() {
        return mWrite;
    }

    /**
     * writeを許可するユーザ・グループの一覧を設定する。<br>
     * nullが引数に渡された場合空の一覧が設定される。
     * @param write writeを許可するユーザID/グループ名('g:'プレフィクス付き)のリスト
     */
    public void setWrite(Collection<String> write) {
        List<String> newList = new NbStringArrayNoDup(write);
        mWrite = newList;
    }

    /**
     * createが許可されたユーザ・グループの一覧を取得する。
     * @return createが許可されたユーザID/グループ名('g:'プレフィクス付き)のリスト
     */
    public List<String> getCreate() {
        return mCreate;
    }

    /**
     * createを許可するユーザ・グループの一覧を設定する。<br>
     * nullが引数に渡された場合空の一覧が設定される。
     * @param create createを許可するユーザID/グループ名('g:'プレフィクス付き)のリスト
     */
    public void setCreate(Collection<String> create) {
        List<String> newList = new NbStringArrayNoDup(create);
        mCreate = newList;
    }

    /**
     * updateが許可されたユーザ・グループの一覧を取得する。
     * @return updateが許可されたユーザID/グループ名('g:'プレフィクス付き)のリスト
     */
    public List<String> getUpdate() {
        return mUpdate;
    }

    /**
     * updateを許可するユーザ・グループの一覧を設定する。<br>
     * nullが引数に渡された場合空の一覧が設定される。
     * @param update updateを許可するユーザID/グループ名('g:'プレフィクス付き)のリスト
     */
    public void setUpdate(Collection<String> update) {
        List<String> newList = new NbStringArrayNoDup(update);
        mUpdate = newList;
    }

    /**
     * deleteが許可されたユーザ・グループの一覧を取得する。
     * @return deleteが許可されたユーザID/グループ名('g:'プレフィクス付き)のリスト
     */
    public List<String> getDelete() {
        return mDelete;
    }

    /**
     * deleteを許可するユーザ・グループの一覧を設定する。<br>
     * nullが引数に渡された場合空の一覧が設定される。
     * @param delete deleteを許可するユーザID/グループ名('g:'プレフィクス付き)のリスト
     */
    public void setDelete(Collection<String> delete) {
        List<String> newList = new NbStringArrayNoDup(delete);
        mDelete = newList;
    }

    /**
     * JSON 文字列に変換する
     */
    public String toJsonString() {
        return toJsonObject().toJSONString();
    }

    @Override
    public String toString() {
        return toJsonString();
    }

    /**
     * JSON Object に変換する。
     */
    public NbJSONObject toJsonObject() {
        NbJSONObject json = new NbJSONObject();
        json.put(KEY_READ,   new NbJSONArray<>(mRead));
        json.put(KEY_WRITE,  new NbJSONArray<>(mWrite));
        json.put(KEY_CREATE, new NbJSONArray<>(mCreate));
        json.put(KEY_UPDATE, new NbJSONArray<>(mUpdate));
        json.put(KEY_DELETE, new NbJSONArray<>(mDelete));
        return json;
    }

    /**
     * Permission に対応する List を返す
     * @param permission NbAclPermission
     * @return ユーザID / プレフィクス付きグループ名のリスト
     */
    protected List<String> getListByPermission(NbAclPermission permission) {
        switch (permission) {
            case READ:
                return mRead;
            case WRITE:
                return mWrite;
            case CREATE:
                return mCreate;
            case UPDATE:
                return mUpdate;
            case DELETE:
                return mDelete;
            default:
                throw new IllegalArgumentException("invalid permission");
        }
    }

    /**
     * 対象権限にユーザID/グループ名を追加する。
     *
     * <p>
     * 引数のentryには下記が指定できる。
     * <ul>
     *     <li>ユーザIDの文字列</li>
     *     <li>グループ名の文字列(名前の前にg:を付ける必要がある)</li>
     * </ul>
     * </p>
     *
     * <p>すでに対象が ACL に含まれている場合は、二重登録は行わない。/p>
     *
     * @param permission 対象の権限
     * @param entry ユーザまたはグループの情報
     * @return エントリを追加できた場合はtrue、それ以外はfalseを返す
     */
    public boolean addEntry(NbAclPermission permission, String entry) {
        if (entry != null) {
            try {
                getListByPermission(permission).add(entry);
                return true;
            } catch (IllegalArgumentException e) {
                // error
            }
        }
        return false;
    }

    /**
     * 対象権限にユーザを追加する。
     *
     * @param permission 対象の権限
     * @param user NebulaUserクラスのインスタンス(ユーザIDが含まれていること)
     * @return エントリを追加できた場合はtrue、それ以外はfalseを返す
     */
    public boolean addEntry(NbAclPermission permission, NbUser user) {
        if (user == null) return false;
        return addEntry(permission, user.getUserId());
    }

    /**
     * 対象権限にグループを追加する。
     *
     * @param permission 対象の権限
     * @param group NebulaGroupクラスのインスタンス(グループ名が含まれていること)
     * @return エントリを追加できた場合はtrue、それ以外はfalseを返す
     */
    public boolean addEntry(NbAclPermission permission, NbGroup group) {
        if ((group == null) || (group.getGroupName() == null)) return false;
        return addEntry(permission, "g:" + group.getGroupName());
    }

    /**
     * 対象権限からユーザID/グループ名を削除する。
     *
     * <p>
     * 引数のentryには下記が指定できる。
     * <ul>
     *     <li>ユーザIDの文字列</li>
     *     <li>グループ名の文字列(名前の前にg:を付ける必要がある)</li>
     * </ul>
     * </p>
     *
     * @param permission 対象の権限
     * @param entry ユーザまたはグループの情報
     * @return エントリを削除できた場合はtrue、それ以外はfalseを返す
     */
    public boolean removeEntry(NbAclPermission permission, String entry) {
        if (entry != null) {
            try {
                getListByPermission(permission).remove(entry);
                return true;
            } catch (IllegalArgumentException ex) {
                // error
            }
        }
        return false;
    }

    /**
     * 対象権限からユーザIDを削除する。
     *
     * @param permission 対象の権限
     * @param user NebulaUserクラスのインスタンス(ユーザIDが含まれていること)
     * @return エントリを削除できた場合はtrue、それ以外はfalseを返す
     */
    public boolean removeEntry(NbAclPermission permission, NbUser user) {
        if (user == null) return false;
        return removeEntry(permission, user.getUserId());
    }

    /**
     * 対象権限からユーザID/グループ名を削除する。<p>
     *
     * @param permission 対象の権限
     * @param group NebulaGroupクラスのインスタンス(グループ名が含まれていること)
     * @return エントリを削除できた場合はtrue、それ以外はfalseを返す
     */
    public boolean removeEntry(NbAclPermission permission, NbGroup group) {
        if ((group == null) || (group.getGroupName() == null)) return false;
        return removeEntry(permission, "g:" + group.getGroupName());
    }

    /**
     * JSON 表現をセットする
     * @param json JSON
     * @deprecated {@link #setJson(NbJSONObject)}で置き換え
     */
    @Deprecated
    public void setMap(Map<String, Object> json) {
        _setJson(json);
    }

    /**
     * ACLの JSON 表現をセットする
     * @param json JSON
     */
    public void setJson(NbJSONObject json) {
        _setJson(json);
    }

    protected void _setJson(Map<String, Object> json) {
        if (json == null) return;

        for (Map.Entry<String,Object> entry : json.entrySet()) {
            setByKey(entry.getKey(), entry.getValue());
        }
    }

    protected boolean setByKey(String key, Object value) {
        switch (key) {
            case KEY_READ:
                setRead((Collection<String>)value);
                break;
            case KEY_WRITE:
                setWrite((Collection<String>)value);
                break;
            case KEY_CREATE:
                setCreate((Collection<String>)value);
                break;
            case KEY_UPDATE:
                setUpdate((Collection<String>)value);
                break;
            case KEY_DELETE:
                setDelete((Collection<String>)value);
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object target) {
        if (!(target instanceof NbBaseAcl)) return false;
        NbBaseAcl that = (NbBaseAcl)target;
        if (!listEqualsWithoutOrder(this.getRead(), that.getRead())) return false;
        if (!listEqualsWithoutOrder(this.getWrite(), that.getWrite())) return false;
        if (!listEqualsWithoutOrder(this.getCreate(), that.getCreate())) return false;
        if (!listEqualsWithoutOrder(this.getUpdate(), that.getUpdate())) return false;
        if (!listEqualsWithoutOrder(this.getDelete(), that.getDelete())) return false;
        return true;
    }

    protected boolean listEqualsWithoutOrder(List<String> a, List<String> b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (String str : a) {
            if (!b.contains(str)) {
                return false;
            }
        }
        return true;
    }
}
