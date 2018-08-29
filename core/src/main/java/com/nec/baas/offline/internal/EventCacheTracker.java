/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.trail.BaseEventTracker;
import com.nec.baas.trail.Event;
import com.nec.baas.util.*;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Eventクラスをログとして記録するトラッカークラス（証跡ログ用）<br>
 * @since 1.0
 */
public class EventCacheTracker extends BaseEventTracker {
    private static EventCacheTracker sEventCacheTracker = null;
    private boolean mTransaction = false;
    private List<Event> mTransaction_events;

    private EventCacheTracker() {
    }

    public static synchronized EventCacheTracker getTracker(@NonNull String trackerName) {

        if (sEventCacheTracker == null || !trackerName.equals(sEventCacheTracker.getTrackerName())) {
            sEventCacheTracker = new EventCacheTracker();
            sEventCacheTracker.setTrackerName(trackerName);
        }

        return sEventCacheTracker;
    }

    @Override
    public synchronized void log(Event event) {
        if (NbConsts.ENABLE_DATA_SECURITY) {
            if (event == null)
                throw new NullPointerException("event");

            if (mTransaction) {
                setTransactionEvent(event);
                return;
            }
            super.log(event);
        }
    }

    private void setTransactionEvent(Event event) {
        if (mTransaction_events == null) {
            mTransaction_events = new ArrayList<>();
            mTransaction_events.add(event);
        } else {
            String eBucket = (String)event.getCustomMap().get(NbOfflineUtil.TRAIL_BUCKET_KEY);
            List<String> eEvents = event.getEventNameList();
            boolean merge = false;
            for (int i = 0; i < mTransaction_events.size(); i++) {
                Event tEvent = mTransaction_events.get(i);
                String tBucket = (String)tEvent.getCustomMap().get(NbOfflineUtil.TRAIL_BUCKET_KEY);
                List<String> tEvents = tEvent.getEventNameList();
                /* mEmergencyは「none」固定のためチェックしない */
                if (tBucket.equals(eBucket) &&
                        tEvents.containsAll(eEvents)) {
                    mTransaction_events.set(i, mergeIds(event, tEvent));
                    merge = true;
                    break;
                }
            }
            if (!merge) {
                mTransaction_events.add(event);
            }
        }
    }

    private Event mergeIds(Event dst, Event src) {
        Map<String, Object> event_map = (Map<String, Object>)dst.getCustomMap().get(NbOfflineUtil.TRAIL_OBJECTS_KEY);
        Map<String, Object> map = (Map<String, Object>)src.getCustomMap().get(NbOfflineUtil.TRAIL_OBJECTS_KEY);
        List<String> event_ids;
        for (String event_imp : event_map.keySet()) {
            event_ids = (List<String>)event_map.get(event_imp);
            if (map.containsKey(event_imp)) {
                List<String> tmp = (List<String>)map.get(event_imp);
                for (String ids : event_ids) {
                    tmp.add(ids);
                }
            } else {
                map.put(event_imp, event_ids);
            }
        }
        return src;
    }

    public void begin() {
        if (NbConsts.ENABLE_DATA_SECURITY) {
            mTransaction = true;
        }
    }

    public synchronized void commit() {
        if (NbConsts.ENABLE_DATA_SECURITY) {
            mTransaction = false;
            if (mTransaction_events != null) {
                List<Event> tmp = new ArrayList<>(mTransaction_events);
                mTransaction_events = null;
                for (Event event : tmp) {
                    log(event);
                }
            }
        }
    }

    public void rollback() {
        if (NbConsts.ENABLE_DATA_SECURITY) {
            mTransaction = false;
            mTransaction_events = null;
        }
    }
}
