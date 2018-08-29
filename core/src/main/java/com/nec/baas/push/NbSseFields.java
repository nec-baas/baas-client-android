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
 * Pushメッセージ内のSSE固有値データ格納クラス。<p/>
 * @since 4.0.0
 */
public class NbSseFields {
    /**
     * -- GETTER --
     * 設定したAndroid固有値データを取得する。
     */
    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PACKAGE)
    private NbJSONObject fields;

    // Key
    private static final String KEY_SSE_EVENT_ID = "sseEventId";
    private static final String KEY_SSE_EVENT_TYPE = "sseEventType";

    /**
     * コンストラクタ
     */
    public NbSseFields() {
        fields = new NbJSONObject();
    }

    /**
     * イベントIDを設定する。<br>
     * @param eventId イベントID
     */
    public NbSseFields setEventId(String eventId) {
        if (eventId != null) {
            fields.put(KEY_SSE_EVENT_ID, eventId);
        }
        return this;
    }

    /**
     * イベントタイプを設定する。<br>
     * @param eventType イベントタイプ
     */
    public NbSseFields setEventType(String eventType) {
        if (eventType != null) {
            fields.put(KEY_SSE_EVENT_TYPE, eventType);
        }
        return this;
    }
}
