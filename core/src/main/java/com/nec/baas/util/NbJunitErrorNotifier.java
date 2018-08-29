/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.util;

/**
 * JUnit テスト用エラー通知マネージャ。
 *
 * JUnit のアサーション (AssertionError) が発生したときに、これを TP 側に引き渡すため
 * に使用する。通常時は使用されない。
 */
public class NbJunitErrorNotifier {
    /**
     * エラー通知コールバック。
     */
    public interface Callback {
        /**
         * AssertionError 発生時に呼び出される。
         * @param error
         */
        void onError(AssertionError error);
    }

    public static Callback sCallback = null;

    /**
     * 発生したエラーを通知する。
     * callback が設定されている場合は callback する。
     * callback が設定されていない場合はそのまま throw する。
     * @param error エラー
     */
    public static synchronized void notify(AssertionError error) {
        error.printStackTrace();
        if (sCallback != null) {
            sCallback.onError(error);
            sCallback = null; // callback は1回だけ
        } else {
            throw error;
        }
    }

    /**
     * エラー通知コールバックをセットする。
     * セット可能なコールバックは１つだけ。
     * @param callback
     */
    public static synchronized void setCallback(Callback callback) {
        sCallback = callback;
    }
}
