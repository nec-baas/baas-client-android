/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.file.internal;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.file.*;

import java.util.List;

/**
 * NbFileMetadataCallback List -> Single 変換ラッパー
 */
class FileMetadataCallbackListToSingleWrapper extends NbListToSingleCallbackWrapper<NbFileMetadata> implements NbCallback<NbFileMetadata> {
    public static FileMetadataCallbackListToSingleWrapper wrap(NbFileMetadataCallback callback) {
        if (callback == null) return null;
        return new FileMetadataCallbackListToSingleWrapper(callback);
    }

    private FileMetadataCallbackListToSingleWrapper(NbCallback<List<NbFileMetadata>> callback) {
        super(callback);
    }
}
