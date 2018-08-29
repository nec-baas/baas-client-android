/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import com.nec.baas.core.NbRestExecutor;

/**
 * REST API を実行するクラス RestExecutor を生成するファクトリンターフェース。
 * @since 1.0
 */
public interface NbRestExecutorFactory {
    /**
     * RestExecutor を生成する
     * @return NbRestExecutor
     */
    NbRestExecutor create();
}
