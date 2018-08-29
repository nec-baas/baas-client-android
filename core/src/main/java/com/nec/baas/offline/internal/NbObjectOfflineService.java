/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.core.internal.*;
import com.nec.baas.json.*;
import com.nec.baas.object.*;
import com.nec.baas.offline.*;

import java.util.Map;

/**
 * NbObjectOfflineServiceImpl インタフェース
 */
public interface NbObjectOfflineService extends NbBaseOfflineService {

    /**
     * オブジェクトストレージ:バケット単位での同期を行う。
     * @param bucketName 同期対象のバケット名
     */
    int syncBucket(String bucketName);

    /**
     * オブジェクトストレージ:設定された同期範囲に対し同期を行う。
     */
    int sync();

    /**
     * オブジェクトストレージ:同期範囲の指定を行う。
     * @param bucketName　bucket名
     * @param scope　同期範囲
     */
    void setSyncScope(String bucketName, NbQuery scope);

    /**
     * オブジェクトストレージ:同期範囲の取得を行う。
     * @param bucketName　bucket名
     * @return 同期範囲
     */
    NbQuery getSyncScope(String bucketName);

    /**
     * オブジェクトストレージ:同期範囲の取得を行う。
     * @return バケット名-同期範囲のマップ
     */
    Map<String,NbQuery> getSyncScope();

    /**
     * オブジェクトストレージ:同期範囲を削除する。
     * @param bucketName bucket名
     */
    void removeSyncScope(String bucketName);

    /**
     * オブジェクトストレージ:オブジェクトの同期状態を取得する。
     * @param objectId　同期状態を取得するオブジェクトのID
     * @param bucketName オブジェクトが格納されたバケットの名前
     * @return オブジェクトの同期状態。
     */
    NbSyncState getSyncState(String objectId, String bucketName);

    /**
     * オブジェクトストレージ:オブジェクトの最終同期日時を取得する。
     * @param objectId 最終同期日時を取得するオブジェクトのID
     * @param bucketName オブジェクトが格納されたバケットの名前
     * @return オブジェクトの最終同期日時。
     */
    String getObjectLastSyncTime(String objectId, String bucketName);

//    /**
//     * オブジェクトストレージ:自動同期間隔の設定。<br>
//     * @param bucketName bucket名
//     * @param interval 自動同期の間隔(秒)
//     */
//    void setAutoSyncInterval(String bucketName, long interval);
//
//    /**
//     * オブジェクトストレージ:自動同期間隔を取得する。
//     * @param bucketName bucket名
//     * @return 自動同期の間隔(秒)
//     */
//    long getAutoSyncInterval(String bucketName);

//    /**
//     * オブジェクトストレージ:同期保留中のオブジェクトID一覧を取得。
//     * @return オブジェクトID一覧
//     */
//    List<String> getPendingSyncObjectList(String bucketName);

    /**
     * オブジェクトストレージ:同期処理の進捗を受け取るリスナーを設定する。
     * @param bucketName 登録対象のバケット名
     * @param listener 同期処理の進捗を受け取るリスナー
     */
    void registerSyncEventListener(String bucketName, NbObjectSyncEventListener listener);

    /**
     * オブジェクトストレージ:同期処理の進捗を受け取るリスナーを解除する。
     * @param bucketName 登録対象のバケット名
     * @param listener 解除するリスナーのインスタンス
     */
    void unregisterSyncEventListener(String bucketName, NbObjectSyncEventListener listener);

    /**
     * オブジェクトストレージ:ローカルデータを作成する。<br>
     * 同期していないバケットに対してはデータの作成ができない。
     * @param bucketName 作成するバケットの名前
     * @param json 作成するJSONデータ
     * @return 作成したオブジェクトのJSONデータ、ステータスコード
     */
    NbOfflineResult createLocalData(String bucketName, NbJSONObject json);

    /**
     * オブジェクトストレージ:指定したIDのローカルデータを読み込む。<br>
     * 同期していないバケットからデータの読み込みはできない。
     * @param objectId 読み込み対象のオブジェクトID
     * @param bucketName 読み込み対象が保存されたバケットの名前
     * @return 読み込んだオブジェクトのJSONデータ、ステータスコード
     */
    NbOfflineResult readLocalData(String objectId, String bucketName);

