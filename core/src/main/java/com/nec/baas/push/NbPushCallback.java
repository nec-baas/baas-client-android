/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push;

import com.nec.baas.core.*;
import com.nec.baas.json.NbJSONObject;

/**
 * Push送信用コールバック。
 * 実行結果は NbJSONObject（該当したインスタレーション数を含む）で返却される。
 * @since 1.0
 */
public interface NbPushCallback extends NbCallback<NbJSONObject> {
    /**
     * Push送信に成功した場合に呼び出される。<br>
     * @param result 実行結果（該当したインスタレーション数を含む）。
     */
    void onSuccess(NbJSONObject result);
}
