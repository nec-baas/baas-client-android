/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.http;

import com.nec.baas.core.internal.*;
import com.nec.baas.json.*;
import com.nec.baas.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;

/**
 * HTTPリクエストを生成するクラス
 * @since 1.0
 */
@Getter
@Accessors(prefix = "m")
public class NbHttpRequestFactory {
    private static final NbLogger log = NbLogger.getLogger(NbHttpRequestFactory.class);

    /** テナントID */
    @Setter
    private String mTenantId;

    /** アプリケーションID */
    @Setter
    private String mAppId;

    /** アプリケーションキー */
    @Setter
    private String mAppKey;

    /** エンドポイント URI */
    private String mEndPointUri;

    /** セッショントークン */
    @Setter
    private NbSessionToken mSessionToken;

    protected static final String HEADER_APP_ID = "X-Application-Id";
    protected static final String HEADER_APP_KEY = "X-Application-Key";
    protected static final String HEADER_SESSION_TOKEN = "X-Session-Token";
    protected static final String HEADER_USER_AGENT = "User-Agent";
    protected static final String DEFAULT_USER_AGENT = "baas java sdk";
    protected static final String PATH_API_VERSION = "1";

    /**
     * コンストラクタ
     * @param tenantId テナントID
     * @param appId アプリケーションID
     * @param appKey アプリケーションキー
     * @param endPointUri エンドポイントURI
     * @param session セッショントークン保持クラス
     */
    public NbHttpRequestFactory(String tenantId, String appId, String appKey,
                           String endPointUri, NbSessionToken session) {
        setTenantId(tenantId);
        setAppId(appId);
        setAppKey(appKey);
        setEndPointUri(endPointUri);
        mSessionToken = session;
    }

    /**
     * エンドポイントURIを設定する
     * @param endPointUri エンドポイントURI
     */
    public void setEndPointUri(String endPointUri) {
        this.mEndPointUri = endPointUri;
        if (!mEndPointUri.endsWith("/")) {
            mEndPointUri = mEndPointUri.concat("/");
        }
    }

    /**
     * ログイン状態を確認する
     * @return ログイン中であれば true
     */
    public boolean isLoggedIn() {
        //ログインチェック
        boolean result = false;
        if (mSessionToken.getSessionToken() != null) {
            result = true;
        }
        return result;
    }

    /**
     * GETリクエスト生成
     * @param path APIパス
     * @return Builder
     */
    public Builder get(String path) {
        return request("GET", path);
    }

    /**
     * POSTリクエスト生成
     * @param path APIパス
     * @return Builder
     */
    public Builder post(String path) {
        return request("POST", path);
    }

    /**
     * PUTリクエスト生成
     * @param path APIパス
     * @return Builder
     */
    public Builder put(String path) {
        return request("PUT", path);
    }

    /**
     * DELETEリクエスト生成
     * @param path APIパス
     * @return Builder
     */
    public Builder delete(String path) {
        return request("DELETE", path);
    }

    /**
     * リクエスト生成
     * @param method メソッド
     * @param path APIパス
     * @return Builder
     */
    public Builder request(String method, String path) {
        return new Builder().method(method).path(path);
    }

    /**
     * Httpリクエストビルダ
     */
    public class Builder {
        private Request.Builder mBuilder;
        private String mMethod;
        private RequestBody mRequestBody;
        private String mPath;
        private Map<String,String> mParams = null;
        private boolean sessionSpecified = false;
        private boolean mUserAgentSet = false;

        protected Builder() {
            mBuilder = new Request.Builder();
        }

        protected Builder method(String method) {
            mMethod = method;
            return this;
        }

        /**
         * Http Requestを生成する
         * SessionTokenの指定がない場合、Optionalが指定されたものとみなす
         * @return Request
         */
        public Request build() {
            switch (mMethod) {
                case "GET":
                    mBuilder.get();
                    break;
                case "POST":
                    mBuilder.post(mRequestBody);
                    break;
                case "PUT":
                    mBuilder.put(mRequestBody);
                    break;
                case "DELETE":
                    mBuilder.delete();
                    break;
            }
            setUrlParams();
            setCommonHeader();

            if (!sessionSpecified) {
                // デフォルトは Optional。
                sessionOptional();
            }

            return mBuilder.build();
        }

        /**
         * パスを指定する。パスの値は /api/1/{tenantId} より後方部分。
         * @param path
         * @return
         */
        public Builder path(String path) {
            mPath = path;
            return this;
        }

        /**
         * URL パス要素を末尾に連結する。URIエンコードはされない。
         * パス区切り(/)は必要に応じて自動挿入される。
         * @param path パス
         * @return this
         */
        public Builder addPathRaw(String path) {
            if (mPath.endsWith("/")) {
                if (path.startsWith("/")) {
                    mPath = mPath + path.substring(1);
                } else {
                    mPath = mPath + path;
                }
            } else {
                if (path.startsWith("/")) {
                    mPath = mPath + path;
                } else {
                    mPath = mPath + "/" + path;
                }
            }
            return this;
        }

        /**
         * URL パス要素を末尾に連結する。URIエンコードされる。
         * @param component パスコンポーネント
         * @return this
         */
        public Builder addPathComponent(String component) {
            String encoded = NbUtil.encodeUrl(component);
            if (mPath.endsWith("/")) {
                mPath = mPath + encoded;
            } else {
                mPath = mPath + "/" + encoded;
            }
            return this;
        }

