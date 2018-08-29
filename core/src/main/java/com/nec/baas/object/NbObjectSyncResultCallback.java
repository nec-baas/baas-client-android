/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.core.*;

/**
 * 同期実行結果用コールバック。
 * <p>
 * 同期が成功すると onSuccess() が呼ばれる。
 * <p>
 * 同期が失敗した場合は onFailure() が呼ばれる。
 * この際、statusCode は以下のいずれかの値となる。
 * <ul>
 *     <li>409 (Conflict) : オブジェクトの1つ以上が衝突した場合</li>
 *     <li>500 (Server Internal Error) : オブジェクトの１つ以上が衝突以外のエラーとなった場合</li>
 * </ul>
 * なお、衝突と衝突以外のエラーが同時に発生した場合は、500となる。
 * @since 1.0
 * @deprecated {@link NbResultCallback} で置き換え (v6.5.0)
 */
@Deprecated
public interface NbObjectSyncResultCallback extends NbResultCallback {
    
    /**
     * 同期の実行に成功した場合に呼び出される。
     */
    void onSuccess();
}
