/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.core.*;

import java.util.List;

/**
 * オブジェクトバケット一覧取得用コールバック。
 * @since 1.0
 */
public interface NbObjectBucketListCallback extends NbCallback<List<NbObjectBucket>> {
    /**
     * オブジェクトバケットの取得に成功した場合に呼び出される。<br>
     * @param buckets 取得したオブジェクトバケット。
     */
    void onSuccess(List<NbObjectBucket> buckets);
}
