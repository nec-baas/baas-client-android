/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

/**
 * バケットモード
 * @since 1.2.2
 */
public enum NbBucketMode {
    /**
     * オンラインモード
     */
    ONLINE(0),
    /**
     * レプリカモード
     */
    REPLICA(1),
    /**
     * ローカルモード
     * @since 1.2.3
     */
    LOCAL(2);

    public final int id;
    public final String idString;

    NbBucketMode(final int id) {
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
     * ID(整数値)に対応するモードを返す
     * @param id ID
     * @return モード
     */
    public static NbBucketMode fromInt(int id) {
        for (NbBucketMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Invalid bucket mode id:" + id);
    }

    /**
     * オブジェクトから変換。
     * Integerの場合はそのまま。それ以外の場合は文字列に変換してから parseInt する。
     * @param obj オブジェクト
     * @return NbBucketMode
     */
    public static NbBucketMode fromObject(Object obj) {
        if (obj instanceof Integer) {
            return fromInt((int)obj);
        } else {
            return fromInt(Integer.parseInt(obj.toString()));
        }
    }
}
