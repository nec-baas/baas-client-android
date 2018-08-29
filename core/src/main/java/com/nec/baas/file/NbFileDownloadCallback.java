/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.file;


import com.nec.baas.core.*;

/**
 * ファイルダウンロード用コールバック。
 * <p>
 * 実行結果にはダウンロードしたファイルのパスが渡される。
 * @since 1.0
 */
public interface NbFileDownloadCallback extends NbCallback<String> {
    /**
     * ファイルのダウンロードに成功した場合に呼び出される。<br>
     * @param filePath ダウンロードしたファイルのパス。
     */
    void onSuccess(String filePath);
}
