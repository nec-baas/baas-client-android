/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import android.content.Context;
import lombok.NonNull;

/**
 * Androidシステム情報へアクセスするためのクラス.<br>
 * @since 1.0
 */
public class NbAndroidSystemManager implements SystemManager {
    private Context mContext;

    /**
     * AndroidSystemManagerコンストラクタ.<br>
     * @since 1.0
     */
    public NbAndroidSystemManager(@NonNull Context context) {
        mContext = context;
    }

    /**
     * キャッシュファイル保存先ディレクトリパスの取得.
     * @return String ディレクトリパス
     */
    @Override
    public String getCacheFilePath() {
        if (mContext == null) {
            throw new IllegalStateException();
        }

        String path = mContext.getFilesDir().getPath();
        return path;
    }
}
