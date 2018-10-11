/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.util;

/**
 * JSONキー名定義
 * @since 1.2.0
 */
public abstract class NbKey {
    // common
    public static final String ID = "_id";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String ACL = "ACL";
    public static final String CONTENT_ACL = "contentACL";
    public static final String ETAG = "etag";
    public static final String RESULTS = "results";
    public static final String REASON_CODE = "reasonCode";
    public static final String DETAIL = "detail";
    public static final String OPTIONS = "options";

    // user / group
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String EMAIL = "email";
    public static final String SESSION_TOKEN = "sessionToken";
    public static final String EXPIRE = "expire";
    public static final String GROUPS = "groups";
    public static final String USERS = "users";
    public static final String ONE_TIME_TOKEN = "token";

    // bucket
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String BUCKET_MODE = "bucketMode";

    // file
    public static final String FILENAME = "filename";
    public static final String CONTENT_TYPE = "contentType";
    public static final String LENGTH = "length";
    public static final String PUBLIC_URL = "publicUrl";
    public static final String PUBLISHED = "published";
    public static final String DELETED = "_deleted";
    public static final String META_ETAG = "metaETag";
    public static final String FILE_ETAG = "fileETag";
    public static final String CACHE_DISABLED = "cacheDisabled";
    public static final String META_STATE = "metaState";
    public static final String FILE_STATE = "fileState";
    public static final String DELETE_MARK = "deleteMark";
    public static final String DBID = "dbid";

    // object
    public static final String WHERE = "where";
    public static final String ORDER = "order";
    public static final String SKIP = "skip";
    public static final String LIMIT = "limit";
    public static final String COUNT = "count";
    public static final String PROJECTION = "projection";

    // object(aggregate)
    public static final String PIPELINE = "pipeline";

    // offline
    public static final String REQUESTS = "requests";
    public static final String REQUEST_TOKEN = "requestToken";
    public static final String CURRENT_TIME = "currentTime";

    // DataSecurity
    public static final String IMPORTANCE = "_importance";

    // batch
    public static final String OP = "op";
    public static final String DATA = "data";
    public static final String RESULT = "result";
}
