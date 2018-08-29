/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push;

import com.nec.baas.core.*;
import com.nec.baas.json.*;

/**
 * Sse Pushサーバとの通信接続用コールバック。
 * @since 4.0.0
 */
public interface NbSsePushReceiveCallback {
    /**
     * SSE Push サーバとの接続に成功した場合に呼び出される。<br>
    */
    void onConnect();

    /**
     * SSE Push サーバとの切断に成功した場合に呼び出される。<br>
     */
    void onDisconnect();

    /**
     * Push メッセージを受信した場合に呼び出される。<br>
     * @param message 受信したPush メッセージ情報。<br>
     * id(イベントID)、event(イベントタイプ)、data(データ)、origin(SSE Push サーバのURI)を含む。<br>
     */
    void onMessage(NbJSONObject message);

    /**
     * エラーが発生した場合に呼び出される。<br>
     * @param statusCode ステータスコード。
     * @param errorInfo エラー理由。
     */
    void onError(int statusCode, NbErrorInfo errorInfo);

    /**
     * ハートビート喪失時に呼び出される
     */
    void onHeartbeatLost();
}
