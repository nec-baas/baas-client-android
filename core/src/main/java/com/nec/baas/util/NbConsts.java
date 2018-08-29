/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.util;

import okhttp3.MediaType;

/**
 * 内部定数定義
 * @since 1.2.0
 */
public abstract class NbConsts {
    public static final String DEFAULT_END_POINT_URI = "https://xxx";

    // URI パス定義
    public static final String OBJECT_BUCKET_PATH = "/buckets/object";
    public static final String OBJECTS_PATH = "/objects";
    public static final String FILE_BUCKET_PATH = "/buckets/file";
    public static final String FILES_PATH = "/files";
    public static final String META_PATH_COMPONENT = "meta";
    public static final String AGGREGATE_PATH_COMPONENT = "_aggregate";
    public static final String BATCH_URL = "/_batch";

    public static final String DEFAULT_ENCODING = "UTF-8";
    //public static final Charset DEFAULT_CHARSET = Charset.forName(DEFAULT_ENCODING);

    //public static final int QUERY_LIMIT_MAX = 100;
    public static final int DEFAULT_QUERY_LIMIT = 100;

    //public static final int NAME_NOT_FOUND = -1;
    public static final int MAX_NAME_LENGTH = 32;
    public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final String HEADER_X_CONTENT_LENGTH = "X-Content-Length";
    public static final String HEADER_ETAG = "ETag";
    //public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_ACL = "X-ACL";
    public static final String HEADER_META_OPTIONS = "X-Meta-Options";

    // special group name
    public static final String GROUP_NAME_ANONYMOUS = "g:anonymous";
    public static final String GROUP_NAME_AUTHENTICATED = "g:authenticated";

    //DataSecurity
    public static final boolean ENABLE_DATA_SECURITY = false; // DataSecurity を使用する場合は true にする。
    public static final String TRAIL_TRACKER_NAME = "object_storage";

    // Batch Operation
    public static final String DELETE_OP = "delete";
    public static final String UPDATE_OP = "update";
    public static final String INSERT_OP = "insert";

    // MediaType
    public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");
}
