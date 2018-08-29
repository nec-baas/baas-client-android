/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push;

import com.nec.baas.json.NbJSONObject;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Pushメッセージ内のiOS固有値データ格納クラス。<p/>
 * @since 1.0
 */
public class NbAPNSFields {
    /**
     * -- GETTER --
     * 設定したiOS固有値データを取得する。
     */
    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PACKAGE)
    private NbJSONObject fields;

    // Key
    private static final String KEY_BADGE = "badge";
    private static final String KEY_SOUND = "sound";
    private static final String KEY_CONTENT_AVAILABLE = "content-available";
    private static final String KEY_CATEGORY = "category";

    /**
     * コンストラクタ
     */
    public NbAPNSFields() {
        fields = new NbJSONObject();
    }

    /**
     * バッジカウントを設定する。<br>
     * @param badge バッジカウント
     */
    public NbAPNSFields setBadge(int badge) {
        fields.put(KEY_BADGE, badge);
        return this;
    }

    /**
     * Application Bundle 内のサウンドファイル名を設定する。<br>
     * @param sound Application Bundle 内のサウンドファイル名
     */
    public NbAPNSFields setSound(String sound) {
        if (sound != null) {
            fields.put(KEY_SOUND, sound);
        }
        return this;
    }

    /**
     * バックグランド更新を設定する。<br>
     * @param contentAvailable バックグラウンド更新（1にセットすると、バックグランド Push を有効にする）
     */
    public NbAPNSFields setContentAvailable(int contentAvailable) {
        fields.put(KEY_CONTENT_AVAILABLE, contentAvailable);
        return this;
    }

    /**
     * Notification カテゴリを設定する。<br>
     * @param category Notification カテゴリ
     */
    public NbAPNSFields setCategory(String category) {
        if (category != null) {
            fields.put(KEY_CATEGORY, category);
        }
        return this;
    }
}
