/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.file;

import com.nec.baas.core.*;

/**
 * ファイルバケット取得用コールバック。
 * @since 1.0
 */
public interface NbFileBucketCallback extends NbCallback<NbFileBucket> {
    /**
     * ファイルバケットの取得に成功した場合に呼び出される。<br>
     * @param bucket 取得したファイルバケット。
     */
    void onSuccess(NbFileBucket bucket);
}
