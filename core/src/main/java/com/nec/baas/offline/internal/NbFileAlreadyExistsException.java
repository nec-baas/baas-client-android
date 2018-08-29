/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import java.io.IOException;

/**
 * ACLチェックエラー用Exception。
 * Androidのjava.nioに該当Exceptionがなかったので作成
 * @since 1.0
 */
public class NbFileAlreadyExistsException extends IOException {
    private static final long serialVersionUID = 1L;
    /**
     * コンストラクタ。<br>
     */
    public NbFileAlreadyExistsException(String message) {
        super(message);
    }
}
