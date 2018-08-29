/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.user;

import com.nec.baas.core.*;

import java.util.List;

/**
 * グループ情報取得用コールバック。
 * @since 1.0
 */
public interface NbGroupsCallback extends NbCallback<List<NbGroup>> {
    /**
     * グループ情報の取得に成功した場合に呼び出される。<br>
     * @param groups グループ情報リスト。
     */
    void onSuccess(List<NbGroup> groups);
}