        /**
         * クエリパラメータを設定する(すべて上書き)。
         * @param params クエリパラメータ
         * @return this
         */
        public Builder params(Map<String,String> params) {
            mParams = params;
            return this;
        }

        /**
         * クエリパラメータを追加する。
         * @param name パラメータ名
         * @param value パラメータ
         * @return this
         */
        public Builder param(String name, String value) {
            if (mParams == null) {
                mParams = new HashMap<>();
            }
            mParams.put(name, value);
            return this;
        }

        /**
         * Body を文字列で設定する。Content-Type は application/json 固定。
         * @param body Body
         * @return this
         */
        public Builder body(NbJSONObject body) {
            if (body != null) {
                body(body.toJSONString());
            }
            return this;
        }

        /**
         * Body を文字列で設定する。Content-Type は application/json 固定。
         * @param body Body
         * @return this
         */
        public Builder body(String body) {
            if (body != null) {
                mRequestBody = RequestBody.create(NbConsts.MEDIA_TYPE_JSON, body);
            }
            return this;
        }

        /**
         * Body を InputStream で設定する
         * @param stream Body
         * @param length Content Length。不明時は -1 を指定。この場合は Chunked Encoding となる。
         * @param contentType Content Type
         * @return this
         */
        public Builder body(final InputStream stream, final long length, final String contentType) {
            final MediaType mediaType = MediaType.parse(contentType);
            if (stream != null) {
                mRequestBody = new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return mediaType;
                    }

                    @Override
                    public long contentLength() {
                        return length;
                    }

                    @Override
                    public void writeTo(BufferedSink sink) throws IOException {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = stream.read(buffer)) > 0) {
                            sink.write(buffer, 0, len);
                        }
                    }
                };
            }
            return this;
        }

        /**
         * HTTPヘッダを設定する。
         * @param headers ヘッダ
         * @return this
         */
        public Builder headers(Map<String,String> headers) {
            if (headers != null) {
                // User-Agentの設定有無を確認
                if (headers.containsKey(HEADER_USER_AGENT)) {
                    mUserAgentSet = true;
                }
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    mBuilder.addHeader(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        /**
         * HTTPヘッダを追加する。
         * @param name ヘッダ名
         * @param value ヘッダ
         * @return this
         */
        public Builder header(String name, String value) {
            // User-Agentの設定有無を確認
            if (name.equals(HEADER_USER_AGENT)) {
                mUserAgentSet = true;
            }
            mBuilder.addHeader(name, value);
            return this;
        }

        /**
         * セッショントークンヘッダを設定する。
         * セッショントークンがない(未ログイン)の場合は SecurityException が
         * スローされる。
         * @return this
         * @throws SecurityException 未ログイン
         */
        public Builder sessionRequired() throws SecurityException {
            String token = mSessionToken.getSessionToken();
            if (token != null) {
                mBuilder.addHeader(HEADER_SESSION_TOKEN, token);
            } else {
                throw new SecurityException("Not logged in");
            }
            sessionSpecified = true;
            return this;
        }

        /**
         * セッショントークンがあればヘッダを設定する。
         * @return this
         */
        public Builder sessionOptional() {
            String token = mSessionToken.getSessionToken();
            if (token != null) {
                mBuilder.addHeader(HEADER_SESSION_TOKEN, token);
            }
            sessionSpecified = true;
            return this;
        }

        /**
         * セッショントークンを設定しない。
         */
        public Builder sessionNone() {
            sessionSpecified = true;
            return this;
        }

        /**
         * 共通ヘッダ(X-Application-Id/Key, X-Session-Token)を設定する
         */
        protected void setCommonHeader() {
            mBuilder.addHeader(HEADER_APP_ID, mAppId);
            mBuilder.addHeader(HEADER_APP_KEY, mAppKey);
            //User-Agentがヘッダに設定されていなければデフォルト値をセットする
            if (!mUserAgentSet) {
                mBuilder.addHeader(HEADER_USER_AGENT, DEFAULT_USER_AGENT);
            }
        }

        /**
         * URL / クエリパラメータ設定
         */
        private void setUrlParams() {
            log.fine("setUrlParams() request=" + mBuilder);
            HttpUrl base = HttpUrl.parse(mEndPointUri);
            if (base == null) {
                throw new IllegalArgumentException("Invalid Request URI: " + mEndPointUri);
            }
            HttpUrl.Builder builder = base.newBuilder();
            builder.addPathSegment(PATH_API_VERSION);
            builder.addPathSegment(mTenantId);
            final String path = mPath.startsWith("/") ? mPath.substring(1) : mPath;
            builder.addEncodedPathSegments(path); // path は URL エンコード済み
            if (mParams != null) {
                for (Map.Entry<String,String> entry : mParams.entrySet()) {
                    // #5309: okhttp3 は '{}' などを encode してくれないため、
                    // 自前で encode する。
                    String key = urlEncode(entry.getKey());
                    String value = urlEncode(entry.getValue());
                    builder.addEncodedQueryParameter(key, value);
                    //builder.addQueryParameter(entry.getKey(), entry.getValue());
                }
            }
            mBuilder.url(builder.build());
        }

        private String urlEncode(String s) {
            if (s == null) return null;
            try {
                return URLEncoder.encode(s, "UTF-8");
            } catch (UnsupportedEncodingException never) {
                throw new RuntimeException(never);
            }
        }
    }
}
