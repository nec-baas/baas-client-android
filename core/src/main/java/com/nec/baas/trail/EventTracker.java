/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.trail;

import lombok.NonNull;

/**
 * Eventクラスをログとして記録するトラッカークラス（カスタムログ用)。
 * @since 1.0
 */
public class EventTracker extends BaseEventTracker {
    private static EventTracker sEventTracker = null;

    private EventTracker() {
    }

    /**
     * トラッカーインスタンス取得メソッド
     * @param trackerName トラッカー名
     */
    public static synchronized EventTracker getTracker(@NonNull String trackerName) {

        if (sEventTracker == null || !trackerName.equals(sEventTracker.getTrackerName())) {
            sEventTracker = new EventTracker();
            sEventTracker.setTrackerName(trackerName);
        }

        return sEventTracker;
    }
}
