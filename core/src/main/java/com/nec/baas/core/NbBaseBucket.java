/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

/**
 * バケット基底インタフェース
 */
public interface NbBaseBucket<T extends NbBaseBucket> {
    /**
     * バケットの名前を取得する。
     *
     * @return 現在使用中のバケット名。
     */
    String getBucketName();

    /**
     * バケットのモードを取得する。
     *
     * @return バケットのモード
     * @see NbBucketMode
     */
    NbBucketMode getMode();

    /**
     * ACLを設定する。
     * save()を実行するまで設定は有効にならない。
     *
     * @param acl 設定するACL。
     */
    T setAcl(NbAcl acl);

    /**
     * ACLを取得する。
     *
     * @return 設定されたACL。
     */
    NbAcl getAcl();

    /**
     * コンテンツACLを設定する。
     * save()を実行するまで設定は有効にならない。
     *
     * @param contentAcl 設定するコンテンツACL。
     */
    T setContentAcl(NbContentAcl contentAcl);

    /**
     * コンテンツACLを取得する。
     * @return 設定されたコンテンツACL。
     */
    NbContentAcl getContentAcl();

    /**
     * バケットの説明文を取得する。
     * @return バケットの説明文
     */
    String getDescription();

    /**
     * バケットの説明文を設定する。
     * @param description バケットの説明文
     */
    T setDescription(String description);

    /**
     * ACLレスモードを取得する (Object Bucket でのみ有効)
     * @return ACLレスバケットの場合は true
     * @since 7.5.1
     */
    boolean isNoAcl();

    /**
     * ACLレスモードを設定する (Object Bucket でのみ有効)
     * @param noAcl ACLレスバケットの場合は true
     * @since 7.5.1
     */
    T setNoAcl(boolean noAcl);
}
