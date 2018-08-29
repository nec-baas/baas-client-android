/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * コンテンツACL。
 * <p>
 * read, write, create, update, delete 属性で構成される。
 * {@link NbAcl} と異なり、owner と admin 権限は存在しない。
 * @see NbAcl
 * @since 1.0
 */
public class NbContentAcl extends NbBaseAcl implements Serializable {
    public NbContentAcl() {
        super();
    }

    public NbContentAcl(NbContentAcl acl) {
        super(acl);
    }

    /**
     * コンストラクタ。ACL の JSON Object 表現から生成。
     * @param json JSON Object
     */
    public NbContentAcl(Map<String, Object> json) {
        super(json);
    }

    /**
     * ACLの各パラメータ初期化用コンストラクタ。
     * 引数に渡されないパラメータ(=null)は初期化しない。
     *
     * <p>パラメータリストには、ユーザIDおよびグループ名('g:'プレフィクス付き)を
     * 含めることができる。
     *
     * @param read readを許可するユーザID/グループ名のリスト
     * @param write writeを許可するユーザID/グループ名のリスト
     * @param create createを許可するユーザID/グループ名のリスト
     * @param update updateを許可するユーザID/グループ名のリスト
     * @param delete deleteを許可するユーザID/グループ名のリスト
     */
    public NbContentAcl(List<String> read, List<String> write,
                        List<String> create, List<String> update, List<String> delete) {
        super(read, write, create, update, delete);
    }
}