    /**
     * オブジェクトストレージ:指定したIDのローカルデータを更新する。<br>
     * 同期していないバケットのデータは更新できない。<br>
     * 同期範囲外のデータに対し更新は出来ない。
     * @param objectId 更新対象のオブジェクトID
     * @param bucketName 更新対象が保存されたバケットの名前
     * @param json 更新に使用するJSONデータ
     * @return 更新したオブジェクトのJSONデータ
     */
    NbOfflineResult updateLocalData(String objectId, String bucketName,
                                                  NbJSONObject json);

    /**
     * オブジェクトストレージ:指定したIDのローカルデータを削除する。<br>
     * 同期していないバケットのデータは削除できない。<br>
     * 同期範囲外のデータに対し削除は出来ない。<br>
     * 本メソッドではローカルデータのステータス変更のみ行い、同期実行時に実データを削除する。
     * @param objectId 削除対象のオブジェクトID
     * @param bucketName 削除対象が保存されたバケットの名前
     * @param etag 削除対象のETag
     */
    NbOfflineResult deleteLocalData(String objectId, String bucketName, String etag);


    /**
     * オブジェクトストレージ:指定したバケットに対しクエリを行う。<br>
     * 同期していないバケットに対しクエリは実行できない。
     * @param query クエリ条件
     * @param bucketName クエリ対象のバケット名
     * @return クエリ実行結果のJSONデータ
     */
    NbOfflineResult queryLocalData(NbQuery query, String bucketName);

    /**
     * オブジェクトストレージ:指定したバケットに対しローカルデータをキャッシュする。<br>
     * @param objectId キャッシュするオブジェクトのID
     * @param bucketName キャッシュ対象を保存するバケットの名前
     * @param json キャッシュ対象のJSONデータ
     */
    void saveCacheData(String objectId, String bucketName, NbJSONObject json);

    /**
     * オブジェクトストレージ:指定したバケットに対しローカルデータをキャッシュする。<br>
     * @param objectId キャッシュするオブジェクトのID
     * @param bucketName キャッシュ対象を保存するバケットの名前
     * @param json キャッシュ対象のJSONデータ
     * @param isForce 強制的にキャッシュへ保存する。（コンフリクトチェックしない）
     */
    void saveCacheData(String objectId, String bucketName, NbJSONObject json, boolean isForce);

    /**
     * オブジェクトストレージ:指定したIDのローカルデータを読み込む。<br>
     * 同期していないバケットからデータの読み込みはできない。ACLチェックは行わない。（キャッシュ用）
     * @param objectId 読み込み対象のオブジェクトID
     * @param bucketName 読み込み対象が保存されたバケットの名前
     * @return 読み込んだオブジェクトのJSONデータ、ステータスコード
     */
    NbOfflineResult readCacheData(String objectId, String bucketName);

    /**
     * オブジェクトストレージ:指定したIDのローカルデータを削除する。<br>
     * @param objectId 削除対象のオブジェクトID
     * @param bucketName 削除対象が保存されたバケットの名前
     */
    boolean removeCacheData(String objectId, String bucketName);

    /**
     * 衝突解決のポリシーを設定する。<br>
     * @param bucketName ポリシーを設定するバケットの名前
     * @param policy 設定する解決ポリシー
     */
    void setResolveConflictPolicy(String bucketName, NbConflictResolvePolicy policy);

    /**
     * 設定された衝突解決ポリシーを取得する。<br>
     * デフォルト値はサーバ優先となる。
     * @return 衝突解決ポリシー
     */
    NbConflictResolvePolicy getResolveConflictPolicy(String bucketName);

    /**
     * サーバ側で発生したコンフリクトを通知する。
     * @param bucketName コンフリクトが発生したバケット名
     * @param client クライアントのオブジェクト
     * @param server サーバのオブジェクト
     */
    void notifyConflict(String bucketName, NbObject client, NbObject server);


    /**
     * ローカルDB用のインデックスを設定する。
     * @param indexKeys インデックス設定したいフィールド名の一覧
     * @param bucketName インデックス設定を行うバケットの名前
     */
    NbOfflineResult setIndexToLocalData(Map<String, NbIndexType> indexKeys, String bucketName);

    /**
     * ローカルDBにインデックスされているキーの一覧を取得する。
     * @param bucketName インデックスを取得したいバケットの名前
     * @return ローカルDBにインデックスされているキーの一覧
     */
    Map<String, NbIndexType> getIndexFromLocalData(String bucketName);
}
