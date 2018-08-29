/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import com.nec.baas.json.*;
import com.nec.baas.util.*;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * ACL。
 * owner, read, write, create, update, delete, admin 属性から構成される。
 * @see NbContentAcl
 * @since 1.0
 */
public class NbAcl extends NbBaseAcl implements Serializable {

    private String mOwner = null;
    private List<String> mAdmin = new NbStringArrayNoDup();

    /**
     * デフォルトコンストラクタ
     */
    public NbAcl() {
        super();
    }

    /**
     * ディープコピー用コンストラクタ
     * @param acl ACL
     */
    public NbAcl(NbAcl acl) {
        super();
        _deepCopyFrom(acl);
    }

    private void _deepCopyFrom(NbAcl acl) {
        if (acl != null) {
            super._deepCopyFrom(acl);
            setOwner(acl.getOwner());
            setAdmin(acl.getAdmin());
        }
    }

    /**
     * コンストラクタ。ACL の JSON Object 表現から生成。
     * @param json JSON Object
     */
    public NbAcl(Map<String, Object> json) {
        // 注: 実行順序の関係で、super(json)は×。
        // (mOwner, mAdmin の初期化は super の後)
        super();
        _setJson(json);
    }

    /**
     * コンストラクタ。Acl または Map から生成。
     * @param obj コピー元のオブジェクト。Acl または Map 型。
     * @throws IllegalArgumentException obj の型が不正
     */
    public NbAcl(Object obj) {
        if (obj instanceof NbAcl) {
            _deepCopyFrom((NbAcl) obj);
        } else if (obj instanceof Map) {
            _setJson((Map<String,Object>)obj);
        } else {
            throw new IllegalArgumentException("Invalid acl type");
        }
    }

    /**
     * コンテンツACLの各パラメータ初期化用コンストラクタ。
     * 引数に渡されないパラメータ(=null)は初期化しない。
     *
     * <p>パラメータリストには、ユーザIDおよびグループ名('g:'プレフィクス付き)を
     * 含めることができる。
     *
     * @param owner オーナのユーザID
     * @param read readを許可するユーザID/グループ名のリスト
     * @param write writeを許可するユーザID/グループ名のリスト
     * @param create createを許可するユーザID/グループ名のリスト
     * @param update updateを許可するユーザID/グループ名のリスト
     * @param delete deleteを許可するユーザID/グループ名のリスト
     * @param admin admin権限を付加するユーザID/グループ名のリスト
     */
    public NbAcl(String owner, Collection<String> read, Collection<String> write,
                 Collection<String> create, Collection<String> update, Collection<String> delete,
                 Collection<String> admin) {
        super(read, write, create, update, delete);

        if (owner != null) {
            setOwner(owner);
        }
        if (admin != null) {
            setAdmin(admin);
        }
    }

    /**
     * オーナのユーザIDを取得する。
     * @return String オーナのユーザID
     */
    public String getOwner() {
        return mOwner;
    }

    /**
     * オーナのユーザIDを設定する。
     * nullを引数に渡した場合、空の文字列""が設定される。
     * @param owner オーナのユーザID
     */
    public void setOwner(String owner) {
        mOwner = owner;
    }

    /**
     * admin権限が付加されたユーザ・グループの一覧を取得する。
     * @return admin権限が付加されたユーザID/グループ名('g:'プレフィクス付き)のリスト
     */
    public List<String> getAdmin() {
        return mAdmin;
    }

    /**
     * admin権限を付加するユーザ・グループの一覧を設定する。
     * nullを引数に渡した場合、空の一覧が設定される。
     * @param admin admin権限を付加するユーザID/グループ名('g:'プレフィクス付き)のリスト
     */
    public void setAdmin(Collection<String> admin) {
        mAdmin = new NbStringArrayNoDup(admin);
    }

    /** {@inheritDoc} */
    @Override
    public NbJSONObject toJsonObject() {
        NbJSONObject json = super.toJsonObject();
        json.put(KEY_OWNER, mOwner);
        json.put(KEY_ADMIN, new NbJSONArray<>(mAdmin));
        return json;
    }

    @Override
    protected List<String> getListByPermission(NbAclPermission permission) {
        switch (permission) {
            case ADMIN:
                return mAdmin;
            default:
                return super.getListByPermission(permission);
        }
    }

    @Override
    protected boolean setByKey(String key, Object value) {
        switch (key) {
            case KEY_OWNER:
                setOwner((String)value);
                break;
            case KEY_ADMIN:
                setAdmin((Collection<String>)value);
                break;
            default:
                return super.setByKey(key, value);
        }
        return true;
    }

    @Override
    public boolean equals(Object target) {
        if (!(target instanceof NbAcl)) return false;
        NbAcl that = (NbAcl)target;
        if (mOwner == null) {
            if (that.getOwner() != null) return false;
        } else if (!mOwner.equals(that.getOwner())) {
            return false;
        }
        if (!listEqualsWithoutOrder(this.getAdmin(), that.getAdmin())) return false;

        return super.equals(target);
    }
}
