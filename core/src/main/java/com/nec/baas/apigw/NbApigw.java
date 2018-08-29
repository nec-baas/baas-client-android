/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */
package com.nec.baas.apigw;

import com.nec.baas.core.NbCallback2;
import com.nec.baas.core.NbRestResponseHandler;
import com.nec.baas.core.NbService;
import com.nec.baas.core.internal.NbServiceImpl;
import com.nec.baas.core.internal.NbSimpleRestResponseHandler;
import com.nec.baas.http.NbHttpRequestFactory;
import com.nec.baas.json.NbJSONObject;
import com.nec.baas.util.NbLogger;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.NonNull;
import okhttp3.Request;
import okhttp3.Response;

/**
 * APIゲートウェイ
 * @since 7.0.0
 */
@Getter
public class NbApigw {
    private static final NbLogger log = NbLogger.getLogger(NbApigw.class);
    private NbServiceImpl mService = null;
    private Request mRequest = null;

    /**
     * コンストラクタ
     * @param service NbService
     * @param request HTTPリクエスト
     */
    private NbApigw(NbService service, Request request) {
        mService = (NbServiceImpl)service;
        mRequest = request;
    }

    /**
     * リクエスト builder を生成する
     * @param apiname API名
     * @return NbApigw.Builder
     */
    public static NbApigw.Builder builder(String apiname) {
        return new Builder(null, apiname);
    }

    /**
     * リクエスト builder を生成する
     * @param service NbService
     * @param apiname API名
     * @return NbApigw.Builder
     */
    public static NbApigw.Builder builder(NbService service, String apiname) {
        return new Builder(service, apiname);
    }

    /**
     * 結果をJSONで取得するAPIを実行する。
     * @param callback コールバック
     */
    public void executeJSON(@NonNull final NbCallback2 callback) {
        log.fine("executeJSON() <start>");

        if (!isContentType()) {
            throw new IllegalStateException("ContentType is null");
        }

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback) {
            @Override
            public void onSuccess(Response response, NbJSONObject json) {
                callback.onSuccess(json, response);
            }
        };

        mService.createRestExecutor().executeRequest(mRequest, handler);

