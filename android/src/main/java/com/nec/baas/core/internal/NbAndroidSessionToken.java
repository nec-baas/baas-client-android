/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.nec.baas.json.NbJSONArray;
import com.nec.baas.json.NbJSONObject;
import com.nec.baas.json.NbJSONParser;
import com.nec.baas.user.internal.*;

/**
 * Android用セッショントークン保持クラス
 * @since 1.0
 */
public class NbAndroidSessionToken implements NbSessionToken {
    /*package*/ Context mContext;
    /*package*/ SharedPreferences mPreference;

    private static final String PREFERENCE_NAME = "nebula_pref";
    // Preferenceキー名はJSONキーに合わせる
    // NbKeyが変更になった時に互換性がなくなる可能性があるので、NbKeyは参照しない
    private static final String SESSION_TOKEN_KEY = "sessionToken";
    private static final String EXPIRE_KEY = "expire";
    private static final String USERID_KEY = "_id";
    private static final String USERNAME_KEY = "username";
    private static final String EMAIL_KEY = "email";
    private static final String OPTIONS_KEY = "options";
    private static final String GROUPS_KEY = "groups";
    private static final String CREATETIME_KEY = "createdAt";
    private static final String UPDATETIME_KEY = "updatedAt";

    /**
     * Android用コンストラクタ
     * @param context Android依存処理に使用するコンテキスト
     */
    public NbAndroidSessionToken(Context context) {
        mContext = context;
        mPreference = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * セッショントークンを保存する。
     * @see com.nec.baas.core.internal.NbSessionToken#setSessionToken(String, long)
     */
    @Override
    public void setSessionToken(String sessionToken, long expireAt) {
        Editor editor = mPreference.edit();
        editor.putString(SESSION_TOKEN_KEY, sessionToken);
        editor.putLong(EXPIRE_KEY, expireAt);
        editor.commit();
    }

    /**
     * セッショントークンを取得する。
     * @see NbSessionToken#getSessionToken()
     */
    @Override
    public String getSessionToken() {
        String result = null;
        long currentTime = System.currentTimeMillis() / 1000;
        long expire = mPreference.getLong(EXPIRE_KEY, 0);
        if (expire > currentTime) {
            result = mPreference.getString(SESSION_TOKEN_KEY, null);
        }
        return result;
    }

    /**
     * セッショントークンの有効期限を取得する。
     * @see NbSessionToken#getExpireAt()
     */
    @Override
    public long getExpireAt() {
        long currentTime = System.currentTimeMillis() / 1000;
        long expire = mPreference.getLong(EXPIRE_KEY, 0);
        if (expire > currentTime) {
            return expire;
        }
        return 0;
    }

    /**
     * ログインユーザ情報を設定する。
     * @see com.nec.baas.core.internal.NbSessionToken#setSessionUserEntity(com.nec.baas.user.internal.NbUserEntity)
     */
    @Override
    public void setSessionUserEntity(NbUserEntity entity) {
        Editor editor = mPreference.edit();

        // オプションパラメータは存在しない場合もあるので、一旦削除してから保存する
        editor.remove(USERID_KEY);
        editor.remove(USERNAME_KEY);
        editor.remove(EMAIL_KEY);
        editor.remove(OPTIONS_KEY);
        editor.remove(GROUPS_KEY);
        editor.remove(CREATETIME_KEY);
        editor.remove(UPDATETIME_KEY);

        if (entity != null) {
            editor.putString(USERID_KEY, entity.getId());
            editor.putString(USERNAME_KEY, entity.getUsername());
            editor.putString(EMAIL_KEY, entity.getEmail());
            // SharedPreferenceにそのまま保存できないので、Stringに変換した後に保存する
            editor.putString(OPTIONS_KEY,
                    (entity.getOptions() == null) ? null : entity.getOptions().toJSONString());
            NbJSONObject groups = new NbJSONObject();
            groups.put(GROUPS_KEY, entity.getGroups());
            editor.putString(GROUPS_KEY, groups.toJSONString());
            editor.putString(CREATETIME_KEY, entity.getCreatedTime());
            editor.putString(UPDATETIME_KEY, entity.getUpdatedTime());
        }
        editor.commit();
    }

    /**
     * ログインユーザ情報を取得する。
     * @see NbSessionToken#getSessionUserEntity()
     */
    @Override
    public NbUserEntity getSessionUserEntity() {
        long currentTime = System.currentTimeMillis() / 1000;
        long expire = mPreference.getLong(EXPIRE_KEY, 0);
        if (expire > currentTime) {
            NbUserEntity entity = new NbUserEntity();
            entity.setId(mPreference.getString(USERID_KEY, null));
            entity.setUsername(mPreference.getString(USERNAME_KEY, null));
            entity.setEmail(mPreference.getString(EMAIL_KEY, null));
            entity.setOptions(NbJSONParser.parse(mPreference.getString(OPTIONS_KEY, "{}")));

            NbJSONObject groups = NbJSONParser.parse(mPreference.getString(GROUPS_KEY, "{}"));
            NbJSONArray groupsArray = groups.getJSONArray(GROUPS_KEY);
            if (groupsArray != null) {
                entity.setGroups(groupsArray);
            }

            entity.setCreatedTime(mPreference.getString(CREATETIME_KEY, null));
            entity.setUpdatedTime(mPreference.getString(UPDATETIME_KEY, null));
            return entity;
        }
        return null;
    }

    /**
     * セッショントークンを破棄する。
     * @see NbSessionToken#clearSessionToken()
     */
    @Override
    public void clearSessionToken() {
        mPreference.edit().clear().commit();
    }
}
