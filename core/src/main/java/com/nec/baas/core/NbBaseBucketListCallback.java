/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;


import java.util.List;

/**
 * 共通バケット一覧取得用コールバック。
 * @since 1.0
 * @deprecated {@link NbCallback} で置き換え (v6.5.0)
 */
@Deprecated
public interface NbBaseBucketListCallback<T extends NbBaseBucket> extends NbCallback<List<T>> {
}
