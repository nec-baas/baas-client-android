/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import android.content.Context;

/**
 * Android用RestExecutorクラスのファクトリクラス
  * @since 1.0
 */
public class NbAndroidRestExecutorFactory implements NbRestExecutorFactory {
    private Context mContext;

    /**
     * コンストラクタ
     */
    public NbAndroidRestExecutorFactory(Context context) {
        mContext = context;
    }

    @Override
    public com.nec.baas.core.NbRestExecutor create() {
        return new NbAndroidAsyncRestExecutor(mContext);
    }

}
