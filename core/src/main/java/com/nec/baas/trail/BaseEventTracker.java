/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.trail;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.object.*;
import com.nec.baas.user.*;
import com.nec.baas.util.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * Eventクラスをログとして記録するトラッカーの抽象クラス。
 * @since 1.2
 */
public abstract class BaseEventTracker {
    private NbLogger log = NbLogger.getLogger(BaseEventTracker.class);

    @Getter @Setter
    private String trackerName;
    private NbAcl mAcl = new NbAcl();
    private NbServiceImpl mNebulaService;

    private static final String APP_ID_KEY = "appId";
    private static final String TRACKER_NAME_KEY = "tracker";
    private static final String EVENTS_KEY = "events";
    private static final String DEVICE_ID_KEY = "deviceId";
    private static final String USER_ID_KEY = "userId";
    private static final String DATE_KEY = "date";
    private static final String EMERGENCY_KEY = "emergency";
    private static final String CUSTOM_KEY = "custom";

    private static final String TRAIL_BUCKET_NAME = "trailer_log";

    /**
     * Eventの記録用メソッド
     * @param event 記録する証跡イベント。
     */
    public synchronized void log(@NonNull Event event) {
        if (NbConsts.ENABLE_DATA_SECURITY) {
            NbObject obj = makeObject(event);
            obj.save(new NbCallback<NbObject>() {
                @Override
                public void onSuccess(NbObject object) {
                }

                @Override
                public void onFailure(int statusCode, NbErrorInfo errorInfo) {
                    log.fine("log() <onFailure>" + statusCode);
                }
            });
        }
    }

    private NbObject makeObject(Event event) {
        mNebulaService = (NbServiceImpl)NbService.getInstance();
        //TODO: モード変更のため仮のモードを指定　動作保証はしない
        NbObject obj = new NbObject(mNebulaService, TRAIL_BUCKET_NAME, NbBucketMode.ONLINE);
        return makeObjectBody(event, obj);
    }

    private NbObject makeObjectBody(Event event, NbObject obj) {
        //set appId
        if (mNebulaService.getAppId() != null) {
            obj.put(APP_ID_KEY, mNebulaService.getAppId());
        }
        //set tracker
        obj.put(TRACKER_NAME_KEY, trackerName);
        //set events
        obj.put(EVENTS_KEY, event.eventNameList);
        //set deviceid
        if (mNebulaService.getDeviceId() != null) {
            obj.put(DEVICE_ID_KEY, mNebulaService.getDeviceId());
        }
        //set userid
        if (NbUser.isLoggedIn(mNebulaService)) {
            obj.put(USER_ID_KEY, NbUser.getCurrentUser(mNebulaService).getUserId());
        }

        //set date
        Calendar cal = Calendar.getInstance();
        DateFormat df = new SimpleDateFormat(NbConsts.TIMESTAMP_FORMAT);
        String currentTime = df.format(cal.getTime());
        obj.put(DATE_KEY, currentTime);

        //set emergency
        obj.put(EMERGENCY_KEY, event.emergency);

        //set custom
        obj.put(CUSTOM_KEY, event.customMap);

        mAcl.addEntry(NbAclPermission.READ, NbConsts.GROUP_NAME_ANONYMOUS);
        mAcl.addEntry(NbAclPermission.WRITE, NbConsts.GROUP_NAME_ANONYMOUS);
        obj.putAcl(mAcl);

        return obj;
    }
}
