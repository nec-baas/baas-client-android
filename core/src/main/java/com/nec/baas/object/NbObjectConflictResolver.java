/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.offline.*;

/**
 * オブジェクトデータの衝突を解決するためのインタフェース。<br>
 * @since 1.0
 */
public interface NbObjectConflictResolver {
    /**
     * 衝突解決を指示する。
     * @param bucketName オブジェクトが所属するバケットの名前
     * @param objectId 衝突したオブジェクトのID
     * @param resolve 解決方法。CLIENT, SERVER のいずれか。
     */
    void resolveConflict(String bucketName, String objectId, NbConflictResolvePolicy resolve);
}
