/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import com.nec.baas.json.*;
import com.nec.baas.offline.*;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * オフライン機能の結果格納コンテナクラス。
 * @since 1.0
 */
@Getter
@Setter
@EqualsAndHashCode
public class NbOfflineResult {
    /** JSONテキスト */
    private String json;
    /** JSONオブジェクト */
    private NbJSONObject jsonData = new NbJSONObject();
    /** ステータスコード */
    private int statusCode;
    /**
     * 同期状態。データが単数の時のみ有効。<br>
     * (queryLocalData等で複数のデータをmJsonDataで保持する場合は無効)
     */
    private NbSyncState syncState;
}
