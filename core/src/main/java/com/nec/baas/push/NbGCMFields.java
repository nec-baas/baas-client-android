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
 * Pushメッセージ内のAndroid固有値データ格納クラス。<p/>
 * @since 1.0
 */
public class NbGCMFields {
    /**
     * -- GETTER --
     * 設定したAndroid固有値データを取得する。
     */
    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PACKAGE)
    private NbJSONObject fields;

    // Key
    private static final String KEY_TITLE = "title";
    private static final String KEY_URI = "uri";

    /**
     * コンストラクタ
     */
    public NbGCMFields() {
        fields = new NbJSONObject();
    }

    /**
     * システムバーに表示するタイトルを設定する。<br>
     * @param title システムバーに表示するタイトル
     */
    public NbGCMFields setTitle(String title) {
        if (title != null) {
            fields.put(KEY_TITLE, title);
        }
        return this;
    }

    /**
     * 通知を開いたときに起動するURIを設定する。<br>
     * @param uri 通知を開いたときに起動する URI
     */
    public NbGCMFields setUri(String uri) {
        if (uri != null) {
            fields.put(KEY_URI, uri);
        }
        return this;
    }
}
