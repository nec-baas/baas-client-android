/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import lombok.NonNull;
import lombok.Value;

/**
 * エラー詳細情報
 * @since 1.1.0
 */
@Value
public class NbErrorInfo {
    /**
     * Reason Phrase. Human readable text.
     * -- GETTER --
     * エラー原因テキストを返却する。これは人間が可読なテキストである。
     */
    @NonNull
    String reason;

    public NbErrorInfo(String reason) {
        this.reason = reason;
    }
}
