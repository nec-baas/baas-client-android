/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.offline.*;

/**
 * Network Monitor
 */
public interface NbNetworkMonitor {
    /**
     * ネットワークの接続状態を取得する。
     * @return trueは接続中、falseは未接続となる。
     */
    boolean isOnline();

    /**
     * ネットワークの接続状態変更を受け取るリスナーを設定する。
     * @param listener ネットワーク状態の変更を受け取るリスナー
     */
    void registerNetworkEventListener(NbNetworkEventListener listener);

    /**
     * ネットワークの接続状態変更を受け取るリスナーを解除する。
     * @param listener 解除するリスナーのインスタンス
     */
    void unregisterNetworkEventListener(NbNetworkEventListener listener);

    /**
     * ネットワーク状態の設定を行う。
     * @param mode 設定するネットワーク状態
     */
    void setNetworkMode(NbNetworkMode mode);

    /**
     * 設定されたネットワーク状態を取得する。
     * @return 設定されたネットワーク状態
     */
    NbNetworkMode getNetworkMode();
}
