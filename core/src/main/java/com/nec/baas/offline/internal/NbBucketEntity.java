/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.core.*;
import com.nec.baas.offline.*;

import lombok.Getter;
import lombok.Setter;

/**
 * バケット情報データクラス。<p/>
 *
 * @since 1.0
 */
@Getter
@Setter
public class NbBucketEntity {
    /**
     * バケット名
     */
    private String bucketName;

    /**
     * ACL
     */
    private NbAcl acl;

    /**
     * Content ACL
     */
    private NbContentAcl contentAcl;

    /**
     * 衝突解決ポリシ。
     */
    private NbConflictResolvePolicy policy;

    /**
     * JSONテキスト。サーバ応答JSONそのもの。
     * name, description, ACL, contentACL, bucketMode が含まれる。
     */
    private String jsonData;

    /**
     * バケットモード
     */
    private NbBucketMode bucketMode;
}
