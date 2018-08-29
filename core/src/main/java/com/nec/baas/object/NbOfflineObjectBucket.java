/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.core.*;

import java.util.Map;

/**
 * オフラインオブジェクトバケット
 * @since 1.0
 */
// TODO: 本来であれば NbObjectBucket -> NbOfflineObjectBucket の継承関係にするべき。
// TODO: 互換性のため現時点では逆になっている。
public interface NbOfflineObjectBucket {
    /**
     * バケットの同期を行う。<p/>
     * 本メソッドはレプリカモードのみ有効。<br>
     * 同期条件が未設定の場合、処理に失敗する。<br>
     * 同期ではバケット情報とバケットに保存されたオブジェクトをサーバから取得後、オフライン用データベースのデータを更新する。<br>
     * 処理の進捗はSyncEventListenerで通知する。
     * @see NbOfflineObjectBucket#setSyncScope(NbQuery)
     * @see NbObjectSyncEventListener
     */
    void sync(final NbResultCallback callback);

    /**
     * オフライン用データベースに保存されたバケットとバケットに保存されたオブジェクトの情報を削除する。<br>
     * 本メソッドはレプリカ・ローカルモードで有効。
     */
    void removeCache();

//    /**
//     * 同期保留中（再同期待ち）のオブジェクトID一覧取得。<br>
//     * 本メソッドはレプリカモードのみ有効。<br>
//     * ネットワーク障害により同期が失敗した場合に同期保留している<br>
//     * オブジェクトのID一覧取得を行う。<br>
//     * データ無し、もしくは取得できなかった場合は空リスト or nullを返却
//     * @return オブジェクトID一覧
//     */
//    List<String> getPendingSyncObjectList();

    /**
     * オフライン: 同期範囲の指定を行う。
     * scopeにquery条件を設定する。
     * 同期範囲のsortOrder、limit、skipCount、deleteMarkは無効とする
     * 範囲条件が無い場合は、空のNbQueryインスタンスを設定すること。
     * @param scope　同期範囲
     */
    void setSyncScope(NbQuery scope);

    /**
     * オフライン: 同期範囲の取得を行う。
     * @return 同期範囲
     */
    NbQuery getSyncScope();

    /**
     * オフライン: 同期範囲の削除を行う。
     */
    void removeSyncScope();

    /**
     * オフライン: 同期処理の進捗イベントを受け取るリスナーを設定する。
     * @param listener 同期処理の進捗イベントを受け取るリスナー
     * @see NbObjectSyncEventListener
     */
    void registerSyncEventListener(NbObjectSyncEventListener listener);

    /**
     * オフライン: 同期処理の進捗イベントを受け取るリスナーを解除する。
     * @param listener 解除するリスナーのインスタンス
     * @see NbObjectSyncEventListener
     */
    void unregisterSyncEventListener(NbObjectSyncEventListener listener);

//    /**
//     * オフライン: 自動同期の間隔を秒単位で設定する。
//     * <ul>
//     *     <li>1 以上が指定された場合は、自動同期を設定した後に
//     *     自動同期を開始する。（設定直後に1回目が実行され、その後指定された間隔で2回目以降を実施）</li>
//     *     <li>0 は設定不可。</li>
//     *     <li>-1 が指定された場合は自動同期を行わない。（自動同期のスケジュールを停止する）</li>
//     * </ul>
//     * 自動同期は呼び出し元プロセス配下のタイマ（リソース）で動作するため、
//     * 呼び出し元は「サービス」等の常時起動型のプロセスから呼び出すこと。
//     * プロセス終了後、再度自動同期を再開したい場合は再度本APIを呼び出すこと。
//     * @param interval 自動同期の間隔(秒)
//     */
//    void setAutoSyncInterval(long interval);
//
//    /**
//     * オフライン: 自動同期の間隔を取得する。
//     * @return 自動同期の間隔(秒)
//     */
//    long getAutoSyncInterval();

    /**
     * ローカルDB用のインデックスを設定する。<br>
     * <ul>
     *     <li>インデックスに使用するフィールドは、トップレベルのフィールドのみ指定できる。</li>
     *     <li>インデックスに使用するフィールドに設定するデータは、スカラ値のみサポートする。</li>
     * </ul>
     * @param indexKeys インデックス設定したいフィールド名の一覧
     * @param callback インデックス設定結果を取得するコールバック
     * @since 1.2.2
     */
    void setIndexToLocal(final Map<String, NbIndexType> indexKeys, final NbResultCallback callback);

    /**
     * ローカルDBにインデックスされているキーの一覧を取得する。
     * @return ローカルDBにインデックスされているキーの一覧
     * @since 1.2.2
     */
    Map<String, NbIndexType> getIndexFromLocal();
}
