/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

/**
 * システム固有情報へアクセスするためのインタフェース.<br>
 * @since 1.0
 */
public interface SystemManager {

    /**
     * キャッシュファイル保存先ディレクトリパスの取得
     * @return String ディレクトリパス
     */
    String getCacheFilePath();
}
