/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push.internal;

import java.util.Set;

/**
 * プリファレンス
 */
public interface NbPreferences {
    /**
     * キーに対応する文字列を取得する
     * @param key
     * @param defValue
     * @return
     */
    String getString(String key, String defValue);

    /**
     * キーに対応する文字列セットを取得する
     * @param key
     * @param defValue
     * @return
     */
    Set<String> getStringSet(String key, Set<String> defValue);

    /**
     * 文字列を保存する
     * @param key
     * @param value
     */
    NbPreferences putString(String key, String value);

    /**
     * 文字列セットを保存する
     * @param key
     * @param value
     */
    NbPreferences putStringSet(String key, Set<String> value);

    /**
     * 全設定をクリアする
     */
    NbPreferences clear();

    /**
     * 変更内容を反映する
     */
    NbPreferences apply();
}
