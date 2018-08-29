/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Android 用 NbServiceImpl。Context 保持などを行う点が異なる。
 */
public class NbAndroidService extends NbServiceImpl {
    private Context mContext;
    private static Handler sHandler;
    private static Thread sMainThread;

    static {
        Looper mainLooper = Looper.getMainLooper();
        sHandler = new Handler(mainLooper);
        sMainThread = mainLooper.getThread();
    }

    /** {@inheritDoc} */
    public NbAndroidService() {
        super();
    }

    /**
     * Context (Application Context) を取得する
     * @return Context
     */
    public Context getContext() {
        return mContext;
    }

    // 内部用
    public void _setContext(Context context) {
        mContext = context;
    }

    /** {@inheritDoc} */
    @Override
    public void runOnUiThreadInstance(final Runnable r) {
        if (Thread.currentThread() == sMainThread) {
            r.run();
        } else {
            sHandler.post(r);
        }
    }
}
