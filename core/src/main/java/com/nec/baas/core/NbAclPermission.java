/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

/**
 * ACLで使用する権限種別の列挙型。
 * @since 1.0
 */
public enum NbAclPermission {
    /**
     * read権限("r")。
     */
    READ("r"),

    /**
     * write権限("w")。
     * create, update, delete 権限を包含する。
     */
    WRITE("w"),

    /**
     * create権限("c")。
     */
    CREATE("c"),

    /**
     * update権限("u")。
     */
    UPDATE("u"),

    /**
     * delete権限("d")。
     */
    DELETE("d"),

    /**
     * admin権限("admin")。
     * ACLを変更可能な権限。
     */
    ADMIN("admin");

    public final String key;

    NbAclPermission(String key) {
        this.key = key;
    }
}
