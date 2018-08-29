/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

/**
 * 基本コールバック。成功時は実行結果２個を返却する。
 * @since 7.0.0
 */
public interface NbCallback2<T, U> extends NbBaseCallback {
    /**
     * API呼び出しが成功した場合に呼び出される。
     * @param t パラメータ1
     * @param u パラメータ2
     */
    void onSuccess(T t, U u);
}
