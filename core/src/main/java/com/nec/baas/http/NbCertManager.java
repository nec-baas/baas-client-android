/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.http;

import com.nec.baas.util.NbLogger;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import org.apache.commons.codec.binary.Base64;

import javax.net.ssl.*;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 証明書管理クラス
 *
 * @since 6.5.0
 */
@Accessors(prefix = "m")
class NbCertManager {
    public static final String BEGIN_CERTIFICATE_DELIMITER = "-----BEGIN CERTIFICATE-----";
    public static final String END_CERTIFICATE_DELIMITER = "-----END CERTIFICATE-----";

    private static final NbLogger log = NbLogger.getLogger(NbCertManager.class);
    @Getter
    /* package */ KeyManager[] mKeyManagers;
    @Getter
    /* package */ X509TrustManager mTrustManager;

    public NbCertManager() {
    }

    /**
     * クライアント証明書用のKeyManagerFactoryを生成する
     *
     * @param clientCert クライアント証明書へのパス
     * @param password   クライアント証明書のインポート用パス
     * @throws IOException              証明書のパスが不正a
     * @throws GeneralSecurityException 証明書の設定に不備を検出
     * @since 6.5.0
     */
    public void configureKeyManager(InputStream clientCert, String password) throws IOException, GeneralSecurityException {
        KeyManagerFactory kmf;
        try {
            // パスワードはログに出力せず、SDK内のメンバ変数には格納しないこと
            char[] keyPass = password.toCharArray();

            KeyStore keyStore = getKeyStore("PKCS12");
            keyStore.load(clientCert, keyPass);

            kmf = getKeyManagerFactory(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keyPass);

            mKeyManagers = kmf.getKeyManagers();
        } catch (IOException | GeneralSecurityException e) {
            // InputStreamはコール元が提供しているためcloseしない
            log.warning("failed to loading client certificate : {0}", e.getMessage());
            throw e;
        }
    }

    /**
     * PrivateCA用のTrustManagerを生成する
     *
     * @param trustedCACert 信頼するCAの証明書
     * @throws IOException              証明書の読み出しに失敗
     * @throws GeneralSecurityException 証明書の検証に失敗
     * @since 6.5.0
     */
    public void configureTrustManager(InputStream trustedCACert) throws IOException, GeneralSecurityException {
        // システムデフォルトのTrustManagerを取得
        // Private CAの指定がない場合はdefaultを使用する
        X509TrustManager trustManagerConcat;
        X509TrustManager defaultTm;

        try {
            trustManagerConcat = defaultTm = getDefaultTrustManager();

            if (trustedCACert != null) {
                // private ca用のTrustManagerを取得
                X509TrustManager tmForPrivateCa = getPrivateTrustManager(trustedCACert);
                // デフォルトとPrivateCA用のTrustManagerを結合
                trustManagerConcat = getMergedTrustManager(tmForPrivateCa, defaultTm);
            }
        } catch (IOException | GeneralSecurityException e) {
            // InputStreamはコール元が提供しているためcloseしない
            log.warning("failed to load ca certificate : {0}", e.getMessage());
            throw e;
        }

        mTrustManager = trustManagerConcat;
    }

    /* --------------------------------------------------------------------------- */

    /**
     * KeyStoreの取得
     *
     * @param input 証明書データ(PEMフォーマット)
     * @return KeyStore
     * @throws IOException              証明書取得に失敗
     * @throws GeneralSecurityException KeyStoreの生成に失敗
     */
    KeyStore loadKeyStore(InputStream input) throws IOException, GeneralSecurityException {
        List<byte[]> rawPemList = getRawCertificates(input);
        if (rawPemList.size() == 0) {
            throw new IOException("there is no valid certificate. check input file");
        }

        KeyStore trustStore;
        try {
            trustStore = getKeyStore(KeyStore.getDefaultType());
            trustStore.load(null, null);
        } catch (IOException | GeneralSecurityException e) {
            log.warning("failed to get a KeyStore : {0}", e.getMessage());
            throw e;
        }

        for (byte[] pem : rawPemList) {
            InputStream certInputStream = null;
            try {
                certInputStream = new ByteArrayInputStream(pem);

                CertificateFactory certFactory = getCertificateFactory("X.509");
                X509Certificate cert = getCertificate(certFactory, certInputStream);
                String alias = cert.getSubjectX500Principal().getName();

                trustStore.setCertificateEntry(alias, cert);
            } catch (GeneralSecurityException e) {
                log.warning("failed to get a certificate entity: {0}", e.getMessage());
                // e.printStackTrace();
                throw e;
            } finally {
                if (certInputStream != null) {
                    certInputStream.close();
                }
            }
        }

        return trustStore;
    }

    /**
     * PEM証明書データから、バイナリ形式に変換
     *
     * @param input 証明書データ入力(PEMフォーマット)
     *              複数証明書に対応
     * @return バイナリ化した証明書データのリスト
     * @throws IOException 証明書の取得に失敗
     */
    List<byte[]> getRawCertificates(InputStream input) throws IOException {
        List<byte[]> rawPemList = new ArrayList<>();

        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(input));

