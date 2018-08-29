/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

/**
 * データベース処理用Exception。<br>
 * @since 1.0
 */
public class NbDatabaseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * コンストラクタ
     * @param message メッセージ
     */
    public NbDatabaseException(String message) {
        super(message);
    }

    /**
     * コンストラクタ
     * @param e 原因となった throwable
     */
    public NbDatabaseException(Throwable e) {
        super(e);
    }
}
