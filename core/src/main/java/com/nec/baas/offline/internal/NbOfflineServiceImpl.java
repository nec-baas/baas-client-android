/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.core.internal.*;
import com.nec.baas.http.*;
import com.nec.baas.offline.*;

/**
 * NbOfflineService 実装
 */
public class NbOfflineServiceImpl implements NbOfflineService {
    private NbObjectOfflineServiceImpl mObjectOfflineService;
    //private NbFileOfflineServiceImpl mFileOfflineService;
    private NbDatabaseManager mDatabaseManager;
    private NbNetworkMonitorImpl mNetworkMonitor;
    private NbLoginOfflineServiceImpl mLoginOfflineService;
    private SystemManager mSystemManager;

    /**
     * NbOfflineServiceコンストラクタ.<br>
     * @since 1.0
     */
    public NbOfflineServiceImpl(NbObjectSyncManager syncManager, NbNetworkMonitorImpl monitor, NbDatabaseManager database,
                            SystemManager systemManager,
                            NbRestExecutorFactory builder, String machineId, int pid) {

        NbOfflineUtil.setMachineId(machineId);
        NbOfflineUtil.setPid(pid);

        mDatabaseManager = database;
        mNetworkMonitor = monitor;

        syncManager.setDatabaseManager(mDatabaseManager);
        syncManager.setRestExecutorFactory(builder);
        syncManager.setOfflineService(this);

        //fileSyncManager.setDatabaseManager(mDatabaseManager);
        //fileSyncManager.setRestExecutorFactory(factory);
        //fileSyncManager.setOfflineService(this);

        mSystemManager = systemManager;

        mLoginOfflineService = new NbLoginOfflineServiceImpl(mDatabaseManager);

        mObjectOfflineService = new NbObjectOfflineServiceImpl(this, syncManager);
        //mFileOfflineService = new NbFileOfflineServiceImpl(this, fileSyncManager);
    }

    /**
     * ObjectOfflineService を取得する
     * @return ObjectOfflineService
     */
    public NbObjectOfflineService objectService() {
        return mObjectOfflineService;
    }

    /**
     * FileOfflineService を取得する
     * @return FileOfflineService
     */
    /*
    public NbFileOfflineService fileService() {
        return mFileOfflineService;
    }
    */

    /**
     * Login Offline Service を取得する
     * @return LoginOfflineService
     */
    public NbLoginOfflineService loginService() {
        return mLoginOfflineService;
    }

    /**
     * データベースマネージャを取得する
     * @return NbDatabaseManager
     */
    public NbDatabaseManager databaseManager() {
        return mDatabaseManager;
    }

    /**
     * SystemManager を取得する
     * @return SystemManager
     */
    public SystemManager systemManager() {
        return mSystemManager;
    }

    /**
     * オフラインサービス機能終了.
     * サービス終了時に呼び出される。オフライン管理のリソース解放（DBクローズ処理等）を行う。
     */
    public void finish() {
        mDatabaseManager.close();
    }

    /**
     * 同期処理で使用するNebulaHttpRequestFactoryを設定する。
     * @param factory HTTPリクエストを作成するファクトリークラス
     */
    public void setRequestFactory(NbHttpRequestFactory factory) {
        mObjectOfflineService.setRequestFactory(factory);
        //mFileOfflineService.setRequestFactory(factory);
    }

    /**
     * トランザクション開始
     */
    public void beginTransaction() {
        mDatabaseManager.begin();
    }

    /**
     * トランザクション終了(commit)
     */
    public void endTransaction() {
        mDatabaseManager.commit();
    }

    //---------------------------------------------------------------------------------------------
    // Network Monitor Delegate
    //---------------------------------------------------------------------------------------------
    @Override
    public void registerNetworkEventListener(NbNetworkEventListener listener) {
        mNetworkMonitor.registerNetworkEventListener(listener);
    }

    @Override
    public void unregisterNetworkEventListener(NbNetworkEventListener listener) {
        mNetworkMonitor.unregisterNetworkEventListener(listener);
    }

    @Override
    public boolean isOnline() {
        return mNetworkMonitor.isOnline();
    }

    @Override
    public void setNetworkMode(NbNetworkMode mode) {
        mNetworkMonitor.setNetworkMode(mode);
    }

    @Override
    public NbNetworkMode getNetworkMode() {
        return mNetworkMonitor.getNetworkMode();
    }
}
