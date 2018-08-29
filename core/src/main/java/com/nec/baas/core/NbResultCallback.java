/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

/**
 * API実行結果用コールバック。
 * 結果引数は無し。
 * @since 1.0
 */
public interface NbResultCallback extends NbBaseCallback {
    /**
     * APIの実行に成功した場合に呼び出される。
     */
    void onSuccess();
}
