/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import android.content.Context;
import android.os.Build;

import com.nec.baas.core.internal.*;
import com.nec.baas.offline.internal.*;
import com.nec.baas.util.NbLogger;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Android用Nebulaサービスのビルダークラス
  * @since 1.0
 */
public class NbAndroidServiceBuilder extends NbServiceBuilder<NbAndroidServiceBuilder> {
    private static final NbLogger log = NbLogger.getLogger(NbAndroidServiceBuilder.class);

    private Context mContext;

    private boolean mUseOfflineService = false;
    private String mOfflinePassword;

    /**
     * コンストラクタ。
     * テナントID/アプリID/アプリキー/EndPoint URI は、
     * {@link #tenantId()}, {@link #appId()}, {@link #appKey()}, {@link #endPointUri()}
     * で設定すること。
     * @param context アプリケーションコンテキスト
     * @since 5.0.0
     */
    public NbAndroidServiceBuilder(Context context) {
        super(NbAndroidServiceBuilder.class);
        mContext = context.getApplicationContext();

        //DataSecurity
        deviceId(Build.SERIAL);
    }

    /**
     * Nebulaサービス生成クラスのコンストラクタ。<br>
     * テナントＩＤ、アプリケーションＩＤ、アプリケーションキーを設定する。
     * エンドポイントUriはデフォルトの値が使用される。
     *
     * @param context アプリケーションコンテキスト
     * @param tenantId アプリが属するテナントID
     * @param appId アプリケーションID
     * @param appKey アプリケーションキー
     * @deprecated {@link #NbAndroidServiceBuilder(Context)} で置き換え。
     */
    @Deprecated
    public NbAndroidServiceBuilder(Context context, String tenantId, String appId, String appKey) {
        this(context);
        tenantId(tenantId).appId(appId).appKey(appKey);
    }

    /**
     * オフラインモードを使用する。Android 版でのみ使用可能。
     * @param password データベース暗号化パスワード
     * @throws UnsupportedOperationException サポートされない
     */
    public NbAndroidServiceBuilder useOfflineMode(String password) {
        mUseOfflineService = true;
        mOfflinePassword = password;
        return myself;
    }

    /** {@inheritDoc} */
    @Override
    public NbService build() {
        NbAndroidService service = (NbAndroidService)super.build();

        if (mUseOfflineService) {
            NbOfflineService offlineService = new NbOfflineServiceImpl(
                    new NbAndroidObjectSyncManager(mContext),
                    //new NbAndroidFileSyncManager(mContext),
                    new NbAndroidNetworkMonitor(mContext),
                    new NbAndroidDatabaseManager(mOfflinePassword, mContext),
                    new NbAndroidSystemManager(mContext),
                    service.getRestExecutorFactory(),
                    getMachineId(),
                    android.os.Process.myPid());
            service.setOfflineService(offlineService);
        }

        return service;
    }

    @Override
    protected NbServiceImpl createNebulaService() {
        NbAndroidService service = new NbAndroidService();
        service._setContext(mContext);
        return service;
    }

    @Override
    public NbRestExecutorFactory createRestExecutorFactory() {
        return new NbAndroidRestExecutorFactory(mContext);
    }

    @Override
    protected NbSessionToken createSessionToken() {
        return new NbAndroidSessionToken(mContext);
    }

    private static final int MACHINE_ID_LENGTH = 3;

    private String getMachineId() {
        String machineId = String.format("%06x", new Random().nextInt(0xffffff));
        try {
            List<NetworkInterface> networkList = Collections.list(NetworkInterface.getNetworkInterfaces());
            machineId = searchMachineId(machineId, networkList);
        } catch (SocketException e) {
            //Exceptionが発生した場合ランダムな値を渡す
            log.warning("Can't get network ID: {0}", e.getMessage());
        }

        return machineId;
    }

    private String searchMachineId(String machineId, List<NetworkInterface> networkLsit) throws SocketException {
        for (NetworkInterface nint : networkLsit) {
            byte[] address = nint.getHardwareAddress();
            if (address != null) {
                if (address.length >= MACHINE_ID_LENGTH) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = address.length - MACHINE_ID_LENGTH; i < address.length; i++) {
                        builder.append(String.format("%02x", address[i]));
                    }
                    machineId = builder.toString();
                    break;
                }
            }
        }
        return machineId;
    }

}
