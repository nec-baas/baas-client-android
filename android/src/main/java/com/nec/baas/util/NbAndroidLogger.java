/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.util;

import android.util.Log;

import java.util.logging.Level;

/**
 * NbLogger Android 実装。
 *
 * android.util.Log を使用してログ出力する。
 * ログレベルの対応は以下の通り。
 *
 * <ul>
 *     <li>SEVERE  → ERROR</li>
 *     <li>WARNING → WARN</li>
 *     <li>INFO    → INFO</li>
 *     <li>FINE    → DEBUG</li>
 *     <li>FINER   → VERBOSE</li>
 *     <li>FINEST  → VERBOSE</li>
 * </ul>
 */
public class NbAndroidLogger extends NbLogger {
    private String mTag;

    /*package*/ NbAndroidLogger(String name) {
        mTag = name;
    }

    @Override
    public void printLog(Level level, String msg, Object... params) {
        int priority;
        if (level == Level.SEVERE) {
            priority = Log.ERROR;
        } else if (level == Level.WARNING) {
            priority = Log.WARN;
        } else if (level == Level.INFO) {
            priority = Log.INFO;
        } else if (level == Level.CONFIG) {
            priority = Log.INFO;
        } else if (level == Level.FINE) {
            priority = Log.DEBUG;
        } else { // FINER, FINEST
            priority = Log.VERBOSE;
        }

        if (!Log.isLoggable(mTag, priority)) {
            return;
        }

        Log.println(priority, mTag, formatMessage(msg, params));
    }
}
