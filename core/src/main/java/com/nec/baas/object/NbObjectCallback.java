/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.core.*;

import java.util.List;

/**
 * NbObject(List)取得用コールバック。
 * @since 1.0
 * @deprecated {@link NbCallback}, {@link NbCountCallback} で置き換え (v6.5.0)
 */
@Deprecated
public interface NbObjectCallback extends NbBaseCallback {
    /**
     * NbObjectの取得に成功した場合に呼び出される。
     * <p>
     * 件数取得を行った場合はcountに件数が格納される。
     * @param objects NebulaObjectのリスト。
     * @param count 件数取得を行った際の件数。件数情報が無い場合は null。
     * @see NbObject
     * @see NbObjectBucket#query(NbQuery, NbObjectCallback)
     */
    void onSuccess(List<NbObject> objects, Number count);
}
