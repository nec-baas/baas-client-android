/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import com.nec.baas.core.*;
import com.nec.baas.file.*;
import com.nec.baas.file.internal.*;
import com.nec.baas.http.*;
import com.nec.baas.object.*;
import com.nec.baas.object.internal.*;
import com.nec.baas.offline.*;
import com.nec.baas.offline.internal.*;
import com.nec.baas.user.*;
import com.nec.baas.util.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * NbService 実装。
 */
@Accessors(prefix = "m")
public class NbServiceImpl extends NbService {
    private static final NbLogger log = NbLogger.getLogger(NbServiceImpl.class);

    @Setter // for test
    private NbObjectBucketManagerImpl mObjectBucketManager;
    private NbFileBucketManagerImpl mFileBucketManager;

    /** HTTP Request Factory */
    @Getter
    @Setter // for test
    private NbHttpRequestFactory mHttpRequestFactory;

    /** NbRestExecutorFactory */
    @Getter // Note: Push など別パッケージから利用するため protected にしない
    @Setter(AccessLevel.PROTECTED) // for test
    private NbRestExecutorFactory mRestExecutorFactory;

    @Getter
    private NbOfflineService mOfflineService;

    /** セッショントークン */
    @Getter
    @Setter // for test
    private NbSessionToken mSessionToken;

    private static final long FINISH_TIMEOUT = 3L; //FINISH処理の待ち時間

    @Getter
    private String mTenantId;
    @Getter
    private String mAppId;
    @Getter
    private String mAppKey;
    @Getter
    private String mEndPointUri;

    //DataSecurity
    @Deprecated
    @Getter @Setter
    private String mDeviceId;

    /**
     * Strict Singleton モード設定を行う。
     * true にすると、Service のインスタンスは１個しか作成できない。
     * ただし、マルチテナントモード有効時は無視される。
     * @param flag
     * @return
     */
    public static void setStrictSingleton(boolean flag) {
        sStrictSingleton = flag;
    }

    /**
     * NbServiceImpl デフォルトコンストラクタ
     */
    public NbServiceImpl() {
    }

    // 初期化処理本体
    public synchronized void initialize(@NonNull String tenantId, @NonNull String appId, @NonNull String appKey, @NonNull String endPointUri,
                                        @NonNull NbSessionToken sessionToken, @NonNull NbRestExecutorFactory executorFactory) {
        log.fine("NbServiceImpl() <start>");
        log.info("NbServiceImpl() appId=" + appId);
        // セキュリティ上、Key はログに出力しない
        // log.info("NbServiceImpl() appKey="+appKey);
        log.info("NbServiceImpl() endPointUri=" + endPointUri);

        // シングルトン保存
        if (!sIsMultiTenantEnabled && sInstance != null) {
            if (sStrictSingleton) {
                throw new IllegalStateException("NbServiceImpl instance had been already created.");
            } else {
                log.warning("Multiple NbServiceImpl instances are created!");
            }
        }
        sInstance = this;

        this.mTenantId = tenantId;
        this.mAppId = appId;
        this.mAppKey = appKey;
        this.mEndPointUri = endPointUri;

        NbHttpClient.getInstance().open();
        mHttpRequestFactory = new NbHttpRequestFactory(tenantId, appId, appKey, endPointUri, sessionToken);
        mRestExecutorFactory = executorFactory;
        mSessionToken = sessionToken;

        initInstances();

        log.fine("NbServiceImpl() <end>");
    }

    protected void initInstances() {
        mObjectBucketManager = new NbObjectBucketManagerImpl(this);
        mFileBucketManager = new NbFileBucketManagerImpl(this);
    }

    /** {@inheritDoc} */
    @Override
    public NbObjectBucketManager objectBucketManager() {
        return mObjectBucketManager;
    }

    /** {@inheritDoc} */
    public NbFileBucketManager fileBucketManager() {
        return mFileBucketManager;
    }

    protected void setFileBucketManager(NbFileBucketManager fbm) {
        mFileBucketManager = (NbFileBucketManagerImpl)fbm;
    }

