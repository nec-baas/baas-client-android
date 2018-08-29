/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

/**
 * 実行結果1個 + カウントを返すコールバック
 * @since 6.5.0
 */
public interface NbCountCallback<T> extends NbBaseCallback {
    /**
     * API呼び出しに成功した場合に呼び出される。
     * 件数取得を行った場合はcountに件数が格納される。
     * @param result 実行結果
     * @param count 件数取得を行った際の件数。件数不明時は -1。
     */
    void onSuccess(T result, int count);
}
