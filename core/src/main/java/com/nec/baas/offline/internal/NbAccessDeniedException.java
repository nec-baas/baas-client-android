/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import java.io.IOException;

/**
 * ACLチェックエラー例外。
 * @since 1.0
 */
public class NbAccessDeniedException extends IOException {
    private static final long serialVersionUID = 1L;

    /**
     * コンストラクタ。
     */
    public NbAccessDeniedException(String message) {
        super(message);
    }
}
