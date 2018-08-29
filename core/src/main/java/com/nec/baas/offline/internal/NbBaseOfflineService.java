/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.core.NbBucketMode;
import com.nec.baas.core.internal.*;

/**
 * オブジェクト・ファイル オフラインサービス共通
 */
public interface NbBaseOfflineService {
    /**
     * 指定したバケット情報をキャッシュに保存する。
     *
     * @param bucketName キャッシュするバケットの名前
     * @param json       バケットの情報
     * @param isChecked  ACLチェックを行う場合はtrue
     * @return キャッシュの保存の実行結果(ステータスコード)
     */
    int saveBucketCache(String bucketName, String json, boolean isChecked);

    /**
     * 指定したバケット情報をキャッシュに保存する。
     * ACLチェックは行わない。
     *
     * @param bucketName キャッシュするバケットの名前
     * @param json       バケットの情報
     * @return キャッシュの保存の実行結果 true:成功 false:失敗
     */
    boolean saveBucketCache(String bucketName, String json);

    /**
     * 指定したバケットキャッシュを削除する。
     *
     * @param bucketName 削除するバケットの名前
     * @param isChecked ACLチェック/データ存在チェックを行う場合はtrue
     * @return キャッシュの削除の実行結果(ステータスコード)
     */
    int removeBucketCache(String bucketName, boolean isChecked);

    /**
     * 指定したバケットキャッシュを削除する。
     * ACLチェックは行わない。
     *
     * @param bucketName 削除するバケットの名前
     */
    void removeBucketCache(String bucketName);

    /**
     * 指定したバケットの情報をローカルDBから読み込む。
     *
     * @param bucketName 対象のバケット名
     * @return 対象バケットのJSONデータ
     */
    NbOfflineResult readLocalBucket(String bucketName);

    /**
     * バケット名一覧をローカルDBから取得する。
     *
     * @return バケット名一覧のJSONデータ
     */
    NbOfflineResult readLocalBucketList();

    /**
     * バケットの最終同期日時を取得する。
     * @param bucketName バケット名
     * @return バケットの最終同期日時。まだ同期していない場合は null。
     */
    String getLastSyncTime(String bucketName);

    /**
     * DBに同じバケット名、バケットモードのバケットが存在するかを確認する。
     *
     * @param bucketName バケット名
     * @param bucketMode バケットモード
     * @return ローカルDBに同じバケット名、バケットモードのバケットが存在する場合はtrueを返却する
     */
    boolean isBucketExists(String bucketName, NbBucketMode bucketMode);
}
