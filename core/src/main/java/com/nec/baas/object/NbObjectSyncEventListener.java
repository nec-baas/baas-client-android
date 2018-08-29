/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.offline.*;

import java.util.List;

/**
 * オブジェクト同期処理の進捗を受け取るリスナー。<br>
 * @since 1.0
 */
public interface NbObjectSyncEventListener {
    /**
     * 同期エラー種別。
     * @since 1.2.0
     */
    enum SyncError {
        /**
         * 同期処理のPUSHエラー。
         */
        PUSH_ERROR,

        /**
         * 同期処理のPULLエラー。
         */
        PULL_ERROR,

        /**
         * 同期処理のID重複エラー。
         * 新規のオブジェクトID同士が衝突したケース。
         * 自動でクライアント側のオブジェクトIDを振り直し、同期を継続する。
         */
        ID_CONFLICTED,

        /**
         * 同期処理のpushエラー後に、同期リトライ中。
         * (自動同期中にネットワーク障害が発生した場合は、再同期（リトライ）を実施)
         */
        SYNC_RETRYING
    }

    /**
     * 同期開始通知。
     * 同期処理の開始時に呼び出される。
     * <p>
     * pushエラー後の同期リトライを開始した場合にも通知される。
     * @param targetBucket 同期対象のバケット名
     */
    void onSyncStart(String targetBucket);

    /**
     * 同期完了通知。
     * 同期処理の完了時に呼び出される。
     * <p>
     * エラー発生時やデータ衝突（Conflict）が発生した場合でも、
     * 一連の同期処理が終了した時点で通知される。
     * pushエラー後の同期リトライを完了した場合にも通知される。
     * @param targetBucket 同期が実行されたバケット名
     * @param syncObjectIds 同期が実行されたオブジェクトのID
     */
    void onSyncCompleted(String targetBucket, List<String> syncObjectIds);

    /**
     * 衝突通知。
     * データの衝突が発生した際に呼び出される。
     * <p>
     * pushエラー後の同期リトライ中にも通知される。
     * @param conflictResolver 衝突解決を行うためのオブジェクト
     * @param bucketName 衝突データが格納されたバケットの名前
     * @param client クライアント側の衝突データ
     * @param server サーバ側の衝突データ
     */
    void onSyncConflicted(NbObjectConflictResolver conflictResolver, String bucketName,
            NbObject client, NbObject server);

    /**
     * 衝突解決通知。
     * データの衝突が解決された際に呼び出される。
     * <p>
     * pushエラー後の同期リトライ中にも通知される。
     * 手動で衝突解決が必要な場合（衝突ポリシー＝「マニュアル」）に呼び出される。
     * （衝突ポリシーにより自動解決された場合は呼び出されない）
     * @param resolveObject 解決に使用したデータ
     * @param resolve 解決方法
     */
    void onResolveConflict(NbObject resolveObject, NbConflictResolvePolicy resolve);

    /**
     * 同期エラー通知。
     * 同期処理でエラーが発生した際に呼び出される。
     * <p>
     * pullエラーの場合オブジェクト情報はnullとなる。
     * pushエラー後の同期リトライ中にも呼び出される。
     * 衝突ポリシーにより手動、自動どちらで解決した場合にでも呼び出される。
     * @param errorCode 発生したエラーの種別
     * @param errorObject エラーが発生したオブジェクトの情報
     */
    void onSyncError(SyncError errorCode, NbObject errorObject);
}
