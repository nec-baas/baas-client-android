/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

/**
 * 共通バケット取得用コールバック。
 * @since 1.0
 * @deprecated {@link NbCallback} で置き換え (v6.5.0)
 */
@Deprecated
public interface NbBaseBucketCallback<T extends NbBaseBucket> extends NbCallback<T> {
    /**
     * バケットの取得に成功した場合に呼び出される。<br>
     * @param bucket 取得したバケット。
     */
    void onSuccess(T bucket);
}
