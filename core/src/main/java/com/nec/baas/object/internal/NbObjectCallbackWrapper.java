/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object.internal;

import com.nec.baas.core.*;
import com.nec.baas.object.*;

import java.util.Collections;

/**
 * NbObjectCallback wrapper
 */
public class NbObjectCallbackWrapper implements NbCallback<NbObject> {
    private NbObjectCallback callback;

    public static NbObjectCallbackWrapper wrap(NbObjectCallback callback) {
        if (callback == null) return null;
        return new NbObjectCallbackWrapper(callback);
    }

    private NbObjectCallbackWrapper(NbObjectCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onSuccess(NbObject object) {
        callback.onSuccess(Collections.singletonList(object), null);
    }

    @Override
    public void onFailure(int statusCode, NbErrorInfo errorInfo) {
        callback.onFailure(statusCode, errorInfo);
    }
}
