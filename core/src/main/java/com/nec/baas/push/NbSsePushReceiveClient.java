/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push;

import com.nec.baas.core.NbErrorInfo;
import com.nec.baas.core.NbStatus;
import com.nec.baas.core.internal.NbServiceImpl;
import com.nec.baas.json.NbJSONObject;
import com.nec.baas.util.NbLogger;

import com.nec.push.sse.*;
import lombok.NonNull;

import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE Push メッセージ受信クライアントクラス。<p/>
 *
 * SSE Push サーバへの接続(Pushメッセージ受信)/切断機能を提供する。
 * <p/>
 * @since 4.0.0
 */
public class NbSsePushReceiveClient {
    private static final NbLogger log = NbLogger.getLogger(NbSsePushReceiveClient.class);

    /*package*/ SsePushClient mSsePushClient;
    private String mUserName = null;
    private String mPassword = null;
    private String mUri = null;
    /*package*/ int mClientState = STATE_IDLE;
    /*package*/ NbSsePushReceiveCallback mSseReceiveClientCallback = null;

    private static final Semaphore sLock = new Semaphore(1);

    // 状態
    /** 処理無し状態。接続中状態、切断中状態のどちらでもない状態 */
    private static final int STATE_IDLE = 0;
    /** Pushサーバとの接続待ち状態、接続中状態、接続完了状態 */
    private static final int STATE_CONNECTING = 1;
    /** 切断待ち状態 */
    private static final int STATE_DISCONNECTING = 2;

    // Key
    private static final String JSON_KEY_ID = "id";
    private static final String JSON_KEY_EVENT = "event";
    private static final String JSON_KEY_DATA = "data";
    private static final String JSON_KEY_ORIGIN = "origin";

    private static final String ALREADY_CONNECTION = "Connection is already being processed.";
    private static final String INSTALLATION_DOESNOT_EXIST = "Installation doesn't exist.";
    private static final String INVALID_ACCOUNT = "Invalid account.";
    private static final String COULD_NOT_ACQUIRE_LOCK = "could not acquire lock.";

    private final AtomicBoolean mIsShutdown = new AtomicBoolean(false);

    /**
     * コンストラクタ
     * @throws java.lang.IllegalStateException
     */
    public NbSsePushReceiveClient() {
        mSsePushClient = new SsePushClient();
    }

    /**
     * ハートビート間隔を設定する
     * @param heartbeatInterval ハートビート間隔
     * @param timeUnit 時間単位
     */
    public void setHeartbeatInterval(long heartbeatInterval, TimeUnit timeUnit) {
        mSsePushClient.setHeartbeatInterval(heartbeatInterval, timeUnit);
    }

    /**
     * Sse Pushサーバと接続する。<p/>
     *
     * UIスレッドにコールバックされる。<br>
     * @param event イベントタイプ一覧
     * @param callback Push メッセージ、エラー情報を受け取るコールバック
     * @throws java.lang.IllegalArgumentException
     * @throws java.lang.IllegalStateException
     * @see NbSsePushReceiveCallback
     */
    public void connect(@NonNull Set<String> event, @NonNull final NbSsePushReceiveCallback callback) {
        log.fine("connect() <start>");

        if (event.size() == 0) {
            throw new IllegalArgumentException("event is empty");
        }
        mSseReceiveClientCallback = callback;

        // 接続状態チェック
        if (getClientState() != STATE_IDLE) {
            log.severe("connect() State is not idle. state=" +getClientState());
            errorUiThreadCallback(NbStatus.CANCELED, ALREADY_CONNECTION);
            return;
        }

        // インスタレーション情報チェック
        if (!installationExists()){
            log.severe("connect() Installation doesn't exist.");
            errorUiThreadCallback(NbStatus.CANCELED, INSTALLATION_DOESNOT_EXIST);
            return;
        }

        // 状態、コールバック設定
        SsePushEventCallback eventCallback = new SsePushEventCallback() {
            @Override
            public void onMessage(SsePushEvent ssePushEvent) {
                messageUiThreadCallback(ssePushEvent);
            }
        };
        for (String eventType : event) {
            mSsePushClient.registerEvent(eventType, eventCallback);
        }

        log.info("connect: uri = {0}", mUri);
        mSsePushClient.open(mUri, mUserName, mPassword, new ClientCallback());
        log.fine("connect() <end>");
    }

    private class ClientCallback implements SsePushClientCallback {
        @Override
        public void onOpen() {
            setClientState(STATE_CONNECTING);
            connectUiThreadCallback();
        }

        @Override
        public void onClose() {
            setClientState(STATE_IDLE);
            disconnectUiThreadCallback();
        }

        @Override
        public void onError(int statusCode, String errorInfo) {
            if (statusCode == NbStatus.UNAUTHORIZED) {
                // 認証子不一致（"HTTP 401 Unauthorized"）の場合、自動回復処理を実施する
                log.info("Start autoRecovery from 401 Unauthorized error.");
                autoRecoveryForUnauthentication(statusCode, errorInfo);
            } else {
                errorUiThreadCallback(statusCode, errorInfo);
            }
        }

        @Override
        public void onHeartbeatLost() {
            heartbeatLostUiThreadCallback();
        }
    }

