/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import com.nec.baas.user.internal.*;

/**
 * セッショントークン保持用クラス
 * @since 1.0
 */
public interface NbSessionToken {
    /**
     * セッショントークンを保存する。
     * @param sessionToken 保存するセッショントークン
     * @param expireAt セッショントークンの有効期限
     */
    void setSessionToken(String sessionToken, long expireAt);

    /**
     * セッショントークンを取得する。
     * セッショントークンの有効期限が切れている場合は null が返る。
     * @return セッショントークン
     */
    String getSessionToken();

    /**
     * セッショントークンの有効期限を取得する。
     * セッショントークンの有効期限が切れている場合は 0が返る。
     * @return セッショントークンの有効期限
     */
    long getExpireAt();

    /**
     * ログインユーザ情報を設定する。
     * @param entity ログインユーザ情報
     */
    void setSessionUserEntity(NbUserEntity entity);

    /**
     * ログインユーザ情報を取得する。
     * セッショントークンの有効期限が切れている場合は nullが返る。
     * @return ログインユーザ情報
     */
    NbUserEntity getSessionUserEntity();

    /**
     * セッショントークンを破棄する。
     */
    void clearSessionToken();
}
