/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import com.nec.baas.user.internal.*;

/**
 * セッショントークン (Pure Java 実装)
 */
public class NbGenericSessionToken implements NbSessionToken {
    /**
     * セッショントークン情報
     */
    public static class TokenInfo {
        /**
         * セッショントークン文字列
         */
        public String sessionToken;

        /**
         * 有効期限 (Epoch秒)
         */
        public long expireAt = 0;

        /**
         * ユーザ情報
         */
        public NbUserEntity sessionUserEntity;
    }

    // 共有用セッショントークン (シングルトン)。
    private static TokenInfo sSharedToken = null;

    // セッショントークン
    private TokenInfo mToken;

    /**
     * セッショントークン共有を有効化する。デフォルトは無効。
     *
     * <p>
     *     有効化すると、全 NbGenericSessionToken 間でトークンが共有される（シングルトン)。
     *     Android SDK と同じ動作。
     * </p>
     * <p>
     *     無効にすると、各インスタンス毎にトークンは個別に保持される。
     * </p>
     * <p>
     *     なお、設定変更前に生成した NbGenericSessionToken の動作は変更されない。
     * </p>
     * @param isShared true にすると共有化有効
     */
    public static synchronized void setIsSessionTokenShared(boolean isShared) {
        if (isShared) {
            if (sSharedToken == null) {
                sSharedToken = new TokenInfo();
            }
        } else {
            if (sSharedToken != null) {
                sSharedToken = null;
            }
        }
    }

    /**
     * コンストラクタ
     */
    public NbGenericSessionToken() {
        if (sSharedToken != null) {
            mToken = sSharedToken;
        } else {
            mToken = new TokenInfo();
        }
    }

    // UT用
    protected TokenInfo getInfo() {
        return mToken;
    }

    /**
     * セッショントークンを保存する。
     * @param sessionToken 保存するセッショントークン
     * @param expireAt セッショントークンの有効期限
     */
    @Override
    public synchronized void setSessionToken(String sessionToken, long expireAt) {
        TokenInfo token = getInfo();
        token.sessionToken = sessionToken;
        token.expireAt = expireAt;
    }

    /**
     * セッショントークンを取得する。
     * セッショントークンの有効期限が切れている場合は null が返る。
     * @return セッショントークン
     */
    @Override
    public synchronized String getSessionToken() {
        String result = null;
        long currentTime = System.currentTimeMillis() / 1000;

        TokenInfo token = getInfo();
        if (token.expireAt > currentTime) {
            result = token.sessionToken;
        }
        return result;
    }

    /**
     * セッショントークンの有効期限を取得する。
     * セッショントークンの有効期限が切れている場合は 0が返る。
     * @return セッショントークンの有効期限
     */
    @Override
    public synchronized long getExpireAt() {
        long currentTime = System.currentTimeMillis() / 1000;
        long expireAt = getInfo().expireAt;
        if (expireAt > currentTime) {
            return expireAt;
        }
        return 0;
    }

    /**
     * ログインユーザ情報を設定する。
     * @param entity ログインユーザ情報
     */
    @Override
    public synchronized void setSessionUserEntity(NbUserEntity entity) {
        TokenInfo token = getInfo();
        if (entity != null) {
            if (token.sessionUserEntity == null) {
                token.sessionUserEntity = new NbUserEntity();
            }
            token.sessionUserEntity.set(entity);
        } else {
            token.sessionUserEntity = null;
        }
    }

    /**
     * ログインユーザ情報を取得する。
     * セッショントークンの有効期限が切れている場合は nullが返る。
     * @return ログインユーザ情報
     */
    @Override
    public synchronized NbUserEntity getSessionUserEntity() {
        long currentTime = System.currentTimeMillis() / 1000;
        TokenInfo token = getInfo();
        if (token.expireAt > currentTime) {
            // 未設定時動作をAndroid側に合わせる
            NbUserEntity entity = new NbUserEntity();
            if (token.sessionUserEntity != null) {
                entity.set(token.sessionUserEntity);
            }
            return entity;
        }
        return null;
    }

    /**
     * セッショントークンを破棄する。
     */
    @Override
    public synchronized void clearSessionToken() {
        TokenInfo token = getInfo();
        token.sessionToken = null;
        token.expireAt = 0;
        token.sessionUserEntity = null;
    }
}
