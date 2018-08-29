/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.file;

import com.nec.baas.core.*;

import java.util.List;

/**
 * ファイルのメタデータ取得用コールバック。
 * @since 1.0
 * @deprecated {@link NbCallback} で置き換え (v6.5.0)
 */
@Deprecated
public interface NbFileMetadataCallback extends NbCallback<List<NbFileMetadata>> {
    /**
     * ファイルのメタデータの取得に成功した場合に呼び出される。<br>
     * @param metas 取得したメタデータのリスト。
     */
    void onSuccess(List<NbFileMetadata> metas);
}