            StringBuilder buf = new StringBuilder();
            String str;
            boolean beginCertData = false;
            while ((str = br.readLine()) != null) {
                if (str.startsWith(BEGIN_CERTIFICATE_DELIMITER)) {
                    beginCertData = true;
                } else if (str.startsWith(END_CERTIFICATE_DELIMITER)) {
                    // finish reading cert data
                    String pem = buf.toString();
                    byte[] rawPem = Base64.decodeBase64(pem.getBytes("UTF-8"));
                    rawPemList.add(rawPem);

                    buf = new StringBuilder(); // cleanup cert data
                    beginCertData = false;
                } else if (beginCertData) {
                    buf.append(str);
                } else {
                    // ignore: no begin header now
                }
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }

        return rawPemList;
    }

    /**
     * TrustManagerをマージする
     *
     * @param tm1 TrustManager1
     * @param tm2 TrustManager2
     * @return マージ後のTrustManager
     */
    X509TrustManager getMergedTrustManager(@NonNull X509TrustManager tm1, @NonNull X509TrustManager tm2) {

        final List<X509TrustManager> trustManagers = new ArrayList<>();
        trustManagers.add(tm1);
        trustManagers.add(tm2);

        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                // クライアントサイドでは未使用
                log.warning("checkClientTrusted authType:" + authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                boolean isTrusted = false;

                for (X509TrustManager tm : trustManagers) {
                    try {
                        tm.checkServerTrusted(chain, authType);
                        // 一方のTrustManagerでサーバが信頼できることが判明した場合、打ち切り
                        isTrusted = true;
                        break;
                    } catch (CertificateException e) {
                        log.fine("cannot trust server certificate by [" + tm.toString() + "] " + e.getMessage());
                    }
                }

                if (!isTrusted) {
                    // どちらのTrustManagerでも信頼できない証明書である場合
                    log.warning("cannot trust server certificate");
                    throw new CertificateException("cannot trust server certificate");
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                ArrayList<X509Certificate> mergedList = new ArrayList<>();
                // システムデフォルトのCA + PrivateCAを許容するIssuerと認定する
                for (X509TrustManager tm : trustManagers) {
                    X509Certificate[] trustedIssuers = tm.getAcceptedIssuers();
                    if (trustedIssuers != null) {
                        mergedList.addAll(Arrays.asList(trustedIssuers));
                    }
                }
                return mergedList.toArray(new X509Certificate[mergedList.size()]);
            }
        };
    }

    /* --------------------------------------------------------------------------- */

    X509TrustManager getDefaultTrustManager() throws GeneralSecurityException {
        TrustManagerFactory tmf;
        X509TrustManager defaultTm;

        try {
            // default TrustManager
            tmf = getDefaultTrustManagerFactory();
            initTrustManagerFactory(tmf, null);
        } catch (GeneralSecurityException e) {
            log.warning("failed to get a TrustManager for system default: {0}", e.getMessage());
            throw e;
        }

        defaultTm = getX509TrustManager(tmf);

        return defaultTm;
    }

    X509TrustManager getPrivateTrustManager(InputStream input) throws IOException, GeneralSecurityException {
        // private TrustManager
        KeyStore privateKeyStore = loadKeyStore(input);

        TrustManagerFactory tmf = getDefaultTrustManagerFactory();
        initTrustManagerFactory(tmf, privateKeyStore);

        return getX509TrustManager(tmf);
    }

    TrustManagerFactory getDefaultTrustManagerFactory() throws NoSuchAlgorithmException {
        TrustManagerFactory tmf;
        // default TrustManager
        tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        return tmf;
    }

    KeyStore getKeyStore(String keyStoreType) throws KeyStoreException {
        KeyStore trustStore;
        trustStore = KeyStore.getInstance(keyStoreType);

        return trustStore;
    }

    void initTrustManagerFactory(TrustManagerFactory trustManagerFactory, KeyStore keyStore) throws KeyStoreException {
        trustManagerFactory.init(keyStore);
    }

    KeyManagerFactory getKeyManagerFactory(String keyManagerType) throws NoSuchAlgorithmException {
        KeyManagerFactory kmf;
        kmf = KeyManagerFactory.getInstance(keyManagerType);

        return kmf;
    }

    CertificateFactory getCertificateFactory(String certificateFactoryType) throws CertificateException {
        return CertificateFactory.getInstance(certificateFactoryType);
    }

    X509Certificate getCertificate(CertificateFactory certificateFactory, InputStream rawCertInputStream) throws CertificateException {
        return (X509Certificate) certificateFactory.generateCertificate(rawCertInputStream);
    }

    X509TrustManager getX509TrustManager(TrustManagerFactory tmf) {
        TrustManager[] managers = tmf.getTrustManagers();

        for (TrustManager tm : managers) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        // never
        throw new IllegalStateException("no X509 TrustManager found: num of trustManagers: " + managers.length);
    }


}
