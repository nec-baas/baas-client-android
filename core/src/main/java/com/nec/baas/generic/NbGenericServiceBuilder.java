/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.generic;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.generic.internal.*;
import com.nec.baas.http.*;

/**
 * 汎用 Service Builder 実装
 * @since 1.0
 */
public class NbGenericServiceBuilder extends NbServiceBuilder<NbGenericServiceBuilder> {

    /**
     * コンストラクタ。
     * テナントID/アプリID/アプリキー/EndPoint URI は、
     * {@link #tenantId()}, {@link #appId()}, {@link #appKey()}, {@link #endPointUri()}
     * で設定すること。
     */
    public NbGenericServiceBuilder() {
        super(NbGenericServiceBuilder.class);
    }

    /**
     * コンストラクタ
     * @param tenantId テナントID
     * @param appId アプリケーションID
     * @param appKey アプリケーションキー
     * @deprecated {@link #NbGenericServiceBuilder()} で置き換え
     */
    @Deprecated
    public NbGenericServiceBuilder(String tenantId, String appId, String appKey) {
        super(NbGenericServiceBuilder.class);
        tenantId(tenantId).appId(appId).appKey(appKey);
    }

    @Override
    protected NbSessionToken createSessionToken() {
        return new NbGenericSessionToken();
    }

    @Override
    protected NbRestExecutorFactory createRestExecutorFactory() {
        return new GenericRestExecutorFactory();
    }

    private static class GenericRestExecutorFactory implements NbRestExecutorFactory {
        private NbHttpClient mHttpClient;

        public GenericRestExecutorFactory() {
            mHttpClient = NbHttpClient.getInstance();
        }

        @Override
        public NbRestExecutor create() {
            return new NbGenericRestExecutor(mHttpClient);
        }
    }
}
