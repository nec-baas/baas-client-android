/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.core.internal.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * await で完了待ちが可能な NbRestResponseHandler。
 * 処理完了時に notifyFinish() で完了通知すること。
 */
public abstract class NbAwaitableRestResponseHandler implements NbPreRestResponseHandler {
    private CountDownLatch mLatch;

    /**
     * コンストラクタ
     */
    public NbAwaitableRestResponseHandler() {
        mLatch = new CountDownLatch(1);
    }

    /**
     * 非同期処理完了を通知する
     */
    protected void notifyFinish() {
        mLatch.countDown();
    }

    /**
     * 非同期処理完了を待つ
     * @param timeout タイムアウト(ms)
     * @return true - 正常完了、false - タイムアウトまたは InterruptedException。
     */
    public boolean await(long timeout) {
        try {
            return mLatch.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            //e.printStackTrace();
            return false;
        }
    }
}
