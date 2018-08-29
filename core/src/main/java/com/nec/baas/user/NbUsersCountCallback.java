/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.user;

import com.nec.baas.core.*;

import java.util.List;

/**
 * ユーザ情報取得用コールバック(全件数付き)。
 * @since 5.0.0
 */
public interface NbUsersCountCallback extends NbCountCallback<List<NbUser>> {
    /**
     * ユーザ情報の取得に成功した場合に呼び出される。<br>
     * @param users ユーザ情報リスト。
     * @param count 全ユーザ数
     */
    void onSuccess(List<NbUser> users, int count);
}