        log.fine("executeJSON() <end>");
    }

    /**
     * 結果をStreamで取得するAPIを実行する。
     * @param callback コールバック
     */
    public void executeStream(@NonNull final NbCallback2 callback) {
        log.fine("executeStream() <start>");

        if (!isContentType()) {
            throw new IllegalStateException("ContentType is null");
        }

        NbRestResponseHandler handler = new NbApigwRestResponseHandler(callback) {
            @Override
            public void onSuccess(InputStream inputStream, Response response) {
                callback.onSuccess(inputStream, response);
            }
        };

        mService.createRestExecutor().executeRequest(mRequest, handler);

        log.fine("executeStream() <end>");
    }

    /**
     * bodyとContent-Typeの有無をチェックする。
     * @return チェック結果
     */
    private boolean isContentType() {

        if (mRequest.method().equals("POST") || mRequest.method().equals("PUT")) {
            if (mRequest.body() != null && mRequest.headers().get("Content-Type") == null) {
                return false;
            }
        }

        return true;
    }

    /**
     * APIゲートウェイリクエストビルダ
     * @since 7.0.0
     */
    public static class Builder {
        private static final String APIGW_PATH = "/api";
        private NbService mService = null;
        private String mMethod = null;
        private String mApiname = null;
        private String mSubpath = null;
        private Map<String,String> mHeaders = null;
        private Map<String,String> mParams = null;
        private String mContent = null;

        private BodyType mBodyType = BodyType.NONE;
        private String mBodyString = null;
        private NbJSONObject mBodyJson = null;
        private InputStream mBodyStream = null;
        private long mContentLength = 0;
        private String mContentType = null;

        private enum BodyType { NONE, JSON, STRING, STREAM }

        /**
         * コンストラクタ
         * @param service NbService
         * @param apiname API名
         */
        private Builder(NbService service, @NonNull String apiname) {
            log.fine("Builder() <start>");

            if (service == null) {
                service = NbService.getInstance();
            }

            mService = service;
            mApiname = apiname;

            log.fine("Builder() <end>");
        }

        /**
         * GETメソッドを指定する。
         */
        public Builder get() {
            mMethod = "GET";
            return this;
        }

        /**
         * PUTメソッドを指定する。
         */
        public Builder put() {
            mMethod = "PUT";
            return this;
        }

        /**
         * POSTメソッドを指定する。
         */
        public Builder post() {
            mMethod = "POST";
            return this;
        }

        /**
         * DELETEメソッドを指定する。
         */
        public Builder delete() {
            mMethod = "DELETE";
            return this;
        }

        /**
         * サブパスを指定する。
         * サブパスは URL エンコードされないので注意すること。
         * @param subpath サブパス
         * @return this
         */
        public Builder subpath(String subpath) {
            if (subpath != null) {
                mSubpath = subpath;
            }
            return this;
        }

        /**
         * ヘッダを追加する。
         * @param name ヘッダ名
         * @param value ヘッダ値
         * @return this
         */
        public Builder header(String name, String value) {
            if (name != null && !name.isEmpty() && value != null) {
                if (mHeaders == null) {
                    mHeaders = new HashMap<>();
                }
                mHeaders.put(name, value);
            }
            return this;
        }

        /**
         * Content-Type を指定する。
         * @param contentType Content-Type
         * @return this
         */
        public Builder contentType(String contentType) {
            if (contentType != null) {
                mContent = contentType;
            }
            return this;
        }

        /**
         * パラメータを追加する。
         * @param name パラメータ名
         * @param value パラメータ値
         * @return this
         */
        public Builder param(String name, String value) {
            if (name != null && value != null) {
                if (mParams == null) {
                    mParams = new HashMap<>();
                }
                mParams.put(name, value);
            }
            return this;
        }

        /**
         * JSON 形式で Body を指定する。
         * @param body JSON
         * @return this
         */
        public Builder body(NbJSONObject body) {
            mBodyType = BodyType.JSON;
            mBodyJson = body;
            return this;
        }

        /**
         * 文字列形式で Body を指定する。
         * @param body Body
         * @return this
         */
        public Builder body(String body) {
            mBodyType = BodyType.STRING;
            mBodyString = body;
            return this;
        }

        /**
         * InputStream で body を指定する。
         * @param stream Stream
         * @param contentLength Content-Length。不明時は -1。この場合は chunked encoding となる。
         * @param contentType Content-Type
         * @return this
         */
        public Builder body(InputStream stream, long contentLength, String contentType) {
            if (contentType != null) {
                mBodyType = BodyType.STREAM;
                mBodyStream = stream;
                mContentLength = contentLength;
                mContentType = contentType;

                this.contentType(contentType);
            }
            return this;
        }

        /**
         * NbApigw を build する。
         * @return NbApigw
         */
        public NbApigw build() {

            if (mMethod == null) {
                throw new IllegalStateException("Method is null");
            }

            NbHttpRequestFactory.Builder requestBuilder = mService.getHttpRequestFactory()
                                      .request(mMethod, APIGW_PATH)
                                      .addPathComponent(mApiname);

            // サブパスを追加する
            if (mSubpath != null) {
                requestBuilder.addPathRaw(mSubpath);
            }

            // POST/PUT時
            if (mMethod == "POST" || mMethod == "PUT") {

                // ボディを追加する
                switch (mBodyType) {
                    case JSON:
                        requestBuilder.body(mBodyJson);
                        break;
                    case STRING:
                        requestBuilder.body(mBodyString);
                        break;
                    case STREAM:
                        requestBuilder.body(mBodyStream, mContentLength, mContentType);
                        break;
                    default:
                        break;
                }

            }

            // パラメータを追加する
            if (mParams != null) {
                requestBuilder.params(mParams);
            }

            // Content-Typeを設定する
            if (mContent != null) {
                this.header("Content-Type", mContent);
            }

            // ヘッダを追加する
            if (mHeaders != null) {
                requestBuilder.headers(mHeaders);
            }

            Request request = requestBuilder.build();

            return new NbApigw(mService, request);
        }
    }
}
