/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

/**
 * 共通コールバック
 * @since 1.0
 */
public interface NbBaseCallback {
    /**
     * APIの実行に失敗した場合に呼び出される。
     * @param statusCode APIの実行に失敗した原因を表すステータスコード ({@link NbStatus})
     * @param errorInfo エラー詳細情報
     * @see NbStatus
     */
    void onFailure(int statusCode, NbErrorInfo errorInfo);
}
