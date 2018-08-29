/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import com.nec.baas.util.NbLogger;
import lombok.NonNull;

/**
 * 設定管理クラス。
 *
 * <p>本クラスはスレッドセーフである。</p>
 *
 * @since 1.0
 */
public final class NbSetting {
    private static final NbLogger log = NbLogger.getLogger(NbSetting.class);
    
    private NbSetting() { }

    private static NbOperationMode mOperationMode = NbOperationMode.PRODUCTION;

    /**
     * 起動モードを取得する。
     * @return mOperationMode
     */
    public static synchronized NbOperationMode getOperationMode() {
        return mOperationMode;
    }

    /**
     * 起動モードを設定する。
     * @param  operationMode 設定する起動モード
     */
    public static synchronized void setOperationMode(@NonNull NbOperationMode operationMode) {
        log.fine("setOperationMode() <start> operationMode=" + operationMode);
        mOperationMode = operationMode;
    }

    /**
     * 起動モードを設定する。
     * @param  operationMode 設定する起動モード
     * @deprecated {@link #setOperationMode(NbOperationMode)} で置き換え
     */
    @Deprecated
    public static synchronized void setOperationMode(int operationMode) {
        setOperationMode(NbOperationMode.fromInt(operationMode));
    }

}
