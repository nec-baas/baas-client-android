/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import lombok.NonNull;

/**
 * ネットワーク管理クラス（Android側）。
 * @since 1.0
 */
public class NbAndroidNetworkMonitor extends NbNetworkMonitorImpl {
    private Context mContext;

    /** 受信するブロードキャスト名(ネットワーク状態変化) */
    private static final String NETWORKCHANGED_BROADCAST_NAME = "android.net.conn.CONNECTIVITY_CHANGE";

    /**
     * コンストラクタ。
     */
    public NbAndroidNetworkMonitor(@NonNull Context context) {
        mContext = context;
        registerNetworkMonitor();
    }

    @Override
    protected void registerNetworkMonitor() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(NETWORKCHANGED_BROADCAST_NAME);
        NetworkStateReceiver receiver = new NetworkStateReceiver();
        mContext.registerReceiver(receiver, filter);
    }

    /**
     * ネットワーク変化イベント受信ブロードキャスター。
     */
    public class NetworkStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(NETWORKCHANGED_BROADCAST_NAME)) {
                ConnectivityManager cm =
                        (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo info = cm.getActiveNetworkInfo();
                boolean isConnected = false;
                if (info != null) {
                    isConnected = info.isConnected();
                }
                changeNetworkState(isConnected);
            }
        }
    }
}
