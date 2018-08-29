/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.file;

import com.nec.baas.core.*;

import java.util.List;

/**
 * ファイルバケットマネージャ。
 *
 * {@link NbService#fileBucketManager()} でインスタンスを取得する。
 * @since 1.0
 */
public interface NbFileBucketManager {
    /**
     * ファイルバケットの作成を行う。
     * <p>
     * バケットの作成にはROOTバケットに対するcreate権限が必要となる。<br>
     * description、ACL、contentAclはオプションのため指定しなくても良い。<br>
     * ACLはオプションのため指定しなくても良い。
     * @param bucketName 作成するバケットの名前。
     * @param acl 作成するバケットのACL。
     * @param contentAcl 作成するバケットのコンテンツACL。
     * @param callback 作成したバケットを受け取るコールバック。
     */
    void createBucket(final String bucketName, String description, NbAcl acl,
                             NbContentAcl contentAcl, final NbCallback<NbFileBucket> callback);

    /**
     * ファイルバケット名一覧を取得する。
     * ROOTバケットに対するread権限が必要となる。
     * @param callback バケット一覧を取得するコールバック。
     */
    void getBucketList(final NbCallback<List<NbFileBucket>> callback);

    /**
     * ファイルバケットを取得する。サーバに問い合わせは行わない。
     * <p>
     * Acl, ContentACL, desciption は設定されない。save() は使用不可。
     * @param bucketName 取得するファイルバケットの名前。
     * @return ファイルバケット
     */
    NbFileBucket getBucket(final String bucketName);

    /**
     * ファイルバケットを取得する。サーバに対する非同期問い合わせが発生する。
     * <p>
     * ROOTバケットおよび対象バケットに対するread権限が必要となる。
     * @param bucketName 取得するファイルバケットの名前。
     * @param callback ファイルバケットを取得するコールバック。
     */
    void getBucket(final String bucketName, final NbCallback<NbFileBucket> callback);

    /**
     * ファイルバケットを削除する。
     * <p>
     * ROOTバケットおよび対象バケットに対するdelete権限が必要となる。
     * オフライン時はエラーとなる。
     * @param bucketName 削除するファイルバケットの名前
     * @param callback 削除結果を受け取るコールバック。
     */
    void deleteBucket(String bucketName, final NbResultCallback callback);
}
