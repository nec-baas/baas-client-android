/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.user;

import com.nec.baas.core.*;

import java.util.List;

/**
 * ユーザ情報取得用コールバック。
 * @since 1.0
 */
public interface NbUsersCallback extends NbCallback<List<NbUser>> {
    /**
     * ユーザ情報の取得に成功した場合に呼び出される。
     * @param users ユーザ情報リスト。
     */
    void onSuccess(List<NbUser> users);
}
