/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push;

import com.nec.baas.core.*;

/**
 * SSE Pushインスタレーション用コールバック。
 * @since 4.0.0
 */
public interface NbSsePushInstallationCallback extends NbCallback<NbSsePushInstallation> {
    /**
     * SSE Push インスタレーションの登録/更新/取得に成功した場合に呼び出される。<br>
     * @param installation 取得したインスタレーション情報。
     */
    void onSuccess(NbSsePushInstallation installation);
}
