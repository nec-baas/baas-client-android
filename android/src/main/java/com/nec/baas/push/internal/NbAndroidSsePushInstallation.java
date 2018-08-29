/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push.internal;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.push.*;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import static android.os.Build.VERSION.SDK_INT;

/**
 * Android 用 SSE Pushインスタレーションクラス。<p/>
 * @since 4.0.0
 */
@Accessors(prefix = "m")
public class NbAndroidSsePushInstallation extends NbSsePushInstallation {
    @Getter @Setter // for test
    protected Context mContext;

    private static final String OS_TYPE_ANDROID = "android";

    private static final String NOT_GET_PACKAGEINFO = "Could not get packageInfo";

    /**
     * コンストラクタ
     */
    public NbAndroidSsePushInstallation() {
        super();

        mNebulaService = (NbServiceImpl) NbService.getInstance();
        NbAndroidService nbAndroidService = (NbAndroidService)mNebulaService;
        mContext = nbAndroidService.getContext();
    }

    @Override
    protected String _getOsType() {
        return OS_TYPE_ANDROID;
    }

    @Override
    protected String _getOsVersion() {
        return String.valueOf(SDK_INT);
    }

    /**
     * バージョンコードを取得する。<br>
     * @return AndroidManifest.xml の versionCode
     */
    @Override
    protected int getVersionCode() {
        return getPackageInfo().versionCode;
    }

    /**
     * バージョン名を取得する。<br>
     * @return AndroidManifest.xml の versionName
     */
    @Override
    protected String getVersionName() {
        return getPackageInfo().versionName;
    }

    /**
     * パッケージ情報を取得する。<br>
     * @return getPackageInfo
     */
    protected PackageInfo getPackageInfo() {
        try {
            return mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(NOT_GET_PACKAGEINFO);
        }
    }

    @Override
    protected NbPreferences getSharedPreferences() {
        return new NbAndroidPreferences(mContext, SSE_PREFERENCE_NAME);
    }

    @Override
    protected NbPreferences getSharedPreferences(String prefsName) {
        return new NbAndroidPreferences(mContext, prefsName);
    }
}
