/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.http.*;

/**
 * オフラインサービス。
 * @since 1.0
 */
public interface NbOfflineService extends NbNetworkMonitor
{
    /**
     * ObjectOfflineService を取得する
     * @return ObjectOfflineService
     */
    NbObjectOfflineService objectService();

    /**
     * Login Offline Service を取得する
     * @return LoginOfflineService
     */
    NbLoginOfflineService loginService();

    /**
     * データベースマネージャを取得する
     * @return NbDatabaseManager
     */
    NbDatabaseManager databaseManager();

    /**
     * SystemManager を取得する
     * @return SystemManager
     */
    SystemManager systemManager();

    /**
     * オフラインサービス機能終了.
     * サービス終了時に呼び出される。オフライン管理のリソース解放（DBクローズ処理等）を行う。
     */
    void finish();

    /**
     * 同期処理で使用するNebulaHttpRequestFactoryを設定する。
     * @param factory HTTPリクエストを作成するファクトリークラス
     */
    void setRequestFactory(NbHttpRequestFactory factory);

    /**
     * トランザクション開始
     */
    void beginTransaction();

    /**
     * トランザクション終了(commit)
     */
    void endTransaction();
}
