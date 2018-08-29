/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push;

import com.nec.baas.core.*;

/**
 * FCM/GCM Pushインスタレーション用コールバック。
 * <p>
 * FCM/GCM Pushインスタレーションの登録/更新/取得に成功した場合に onSuccess が呼び出される。
 * @since 6.0.0
 */
public interface NbFcmPushInstallationCallback extends NbCallback<NbFcmPushInstallation> {
    /**
     * FCM/GCM Pushインスタレーションの登録/更新/取得に成功した場合に呼び出される。<br>
     * @param installation 取得したインスタレーション情報。
     */
    void onSuccess(NbFcmPushInstallation installation);
}
