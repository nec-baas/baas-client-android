/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.core.*;
import com.nec.baas.json.NbJSONArray;

/**
 * バッチ処理結果受信用コールバック
 * <p>
 * 実行結果は NbJSONArray で返される。
 * 配列に格納される各データの詳細は REST API リファレンスを参照 <br>
 * dataキーにはNbObjectインスタンスが格納される
 * @since 3.0.0
 */
public interface NbBatchOperationCallback extends NbCallback<NbJSONArray> {
}
