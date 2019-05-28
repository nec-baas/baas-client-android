/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.core.*;
import java.util.List;

/**
 * オブジェクトバケットマネージャ。
 * {@link NbService#objectBucketManager()} でインスタンスを取得する。
 * @since 1.0
 */
public interface NbObjectBucketManager {
    /**
     * オブジェクトバケットの作成を行う。
     * <p>
     * description、ACL、contentAclはオプションのため指定しなくても良い。
     * バケットの作成にはROOTバケットに対するcreate権限が必要となる。
     * 本メソッドはレプリカ・ローカルモードでのバケット情報更新は不可とする。
     * </p>
     * @param bucketName 作成するバケットの名前
     * @param description バケットの説明文
     * @param acl バケットに設定するACL
     * @param contentAcl バケットに設定するコンテンツACL
     * @param callback 作成したバケットを取得するコールバック。
     */
    void createBucket(final String bucketName, String description, NbAcl acl,
                      NbContentAcl contentAcl, final NbCallback<NbObjectBucket> callback);

    /**
     * オブジェクトバケットの作成を行う。
     * <p>
     * description、ACL、contentAclはオプションのため指定しなくても良い。
     * バケットの作成にはROOTバケットに対するcreate権限が必要となる。
     * 本メソッドはレプリカ・ローカルモードでのバケット情報更新は不可とする。
     * </p>
     * @param bucketName 作成するバケットの名前
     * @param description バケットの説明文
     * @param acl バケットに設定するACL
     * @param contentAcl バケットに設定するコンテンツACL
     * @param noAcl ACLレスバケット時は true
     * @param callback 作成したバケットを取得するコールバック。
     * @since 7.5.1
     */
    void createBucket(final String bucketName, String description,
                      NbAcl acl, NbContentAcl contentAcl, boolean noAcl,
                      final NbCallback<NbObjectBucket> callback);

    /**
     * サーバ上のオブジェクトバケット一覧を取得する。
     * <p>
     * ROOTバケットに対するread権限が必要となる。
     * </p>
     * @param callback バケット名を取得するコールバック。
     */
    void getBucketList(final NbCallback<List<NbObjectBucket>> callback);

    /**
     * ローカルDB上のオブジェクトバケット一覧を取得する。
     */
    List<NbObjectBucket> getBucketList();

    /**
     * バケットを取得する。サーバに対する問い合わせは行わない。
     * @param bucketName バケット名
     * @param bucketMode バケットモード
     * @return バケット
     */
    NbObjectBucket getBucket(final String bucketName, final NbBucketMode bucketMode);

    /**
     * バケット情報を取得する。サーバに対する非同期問い合わせが発生する。
     * <p>
     * ROOTバケットおよび対象バケットに対するread権限が必要となる。
     * バケット情報の取得に成功した場合、ローカルDBに情報をキャッシュする。
     * </p>
     * @param bucketName 情報を取得するバケットの名前。
     * @param bucketMode バケットモード。
     * @param callback バケットを取得するコールバック。
     */
    void getBucket(final String bucketName, final NbBucketMode bucketMode, final NbCallback<NbObjectBucket> callback);

    /**
     * オンラインモード指定でバケット情報を取得する。サーバに対する問い合わせは行わない。
     * @param bucketName バケット名
     * @return バケット
     */
    NbObjectBucket getBucket(final String bucketName);

    /**
     * オンラインモード指定でバケット情報を取得する。サーバに対する非同期問い合わせが発生する。
     * <p>
     * ROOTバケットおよび対象バケットに対するread権限が必要となる。
     * バケット情報の取得に成功した場合、ローカルDBに情報をキャッシュする。
     * </p>
     * @param bucketName 情報を取得するバケットの名前。
     * @param callback バケットを取得するコールバック。
     */
    void getBucket(final String bucketName, final NbCallback<NbObjectBucket> callback);

    /**
     * サーバ上のバケットを削除する。
     * <p>
     * ROOTバケットおよび対象バケットに対するdelete権限が必要となる。
     * バケットの削除に成功した場合、ローカルDBからバケット情報を削除する。
     * </p>
     * @param bucketName 削除するバケットの名前。
     * @param callback 実行結果を取得するコールバック。
     */
    void deleteBucket(final String bucketName, final NbResultCallback callback);

    /**
     * ローカルDB上のバケットを削除する。
     * <p>
     * 対象バケットに対するdelete権限が必要となる。
     * </p>
     * @param bucketName 削除するバケットの名前。
     */
    void deleteBucket(final String bucketName);

    /**
     * ローカルDBに保存されている同期設定済みバケットの一括同期を行う。
     */
    void sync(final NbResultCallback callback);

}