    /** {@inheritDoc} */
    @Override
    public void finish() {
        log.fine("finish() <start>");
        //アプリからUIスレッドで呼び出されることを想定し、サブスレッドでHTTPクローズする。
        final CountDownLatch latch = new CountDownLatch(1);
        NbUtil.runInBackground(new Runnable() {
            public void run() {
                log.fine("finish() called NbHttpClient.close()");
                //HttpClient 終了処理
                NbHttpClient.getInstance().close();
                //Apiカウント保存
                mRestExecutorFactory.create().saveApiCount();
                if (mOfflineService != null) {
                    //オフライン機能終了処理
                    mOfflineService.finish();
                }
                latch.countDown();
            }
        });

        //UIスレッドは5秒以上待てないので、待ち時間は最大でFINISH_TIMEOUT(3秒)とする。
        try {
            latch.await(FINISH_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            //e.printStackTrace();
            log.warning("finish interrupted: {0}", e.getMessage());
        }
        log.fine("finish() <end>");
    }

    //オフライン処理
    /** {@inheritDoc} */
    @Override
    public void registerNetworkEventListener(NbNetworkEventListener listener) {
        mOfflineService.registerNetworkEventListener(listener);
    }

    /** {@inheritDoc} */
    @Override
    public void unregisterNetworkEventListener(NbNetworkEventListener listener) {
        mOfflineService.unregisterNetworkEventListener(listener);
    }

    /** {@inheritDoc} */
    @Override
    public void changeDatabasePassword(String oldPassword, String newPassword) {
        mOfflineService.databaseManager().changePassword(oldPassword, newPassword);
    }

    private void ensureOfflineService() {
        if (mOfflineService == null) {
            throw new IllegalStateException("No offline service enabled.");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setLoginCacheValidTime(Long expire) {
        log.fine("setLoginCacheValidTime() <start>"
                + " expire=" + expire);
        ensureOfflineService();
        mOfflineService.loginService().setLoginCacheValidTime(expire);
        log.fine("setLoginCacheValidTime() <end>");
    }

    /** {@inheritDoc} */
    @Override
    public long getLoginCacheValidTime() {
        ensureOfflineService();
        return mOfflineService.loginService().getLoginCacheValidTime();
    }

    /**
     * OfflineService をセットする。関連する情報も合わせて設定する。
     * @param offlineService OfflineService
     */
    public void setOfflineService(NbOfflineService offlineService) {
        log.fine("setOfflineService() <start>"
                + " offlineService=" + offlineService);
        assert (mOfflineService == null);
        if (offlineService != null) {
            //ログイン状態が有効な場合はユーザ情報を有効化する
            if (NbUser.isLoggedIn(this)) {
                offlineService.loginService().userIdActivate(NbUser.getCurrentUser(this).getUserId());
            }
            mOfflineService = offlineService;
            mOfflineService.setRequestFactory(mHttpRequestFactory);
            mObjectBucketManager.setOfflineService();
            //mFileBucketManager.setOfflineService();
            mOfflineService.loginService().cleanLoginCache();

        }
        log.fine("setOfflineService() <end>");
    }

    /**
     * OfflineService をセットする(テスト用)。
     * 関連情報はセットアップされない。
     * @param offlineService OfflineService
     */
    public void setOfflineServiceDirect(NbOfflineService offlineService) {
        mOfflineService = offlineService;
    }

    /**
     * Runnable を UI スレッドで実行する(instance method)。
     * <p>実際に UI スレッドで実行するかどうかは実装により異なる。
     * Android 実装では UI スレッドで実行されるが、
     * Pure Java 実装では呼び出し元スレッドのまま実行される。
     * @param runnable 実行する Runnable
     */
    public void runOnUiThreadInstance(final Runnable runnable) {
        runnable.run();
    }

    /**
     * Runnable を UI スレッドで実行する。
     * <p>実際に UI スレッドで実行するかどうかは実装により異なる。
     * Android 実装では UI スレッドで実行されるが、
     * Pure Java 実装では呼び出し元スレッドのまま実行される。
     * @param runnable 実行する Runnable
     */
    public static void runOnUiThread(final Runnable runnable) {
        if (sInstance == null) {
            throw new IllegalStateException("NbServiceImpl is not initialized!");
        }
        sInstance.runOnUiThreadInstance(runnable);
    }

    @Override
    public void setTenantId(@NonNull String tenantId) {
        this.mTenantId = tenantId;
        getHttpRequestFactory().setTenantId(tenantId);
    }

    @Override
    public void setAppId(@NonNull String appId) {
        this.mAppId = appId;
        getHttpRequestFactory().setAppId(appId);
    }

    @Override
    public void setAppKey(@NonNull String appKey) {
        this.mAppKey = appKey;
        getHttpRequestFactory().setAppKey(appKey);
    }

    @Override
    public void setEndPointUri(@NonNull String endPointUri) {
        this.mEndPointUri = endPointUri;
        getHttpRequestFactory().setEndPointUri(endPointUri);
    }

    @Override
    public NbRestExecutor createRestExecutor() {
        return getRestExecutorFactory().create();
    }

    // for test
    public static void __clearSingleton() {
        sInstance = null;
    }
}
