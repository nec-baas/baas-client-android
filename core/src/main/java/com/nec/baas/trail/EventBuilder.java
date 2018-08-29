/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.trail;

/**
 * Eventクラスのインスタンスを作成するBuilderクラス。
 * @since 1.0
 */
public class EventBuilder {
    private Event mEvent;

    /**
     * EventBuilderコンストラクタ
     */
    public EventBuilder() {
        mEvent = new Event();
        mEvent.emergency = Event.EMERGENCY_NONE;
    }

    /**
     * イベント追加メソッド。
     * @param eventName Eventに追加するイベント名。
     */
    public EventBuilder addEvent(String eventName) {
        mEvent.eventNameList.add(eventName);
        return this;
    }

    /**
     * 緊急度指定メソッド。
     * @param emergency Eventに指定する緊急度。
     * 
     */
    public EventBuilder setEmergency(String emergency) {
        mEvent.emergency = emergency;
        return this;
    }

    /**
     * カスタム属性指定メソッド。
     * @param tag Eventに指定するカスタム属性の属性名。
     * @param value Eventに指定するカスタム属性の属性値。
     *              (String、Number型、Map<String,Object>のみ）
     */
    public EventBuilder setCustom(String tag, Object value) {
        mEvent.customMap.put(tag, value);
        return this;
    }

    /**
     * Eventを生成する
     * @return Event
     */
    public Event build() {
        if(mEvent.eventNameList.size() == 0) {
            throw new IllegalArgumentException();
        }
        return mEvent;
    }

}
