/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push.internal;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.nec.baas.core.NbService;
import com.nec.baas.core.internal.NbAndroidService;
import com.nec.baas.core.internal.NbServiceImpl;
import com.nec.baas.push.NbPushInstallation;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import static android.os.Build.VERSION.SDK_INT;

/**
 * Android 用 Pushインスタレーションクラス。
 * @since 4.0.0
 */
@Accessors(prefix = "m")
public abstract class NbAndroidPushInstallation extends NbPushInstallation {
    @Getter
    @Setter
    protected Context mContext;

    private static final String OS_TYPE_ANDROID = "android";

    private static final String NOT_GET_PACKAGEINFO = "Could not get packageInfo";

    /**
     * コンストラクタ
     */
    public NbAndroidPushInstallation() {
        super();

        mNebulaService = (NbServiceImpl)NbService.getInstance();
        NbAndroidService nbAndroidService = (NbAndroidService)mNebulaService;
        mContext = nbAndroidService.getContext();
    }

    @Override
    public String _getOsType() {
        return OS_TYPE_ANDROID;
    }

    @Override
    public String _getOsVersion() {
        return String.valueOf(SDK_INT);
    }

    /**
     * バージョンコードを取得する。<br>
     * @return AndroidManifest.xml の versionCode
     */
    @Override
    public int getVersionCode() {
        return getPackageInfo().versionCode;
    }

    /**
     * バージョン名を取得する。<br>
     * @return AndroidManifest.xml の versionName
     */
    @Override
    public String getVersionName() {
        return getPackageInfo().versionName;
    }

    /**
     * パッケージ情報を取得する。<br>
     * @return getPackageInfo
     */
    public PackageInfo getPackageInfo() {
        try {
            return mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(NOT_GET_PACKAGEINFO);
        }
    }
}
