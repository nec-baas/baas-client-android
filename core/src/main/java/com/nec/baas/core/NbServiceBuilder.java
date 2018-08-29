/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import com.nec.baas.core.internal.*;
import com.nec.baas.util.*;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * サービスの生成用抽象クラス
 * @since 1.0
 */
@Accessors(fluent = true)
public abstract class NbServiceBuilder<SELF extends NbServiceBuilder<SELF>> {
    /**
     * テナントID
     */
    @Getter
    private String tenantId;

    /**
     * アプリケーションID
     */
    @Getter
    private String appId;

    /**
     * アプリケーションキー
     */
    @Getter
    private String appKey;

    /**
     * Endpoint URI
     */
    @Getter
    private String endPointUri;

    //DataSecurity
    @Getter @Setter
    private String deviceId;

    protected SELF myself;

    /**
     * コンストラクタ
     */
    protected NbServiceBuilder(Class<? extends NbServiceBuilder> selfType) {
        this.myself = (SELF)selfType.cast(this);
        this.endPointUri = NbConsts.DEFAULT_END_POINT_URI;
    }

    /**
     * テナントIDをセットする。
     * @param tenantId テナントID
     * @since 5.0.0
     */
    public SELF tenantId(String tenantId) {
        this.tenantId = tenantId;
        return myself;
    }


    /**
     * アプリケーションIDをセットする。
     * @param appId アプリケーションID
     * @since 5.0.0
     */
    public SELF appId(String appId) {
        this.appId = appId;
        return myself;
    }

    /**
     * アプリケーションキーをセットする。
     * @param appKey アプリケーションキー
     * @since 5.0.0
     */
    public SELF appKey(String appKey) {
        this.appKey = appKey;
        return myself;
    }

    /**
     * Endpoint URI をセットする。
     * @param endPointUri Endpoint URI
     * @since 5.0.0
     */
    public SELF endPointUri(String endPointUri) {
        this.endPointUri = endPointUri;
        return myself;
    }

    /**
     * Endpoint URI を設定する
     * @param argEndPointUri Endpoint URI
     * @return this
     * @deprecated {@link #endPointUri()} で置き換え
     */
    @Deprecated
    public SELF setEndPointUri(String argEndPointUri) {
        this.endPointUri = argEndPointUri;
        return myself;
    }

    /** @deprecated {@link #tenantId()} で置き換え */
    @Deprecated
    public String getTenantId() { return this.tenantId; }

    /** @deprecated {@link #appId()} で置き換え */
    @Deprecated
    public String getAppId() { return this.appId; }

    /** @deprecated {@link #appKey()} で置き換え */
    @Deprecated
    public String getAppKey() { return this.appKey; }

    /** @deprecated {@link #endPointUri()} で置き換え */
    @Deprecated
    public String getEndPointUri() { return this.endPointUri; }

    /**
     * NbService を生成する
     * @return NbService
     */
    public NbService build() {
        NbServiceImpl service = createNebulaService();


        service.initialize(tenantId, appId, appKey, endPointUri,
                createSessionToken(), createRestExecutorFactory());

        //DataSecurity
        service.setDeviceId(deviceId);

        return service;
    }

    /**
     * NbServiceImpl のインスタンスを生成する。
     * サブクラス側で変更可。
     * @return NbServiceImpl
     */
    protected NbServiceImpl createNebulaService() {
        return new NbServiceImpl();
    }

    /**
     * NbRestExecutorFactory を生成する。
     * @return NbRestExecutorFactory
     */
    abstract protected NbRestExecutorFactory createRestExecutorFactory();

    /**
     * NbSessionToken を生成する。
     * @return NbSessionToken
     */
    abstract protected NbSessionToken createSessionToken();
}
