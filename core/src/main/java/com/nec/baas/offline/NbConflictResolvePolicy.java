/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline;

/**
 * 衝突解決ポリシー
 * @since 1.2.0
 */
public enum NbConflictResolvePolicy {
    /**
     * 衝突解決ポリシー：ユーザ通知。
     */
    MANUAL(0),
    /**
     * 衝突解決ポリシー：クライアント優先。
     */
    CLIENT(1),
    /**
     * 衝突解決ポリシー：サーバ優先。
     */
    SERVER(2);


    public final int id;
    public final String idString;

    NbConflictResolvePolicy(final int id) {
        this.id = id;
        this.idString = Integer.toString(id);
    }

    /**
     * 対応する ID(整数値)を返す
     * @return ID
     */
    public int id() {
        return this.id;
    }

    /**
     * 対応する ID 文字列を返す
     * @return ID文字列
     */
    public String idString() {
        return this.idString;
    }

    /**
     * ID(整数値)に対応する衝突解決ポリシを返す
     * @param id ID
     * @return NbConflictResolvePolicy
     */
    public static NbConflictResolvePolicy fromInt(int id) {
        for (NbConflictResolvePolicy policy : values()) {
            if (policy.id == id) {
                return policy;
            }
        }
        throw new IllegalArgumentException("Invalid conflict resolve policy id:" + id);
    }

    /**
     * オブジェクトから変換。
     * Integerの場合はそのまま。それ以外の場合は文字列に変換してから parseInt する。
     * @param obj オブジェクト
     * @return NbConflictResolvePolicy
     */
    public static NbConflictResolvePolicy fromObject(Object obj) {
        if (obj instanceof Integer) {
            return fromInt((int)obj);
        } else {
            return fromInt(Integer.parseInt(obj.toString()));
        }
    }
}
