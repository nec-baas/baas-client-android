/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

/**
 * 内部エラーコード定義
 * @since 1.0
 */
public class NbStatus {
    /** 200 OK */
    public static final int OK = 200;

    /** 400 Bad Request */
    public static final int REQUEST_PARAMETER_ERROR = 400;
    /** 401 Unauthorized */
    public static final int UNAUTHORIZED = 401;
    /** 403 Forbidden */
    public static final int FORBIDDEN = 403;
    /** 404 Not Found */
    public static final int NOT_FOUND = 404;
    /** 408 Timeout */
    public static final int REQUEST_TIMEOUT = 408;
    /** 409 Conflict */
    public static final int CONFLICT = 409;

    /** 422 Uprocessable entity error */
    public static final int UNPROCESSABLE_ENTITY_ERROR = 422;
    /** 423 Locked */
    public static final int LOCKED = 423;
    /** 500 Internal server error */
    public static final int INTERNAL_SERVER_ERROR = 500;

    /** キャンセルされた (-1) */
    public static final int CANCELED = -1;
    /** ファイル更新エラー (-2) */
    public static final int FILE_UPDATE_ERROR = -2;

    /**
     * ステータスコードが成功 (200番台)であれば true を返す
     * @param statusCode ステータスコード
     * @return 2xx なら true
     */
    public static boolean isSuccessful(int statusCode) {
        return statusCode / 100 == 2;
    }

    /**
     * ステータスコードが失敗(200番台以外)であれば true を返す
     * @param statusCode ステータスコード
     * @return 2xx でなければ true
     */
    public static boolean isNotSuccessful(int statusCode) {
        return !isSuccessful(statusCode);
    }
}
