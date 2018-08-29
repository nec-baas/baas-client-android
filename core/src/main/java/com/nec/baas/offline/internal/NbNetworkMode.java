/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

/**
 * ネットワークモード
 * @since 1.1
 */
public enum NbNetworkMode {
    /**
     * オンラインモード。
     * 常にネットワークが接続されているものとして動作する。
     */
    ONLINE(0),

    /**
     * オフラインモード。
     * 常にネットワークが切断されているものとして動作する。
     */
    OFFLINE(1),

    /**
     * 自動判定モード。
     * オンライン・オフラインかは自動的に判定される。
     */
    AUTO(2);

    public final int id;

    NbNetworkMode(final int id) {
        this.id = id;
    }

    /**
     * 対応する ID(整数値)を返す
     * @return ID
     */
    public int id() {
        return this.id;
    }

    /**
     * ID(整数値)に対応するモードを返す
     * @param id ID
     * @return モード
     */
    public static NbNetworkMode fromInt(int id) {
        for (NbNetworkMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Invalid network mode id:" + id);
    }
}
