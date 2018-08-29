/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.util;

/**
 * NbAndroidLogger ファクトリ
 */
public class NbAndroidLoggerFactory implements NbLogger.LoggerFactory {
    @Override
    public NbLogger create(String name) {
        return new NbAndroidLogger(name);
    }
}
