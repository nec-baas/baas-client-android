/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 汎用ロガー実装: java.util.logging.Logger を使用する。
 * @since 1.2.0
 */
public class NbGenericLogger extends NbLogger {
    static {
        try {
            System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %3$s - %5$s%6$s%n");
        } catch (SecurityException e) {
            // #11459: Security Manager が有効な場合、setProperty() がエラーになる場合がある。
            Logger.getLogger(NbGenericLogger.class.getName())
                    .warning("System.setProperty failed: " + e.getMessage());
        }
    }

    public static class Factory implements NbLogger.LoggerFactory {
        @Override
        public NbLogger create(String name) {
            return new NbGenericLogger(name);
        }
    }

    private Logger mLogger;

    private NbGenericLogger(String name) {
        mLogger = Logger.getLogger(name);
    }

    @Override
    public void printLog(Level level, String msg, Object... params) {
        mLogger.log(level, msg, params);
    }
}
