/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.core.*;

/**
 * オブジェクトバケット取得用コールバック。
 * @since 1.0
 */
public interface NbObjectBucketCallback extends NbCallback<NbObjectBucket> {
    /**
     * オブジェクトバケットの取得に成功した場合に呼び出される。<br>
     * @param bucket 取得したオブジェクトバケット。
     */
    void onSuccess(NbObjectBucket bucket);
}
