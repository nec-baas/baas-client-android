/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.file;

import com.nec.baas.core.*;

import java.util.List;

/**
 * ファイルバケット一覧取得用コールバック。
 * @since 1.0
 */
public interface NbFileBucketListCallback extends NbCallback<List<NbFileBucket>> {
    /**
     * ファイルバケットの取得に成功した場合に呼び出される。<br>
     * @param buckets 取得したファイルバケット。
     */
    void onSuccess(List<NbFileBucket> buckets);
}
