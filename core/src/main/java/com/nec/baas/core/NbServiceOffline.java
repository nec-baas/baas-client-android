/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import com.nec.baas.offline.*;

/**
 * NbService : オフライン機能。
 * @see NbService
 */
public interface NbServiceOffline {
    /**
     * ネットワークの接続状態変更を受け取るリスナーを設定する。
     * @param listener ネットワーク状態の変更を受け取るリスナー
     * @see NbNetworkEventListener
     */
    void registerNetworkEventListener(NbNetworkEventListener listener);

    /**
     * ネットワークの接続状態変更を受け取るリスナーを解除する。
     * @param listener 解除するリスナーのインスタンス
     * @see NbNetworkEventListener
     */
    void unregisterNetworkEventListener(NbNetworkEventListener listener);

    /**
     * オフライン用データベースで使用するパスワードを変更する。
     * @param oldPassword 古いパスワード
     * @param newPassword 新しいパスワード
     */
    void changeDatabasePassword(String oldPassword, String newPassword);

    /**
     * オフライン用ログインキャッシュの有効期限を設定する。
     * @param expire ログインキャッシュの有効期限（秒）
     */
    void setLoginCacheValidTime(Long expire);

    /**
     * オフライン用ログインキャッシュの有効期限を取得する。
     * @return time ログインキャッシュの有効期限（秒）
     */
    long getLoginCacheValidTime();
}
