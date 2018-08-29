/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import com.nec.baas.core.*;

import java.util.Collections;
import java.util.List;

/**
 * List callback を Single callback で wrap する wrapper クラス
 */
public abstract class NbListToSingleCallbackWrapper<T> implements NbCallback<T> {
    NbCallback<List<T>> callback;

    public NbListToSingleCallbackWrapper(NbCallback<List<T>> callback) {
        this.callback = callback;
    }

    @Override
    public void onSuccess(T result) {
        callback.onSuccess(Collections.singletonList(result));
    }

    @Override
    public void onFailure(int statusCode, NbErrorInfo errorInfo) {
        callback.onFailure(statusCode, errorInfo);
    }
}
