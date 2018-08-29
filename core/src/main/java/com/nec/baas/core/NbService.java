/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import com.nec.baas.core.internal.*;
import com.nec.baas.file.*;
import com.nec.baas.http.*;
import com.nec.baas.object.*;
import com.nec.baas.object.internal.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.GeneralSecurityException;

/**
 * MBaaS 機能を提供するメインクラス。
 * <p>
 * {@link NbServiceBuilder#build()}でインスタンスを生成する。
 * マルチテナントモードに設定しない限り、アプリケーションは本インスタンスをシングルトンとして扱うべきである。
 *
 * @since 1.0
 */
public abstract class NbService implements NbServiceOffline {
    /**
     * シングルトンインスタンス
     */
    protected static NbServiceImpl sInstance;

    /**
     * マルチテナント有効フラグ(デフォルト無効)
     */
    protected static boolean sIsMultiTenantEnabled = false;

    /**
     * Strict Singleton モード(デフォルト有効)
     */
    protected static boolean sStrictSingleton = true;

    /**
     * シングルトンインスタンスを取得する。
     * インスタンスは事前に NbServiceBuilder を使用して作成しておく必要がある。
     *
     * @return NbService インスタンス
     * @throws java.lang.IllegalStateException インスタンスが作成されていない
     */
    public static NbService getInstance() {
        if (sIsMultiTenantEnabled) {
            throw new IllegalStateException("Can't get service singleton in multi tenant mode.");
        }
        if (sInstance == null) {
            throw new IllegalStateException("NbService is not created.");
        }
        return sInstance;
    }

    /**
     * マルチテナントモードの有効設定を行う。
     * <p>
     * マルチテナント有効時は、シングルトンインスタンス取得時に IllegalStateException がスローされる。
     * マルチテナント非対応API発行時も同様。
     * </p>
     * <p>
     * マルチテナント無効に明示的に設定した場合、NbService のインスタンスは厳密に１つしか作成されない。
     * (Strict Singleton モード。複数作成すると例外がスローされる)。
     * </p>
     * <p>
     * なお、マルチテナントモードを使用することができるのは {@link com.nec.baas.generic.NbGenericServiceBuilder} だけである。
     * {@link com.nec.baas.core.NbAndroidServiceBuilder} では使用不可。
     * </p>
     *
     * @param enabled 有効
     */
    public static void enableMultiTenant(boolean enabled) {
        sIsMultiTenantEnabled = enabled;
        sStrictSingleton = !enabled;
    }

    /**
     * REST API 呼び出しにおける HTTP プロキシサーバを設定する。
     * <p>
     * 本設定後、すべての REST API 呼び出しは指定したプロキシサーバを使用する。
     * なお、本設定は全 NbService で共通である。
     *
     * @param host プロキシホスト名。null に指定した場合はプロキシサーバを解除する。
     * @param port プロキシポート番号
     * @since 6.5.0
     */
    public static void setProxy(String host, int port) {
        Proxy proxy = null;
        if (host != null) {
            proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        }
        NbHttpClient.getInstance().setProxy(proxy);
    }

    /**
     * クライアント証明書認証に使用する証明書を登録する
     * <p>
     * 本設定後、サーバが対応する場合にクライアント証明書による認証が可能となる。
     * 本設定は全 NbService で共通である。
     * {@see NbHttpClient#setAllowSelfSignedCertificate(boolean)}がtrueの場合、本設定は無効となる。
     *
     * @param clientCert    クライアント証明書(.p12形式: 秘密鍵+クライアント証明書)
     * @param password      クライアント証明書のインポート用パスワード
     *                      パスワード未設定の場合は""(空文字)を指定する。nullの場合は空文字とみなす
     * @param trustedCaCert 信頼するCAの証明書(.pem形式)。通常PrivateCAの証明書を指定する。
     *                      "-----BEGIN CERTIFICATE-----","-----END CERTIFICATE-----" で囲まれた部分を証明書として使用する。
     *                      nullを設定した場合、システムデフォルトのTrustManagerを使用する。
     * @throws IOException              証明書が存在しない、証明書データが取得できない
     * @throws GeneralSecurityException 証明書の検証に問題が発生した
     * @throws IllegalArgumentException クライアント証明書、CA証明書がどちらも指定されなかった
     * @since 6.5.0
     */
    public static void setClientCertificate(InputStream clientCert, String password, InputStream trustedCaCert) throws IOException, GeneralSecurityException {
        if (clientCert == null && trustedCaCert == null) {
            throw new IllegalArgumentException("both clientCert and trustCaCert are null");
        }
        NbHttpClient.getInstance().setClientCertificate(clientCert, password, trustedCaCert);
    }

    /**
     * クライアント証明書認証を無効にする
     * <p>
     * 証明書未登録の状態でコールした場合、何も行わない。
     * 本設定は全 NbService で共通である。
     * @since 6.5.0
     */
    public static void disableClientCertificate() {
        NbHttpClient.getInstance().disableClientCertificate();
    }

    /**
     * クライアント証明書の設定状況を確認する。
     *
     * @return クライアント証明書の設定状況
     * @since 6.5.0
     */
    public static boolean isClientCertSet() {
        return NbHttpClient.getInstance().isClientCertSet();
    }

    /**
     * NbObjectBucketManagerを取得する。
     * オブジェクトストレージの機能を利用する際に使用する。
     *
     * @return オブジェクトバケットの管理を行うためのオブジェクト。
     * @see NbObjectBucketManagerImpl
     */
    public abstract NbObjectBucketManager objectBucketManager();

    /**
     * NbFileBucketManagerを取得する。
     * ファイルストレージの機能を利用する際に使用する。
     *
     * @return ファイルバケットの管理を行うためのオブジェクト。
     * @see NbFileBucketManager
     */
    public abstract NbFileBucketManager fileBucketManager();

    /**
     * NebulaServiceの終了。
     * MBaaS機能の利用が完了した際に使用する。
     * サービスの終了処理を行う。
     */
    public abstract void finish();

    /**
     * テナントIDを設定する(NbService生成後に変更する場合に使用する)。
     * 本APIはFileStorage系クラスでは正しく動作しない。
     *
     * @param tenantId テナントID
     */
    public abstract void setTenantId(String tenantId);

    /**
     * アプリケーションIDを設定する(NbService生成後に変更する場合に使用する)。
     * 本APIはFileStorage系クラスでは正しく動作しない。
     *
     * @param appId アプリケーションID
     */
    public abstract void setAppId(String appId);

    /**
     * アプリケーションキーを設定する(NbService生成後に変更する場合に使用する)。
     * 本APIはFileStorage系クラスでは正しく動作しない。
     *
     * @param appKey アプリケーションキー
     */
    public abstract void setAppKey(String appKey);

    /**
     * エンドポイントURIを設定する(NbService生成後に変更する場合に使用する)。
     * 本APIはFileStorage系クラスでは正しく動作しない。
     *
     * @param endPointUri エンドポイントURI
     */
    public abstract void setEndPointUri(String endPointUri);

    /**
     * HTTP リクエストファクトリを取得する
     * @return NbHttpRequestFactory
     * @since 6.5.0
     */
    public abstract NbHttpRequestFactory getHttpRequestFactory();

    /**
     * REST Executor を生成する
     * @return NbRestExecutor
     * @since 6.5.0
     */
    public abstract NbRestExecutor createRestExecutor();
}
