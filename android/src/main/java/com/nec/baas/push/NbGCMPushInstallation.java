/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push;

import android.os.AsyncTask;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.nec.baas.core.NbErrorInfo;
import com.nec.baas.core.NbStatus;
import lombok.NonNull;

import java.io.IOException;

/**
 * GCM Push Installation。
 * <p>
 * 本クラスは GCM 2.0 用のクラスであり Deprecated である。
 * FCM を使用することを推奨する。
 * @deprecated {@link NbFcmPushInstallation} で置き換え
 * @since 1.0
 */
@Deprecated
public class NbGCMPushInstallation extends NbFcmPushInstallation {
    private static NbGCMPushInstallation sInstance;

    private GoogleCloudMessaging mGcm;

    /**
     * 現在の GCM Pushインスタレーション情報を取得する。<br/>
     *
     * @return インスタレーション情報
     * @throws java.lang.IllegalStateException
     */
    public static synchronized NbGCMPushInstallation getCurrentInstallation() {
        if (sInstance == null) {
            sInstance = new NbGCMPushInstallation();
        }
        // キャッシュに保存されている情報がマスターとなる
        sInstance.loadFromPreferences();
        return sInstance;
    }

    protected NbGCMPushInstallation() {
        super();
        mGcm = GoogleCloudMessaging.getInstance(mContext);
    }

    /**
     * GCMに対して登録処理を行う。<br/>
     * @param projectNumber プロジェクト番号(Sender ID)
     * @param callback 登録したインスタレーション情報を受け取るコールバック
     * @see NbFcmPushInstallationCallback
     */
    public void register(@NonNull final String projectNumber, @NonNull final NbFcmPushInstallationCallback callback) {

        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... voids) {
                try {
                    // GCMに対して登録を行い、Registration IDを取得する
                    // キャッシュに保存
                    String registrationId = mGcm.register(projectNumber);
                    saveRegistrationToken(registrationId);
                } catch (IOException e) {
                    // キャッシュは更新しない
                    return NbStatus.INTERNAL_SERVER_ERROR;
                }
                return NbStatus.OK;
            }

            @Override
            protected void onPostExecute(Integer status) {
                if (NbStatus.isNotSuccessful(status)) {
                    callback.onFailure(status, new NbErrorInfo("Failed to get registration ID."));
                } else {
                    // メモリ上のインスタレーション情報を返す
                    callback.onSuccess(NbGCMPushInstallation.this);
                }
            }
        }.execute(null, null, null);
    }
}