    private void autoRecoveryForUnauthentication(int statusCode, String errorInfo) {
        if (!installationExists()){
            // インスタレーションが削除されている場合など不正の場合、アプリにエラー通知する
            log.severe("onError() Auto recovery fails because installation doesn't exist.");
            errorUiThreadCallback(NbStatus.CANCELED, INSTALLATION_DOESNOT_EXIST);
            disconnect();
            return;
        }

        // インスタレーションを再登録する
        try {
            acquireLock();
        } catch (IllegalStateException e) {
            log.severe("onError() Auto recovery fails because of lock.");
            releaseLock();
            errorUiThreadCallback(NbStatus.CANCELED, COULD_NOT_ACQUIRE_LOCK);
            disconnect();
            return;
        }

        NbSsePushInstallation.getCurrentInstallation().save(new NbSsePushInstallationCallback() {
            @Override
            public void onSuccess(NbSsePushInstallation installation) {
                releaseLock();
                if (installationExists()) {
                    // 3秒ウェイトする
                    try {
                        TimeUnit.SECONDS.sleep(3);
                    } catch (InterruptedException e) {
                        log.info("Interrupt auto-recovery.");
                        Thread.currentThread().interrupt();
                    }

                    if (mIsShutdown.get()) {
                        log.info("Cancel auto-recovery.");
                        return;
                    }

                    // 再登録した認証子で再接続する
                    mSsePushClient.close();
                    mSsePushClient.open(mUri, mUserName, mPassword, new ClientCallback());
                } else {
                    log.severe("onError() Auto recovery fails because installation doesn't exist.");
                    errorUiThreadCallback(NbStatus.CANCELED, INSTALLATION_DOESNOT_EXIST);
                    disconnect();
                }
            }

            @Override
            public void onFailure(int statusCode, NbErrorInfo errorInfo) {
                log.severe("onError() Auto recovery fails because installation can't be registered.");
                releaseLock();
                errorUiThreadCallback(NbStatus.CANCELED, INVALID_ACCOUNT);
                disconnect();
            }
        });
    }

    /**
     * Sse Pushサーバと切断する。<p/>
     *
     * 接続時に指定したコールバックにコールバックされる。<br>
     * @see NbSsePushReceiveCallback
     */
    public void disconnect() {
        log.fine("disconnect() <start>");

        // 接続状態チェック
        if (getClientState() == STATE_CONNECTING) {
            setClientState(STATE_DISCONNECTING);
        }
        mSsePushClient.close();

        log.fine("disconnect() <end>");
    }

    /**
     * テスト用メソッド．
     * autoRecoveryForUnauthentication を止める
     */
    void shutdown() {
        mIsShutdown.set(true);
    }

    /**
     * インスタレーションの更新権利を取得する。<p/>
     *
     * 更新が終わったら、releaseLock()を呼ぶ必要がある。<br>
     * @see NbSsePushReceiveCallback
     * @throws IllegalStateException ロック取得エラー
     */
    public static synchronized void acquireLock() throws IllegalStateException {
        log.fine("acquireLock() <start>");

        if (!sLock.tryAcquire()) {
            throw new IllegalStateException(COULD_NOT_ACQUIRE_LOCK);
        }

        log.fine("acquireLock() <end>");
    }

    /**
     * インスタレーションの更新権利を解放する。<br/>
     */
    public static synchronized void releaseLock() {
        log.fine("releaseLock() <start>");

        sLock.release();

        log.fine("releaseLock() <end>");
    }

    /**
     * 状態を設定する。<br>
     * @param state 状態
     */
    private void setClientState(int state) {
        synchronized (this) {
            log.fine("setClientState() state=" +state);
            mClientState = state;
        }
    }

    /**
     * 状態を取得する。<br>
     * @return 状態
     */
    private int getClientState() {
        return mClientState;
    }

    /**
     * インスタレーション情報(username、password、uri)が存在するかをチェックする。<br>
     * @return チェック結果
     */
    private boolean installationExists() {
        NbSsePushInstallation installation = NbSsePushInstallation.getCurrentInstallation();

        if (installation.getInstallationId() == null) {
            return false;
        }

        mUserName = installation.getUserName();
        mPassword = installation.getPassword();
        mUri = installation.getUri();

        if (mUserName != null && !mUserName.isEmpty() &&
                mPassword != null && !mPassword.isEmpty() &&
                mUri != null && !mUri.isEmpty()) {
            return true;
        }

        return false;
    }

    /**
     * イベント情報をUI スレッドでコールバックする。<br>
     * @param ssePushEvent
     */
    private void messageUiThreadCallback(SsePushEvent ssePushEvent) {
        // JSONObject形式に変更
        final NbJSONObject message = new NbJSONObject();
        message.put(JSON_KEY_ID, ssePushEvent.getId());
        message.put(JSON_KEY_EVENT, ssePushEvent.getEvent());
        message.put(JSON_KEY_DATA, ssePushEvent.getData());
        message.put(JSON_KEY_ORIGIN, ssePushEvent.getOrigin());

        // UIスレッドでコールバックする
        NbServiceImpl.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSseReceiveClientCallback.onMessage(message);
            }
        });
    }

    /**
     * エラー情報をUI スレッドでコールバックする。<br>
     * @param statusCode
     * @param errorInfo
     */
    private void errorUiThreadCallback(final int statusCode, final String errorInfo) {
        // UIスレッドでコールバックする
        NbServiceImpl.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSseReceiveClientCallback.onError(statusCode, new NbErrorInfo(errorInfo));
            }
        });
    }

    /**
     * SSE Push サーバとの接続情報をUI スレッドでコールバックする。<br>
     */
    private void connectUiThreadCallback() {
        // UIスレッドでコールバックする
        NbServiceImpl.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSseReceiveClientCallback.onConnect();
            }
        });
    }

    /**
     * SSE Push サーバとの接続情報をUI スレッドでコールバックする。<br>
     */
    private void disconnectUiThreadCallback() {
        // UIスレッドでコールバックする
        NbServiceImpl.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSseReceiveClientCallback.onDisconnect();
            }
        });
    }

    private void heartbeatLostUiThreadCallback() {
        NbServiceImpl.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSseReceiveClientCallback.onHeartbeatLost();
            }
        });
    }
}
