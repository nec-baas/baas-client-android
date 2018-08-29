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
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %3$s - %5$s%6$s%n");
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
