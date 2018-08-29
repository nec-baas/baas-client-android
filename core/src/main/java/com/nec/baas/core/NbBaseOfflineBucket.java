/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import com.nec.baas.offline.*;

/**
 * オフラインバケットインタフェース。
 */
public interface NbBaseOfflineBucket {
    /**
     * 同期で衝突が発生した場合の解決ポリシーを指定する。
     * @param policy 衝突解決ポリシー
     * @deprecated {@link #setResolveConflictPolicy(NbConflictResolvePolicy)} で置き換え
     */
    @Deprecated
    void setResolveConflictPolicy(int policy);

    /**
     * 同期で衝突が発生した場合の解決ポリシーを指定する。<br>
     * 本メソッドはレプリカモードのみ有効。
     * @param policy 衝突解決ポリシー
     */
    void setResolveConflictPolicy(NbConflictResolvePolicy policy);

    /**
     * 設定された衝突解決ポリシーを取得する。
     * 本メソッドはレプリカモードのみ有効。
     * <p>デフォルト値はサーバ優先となる。
     * @return 衝突解決ポリシー
     */
    NbConflictResolvePolicy getResolveConflictPolicy();

    /**
     * バケットの最終同期日時を取得する。
     * @return バケットの最終更新日時
     */
    String getLastSyncTime();
}
