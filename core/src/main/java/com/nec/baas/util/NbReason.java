/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.util;

/**
 * 原因文字列定義
 * @since 1.2.0
 */
public abstract class NbReason {
    public static final String UNSPECIFIED = "unspecified";
    public static final String REQUEST_CONFLICTED = "request_conflicted";
    public static final String DUPLICATE_KEY = "duplicate_key";
    public static final String DUPLICATE_ID = "duplicate_id";
    public static final String ETAG_MISMATCH = "etag_mismatch";

    public static final String DATABASE_ERROR = "database_error";
    public static final String NOT_FOUND = "not_found";
}
