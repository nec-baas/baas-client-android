/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.http;

import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.security.GeneralSecurityException;


/**
 * HttpClient のラッパクラス。シングルトン。
 *
 * @since 1.0
 */
public abstract class NbHttpClient {
    private static final NbHttpClient sNbHttpClient = new NbHttpClientImpl();

    protected boolean allowSelfSignedCertificate = false;

    /**
     * インスタンス(シングルトン)を取得する
     *
     * @return NbHttpClient
     */
    public static NbHttpClient getInstance() {
        return sNbHttpClient;
    }

    /**
     * プロキシサーバを設定する
     *
     * @param proxy
     */
    public abstract void setProxy(Proxy proxy);

    /**
     * HttpClient を作成する
     */
    public abstract void open();

    /**
     * HTTP Request を実行する
     *
     * @param request HTTPリクエスト
     * @return HTTPレスポンス
     * @throws IOException I/O例外
     * @throws IOException HttpClient が作成されていない(openが呼ばれていない)
     */
    public abstract Response executeRequest(Request request) throws IOException;

    /**
     * HttpClient をクローズする
     */
    public abstract void close();

    /**
     * SSL 自己署名証明書許可設定。
     * 許可する場合は true に設定する。デフォルトは false。
     *
     * @param allow 許可設定
     */
    public void setAllowSelfSignedCertificate(boolean allow) {
        this.allowSelfSignedCertificate = allow;
    }

    /**
     * クライアント証明書認証に使用する証明書を登録する
     * {@see #setAllowSelfSignedCertificate(boolean)}がtrueの場合、本設定は無効となる。
     *
     * @param clientCert    クライアント証明書のファイルパス(.p12形式: 秘密鍵+クライアント証明書)
     * @param password      クライアント証明書のインポート用パスワード
     *                      パスワード未設定の場合は""(空文字)を指定する。nullの場合は空文字とみなす
     * @param trustedCaCert 信頼するCAの証明書(.pem形式)。
     *                      主にPrivateCAを指定する。
     *                      "-----BEGIN CERTIFICATE-----","-----END CERTIFICATE-----" で囲まれた証明書を格納すること.
     *                      nullを設定した場合、システムデフォルトのTrustManagerを使用する。
     * @throws IOException              証明書が存在しない、証明書データが取得できない
     * @throws GeneralSecurityException 証明書の検証に問題が発生した
     * @since 6.5.0
     */
    public abstract void setClientCertificate(InputStream clientCert, String password, InputStream trustedCaCert) throws IOException, GeneralSecurityException;

    /**
     * クライアント証明書認証を無効にする
     * <p>
     * 証明書未登録の状態でコールした場合、何も行わない。
     * @since 6.5.0
     */
    public abstract void disableClientCertificate();

    /**
     * クライアント証明書の設定状況を確認する。
     *
     * @return クライアント証明書の設定状況
     * @since 6.5.0
     */
    public abstract boolean isClientCertSet();
}
