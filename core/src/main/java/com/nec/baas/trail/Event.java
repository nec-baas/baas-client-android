/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.trail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;

/**
 * 証跡イベント。
 * {@link EventBuilder} で作成する。
 * @since 1.2
 */
@Getter
public class Event {
    public static final String EMERGENCY_NONE = "none";

    protected String emergency;
    protected List<String> eventNameList = new ArrayList<>();
    protected Map<String, Object> customMap = new HashMap<>();
}
