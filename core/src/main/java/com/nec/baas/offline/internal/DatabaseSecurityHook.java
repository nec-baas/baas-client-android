/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.trail.*;
import com.nec.baas.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DataSecurity NbDatabaseManager フック
 */
public class DatabaseSecurityHook implements DatabaseHook {
    @Override
    public void deleteBucket(String bucketName) {
        trail_log(null, bucketName, null, NbOfflineUtil.TRAIL_EVENT_CLEAR_NAME);
    }

    @Override
    public void createObject(String bucketName, String objectId, NbObjectEntity data) {
        trail_log(objectId, bucketName, data, NbOfflineUtil.TRAIL_EVENT_STORE_NAME);
    }

    @Override
    public void updateObject(String bucketName, String objectId, NbObjectEntity data) {
        trail_log(objectId, bucketName, data, NbOfflineUtil.TRAIL_EVENT_STORE_NAME);
    }

    @Override
    public void deleteObject(String bucketName, String objectId, NbObjectEntity data) {
        trail_log(objectId, bucketName, data, NbOfflineUtil.TRAIL_EVENT_CLEAR_NAME);
    }

    @Override
    public void begin() {
        EventCacheTracker.getTracker(NbConsts.TRAIL_TRACKER_NAME).begin();
    }

    @Override
    public void commit() {
        EventCacheTracker.getTracker(NbConsts.TRAIL_TRACKER_NAME).commit();
    }

    @Override
    public void rollback() {
        EventCacheTracker.getTracker(NbConsts.TRAIL_TRACKER_NAME).rollback();
    }

    //DataSecurity
    private void trail_log(String objectId, String bucketName, NbObjectEntity data, String eventName) {
        Map<String, Object> ids_map = new HashMap<>();
        List<String> ids = new ArrayList<>();
        if (data == null) {
            ids_map.put("*", ids);
        } else {
            String importance = (String)data.getJsonObject().get(NbKey.IMPORTANCE);
            if (importance == null || importance.equals(NbOfflineUtil.IMPORTANCE_NONE)) {
                return;
            }
            ids.add(objectId);
            ids_map.put(importance, ids);
        }
        Event event = new EventBuilder()
                .addEvent(eventName)
                .setCustom(NbOfflineUtil.TRAIL_BUCKET_KEY, bucketName)
                .setCustom(NbOfflineUtil.TRAIL_OBJECTS_KEY, ids_map)
                .build();

        EventCacheTracker eventCacheTracker = EventCacheTracker.getTracker(NbOfflineUtil.TRAIL_TRACKER_NAME);
        eventCacheTracker.log(event);
    }
}
