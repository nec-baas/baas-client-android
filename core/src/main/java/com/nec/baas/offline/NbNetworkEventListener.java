/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline;

/**
 * ネットワークの接続状態変更を受け取るリスナー。
 * @since 1.0
 */
public interface NbNetworkEventListener {
    /**
     * ネットワークの接続状態が変更された際に呼び出される。<br>
     * @param isOnline trueはオンライン状態、falseはオフライン状態
     */
    void onNetworkStateChanged(boolean isOnline);
}
