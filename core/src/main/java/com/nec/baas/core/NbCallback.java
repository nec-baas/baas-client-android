/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

/**
 * 基本コールバック。成功時は実行結果１個を返却する。
 * @since 6.5.0
 */
public interface NbCallback<T> extends NbBaseCallback {
    /**
     * API呼び出しが成功した場合に呼び出される。
     * @param result 実行結果
     */
    void onSuccess(T result);
}
