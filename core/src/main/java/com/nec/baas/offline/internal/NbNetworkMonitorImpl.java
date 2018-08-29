/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.offline.*;
import com.nec.baas.util.*;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * ネットワーク監視クラス。
 * @since 1.0
 */
public abstract class NbNetworkMonitorImpl implements NbNetworkMonitor {
    private static final NbLogger log = NbLogger.getLogger(NbNetworkMonitorImpl.class);

    private boolean mIsOnline = true;
    private boolean mIsConnected = false;
    private List<NbNetworkEventListener> mNetworkEventListeners = new ArrayList<NbNetworkEventListener>();
    private NbNetworkMode mNetworkMode = NbNetworkMode.AUTO;

    @Override
    public boolean isOnline() {
        boolean isOnline = mIsOnline;
        if (mNetworkMode == NbNetworkMode.AUTO) {
            isOnline = mIsConnected;
        }
        return isOnline;
    }

    protected void changeNetworkState(boolean isConnected) {
        log.fine("changeNetworkState() <start>"
                + " isConnected=" + isConnected);

        if (mIsConnected != isConnected) {
            mIsConnected = isConnected;

            for (NbNetworkEventListener listener : mNetworkEventListeners) {
                listener.onNetworkStateChanged(isConnected);
            }
        }
        log.fine("changeNetworkState() <end>");
    }

    @Override
    public void registerNetworkEventListener(@NonNull NbNetworkEventListener listener) {
        mNetworkEventListeners.add(listener);
    }

    @Override
    public void unregisterNetworkEventListener(@NonNull NbNetworkEventListener listener) {
        mNetworkEventListeners.remove(listener);
    }

    @Override
    public void setNetworkMode(NbNetworkMode mode) {
        log.fine("setNetworkMode() called mode=" + mode);
        switch (mode) {
            case OFFLINE:
                mIsOnline = false;
                mNetworkMode = mode;
                break;
            case ONLINE:
                mIsOnline = true;
                mNetworkMode = mode;
                break;
            case AUTO:
                mNetworkMode = mode;
                break;
        }
    }

    @Override
    public NbNetworkMode getNetworkMode() {
        return mNetworkMode;
    }

    abstract protected void registerNetworkMonitor();
}
