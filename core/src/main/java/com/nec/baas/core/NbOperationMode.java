/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

/**
 * 起動モード。
 * @see NbSetting#setOperationMode(int)
 * @since 1.0
 */
public enum NbOperationMode {
    /** 本番モード */
    PRODUCTION(0),

    /** デバッグモード */
    DEBUG(1),

    /** テストモード */
    TEST(2);

    public final int id;

    NbOperationMode(int id) {
        this.id = id;
    }

    /**
     * ID(整数値)に対応するモードを返す
     * @param id ID
     * @return モード
     */
    public static NbOperationMode fromInt(int id) {
        for (NbOperationMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Invalid operation mode id:" + id);
    }
}
