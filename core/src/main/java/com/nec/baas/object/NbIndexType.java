/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.core.NbResultCallback;

import java.util.Map;

/**
 * ローカルDB用インデックスの型
 * @see NbOfflineObjectBucket#setIndexToLocal(Map, NbResultCallback)
 * @see NbOfflineObjectBucket#getIndexFromLocal()
 * @since 1.2.2
 */
public enum NbIndexType {
    /**
     * 文字列。
     */
    STRING("STRING"),

    /**
     * 論理値。
     */
    BOOLEAN("BOOLEAN"),

    /**
     * 数値。
     */
    NUMBER("NUMBER");

    public final String type;

    NbIndexType(final String type) {
        this.type = type;
    }

    /**
     * 対応するインデックスの型(文字列)を返す
     * @return インデックスの型
     */
    public String type() {
        return this.type;
    }

    /**
     * type(文字列)に対応するインデックスの型を返す
     * @param type インデックスの型(文字列)
     * @return インデックスの型
     */
    public static NbIndexType fromString(String type) {
        for (NbIndexType t : values()) {
            if (t.type.equals(type)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Invalid index type:" + type);
    }
}
