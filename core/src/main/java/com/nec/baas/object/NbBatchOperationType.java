/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

/**
 * バッチ処理タイプ<br>
 * @since 3.0.0
 */
public enum NbBatchOperationType {
    /**
     * 新規追加
     */
    INSERT(0),
    /**
     * 上書き更新
     */
    UPDATE(1),
    /**
     * 削除
     */
    DELETE(2);

    public final int id;

    NbBatchOperationType(final int id) {
        this.id = id;
    }

}
