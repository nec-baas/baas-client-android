/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.generic.internal;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.http.*;
import com.nec.baas.util.*;

import java.io.IOException;

import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 汎用 REST Executor。
 * 別スレッドで REST API を実行する。
 * スレッドはスレッドプールで管理される。
 */
public class NbGenericRestExecutor implements NbRestExecutor {
    private static final NbLogger log = NbLogger.getLogger(NbGenericRestExecutor.class);

    private NbHttpClient mHttpClient;
    private static long sApiCounter = 0;

    /**
     * コンストラクタ
     */
    public NbGenericRestExecutor(NbHttpClient httpClient) {
        mHttpClient = httpClient;
    }
    
    @Override
    public void executeRequest(final Request request, final NbRestResponseHandler handler) {
        NbUtil.runInBackground(new Runnable() {
            @Override
            public void run() {
                Response response;
                try {
                    response = NbGenericRestExecutor.this.executeRequestSync(request);
                } catch (Exception e) {
                    final String errMsg = "HTTP Execute Error: " + e.toString();
                    log.severe(errMsg);
                    if (NbSetting.getOperationMode() == NbOperationMode.DEBUG) {
                        //e.printStackTrace();
                    }
                    response = new Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(NbStatus.INTERNAL_SERVER_ERROR)
                            .message(errMsg)
                            .build();
                }

                try {
                    NbRestResponseHandlerUtil.handleResponse(response, handler);
                }
                catch (AssertionError e) {
                    NbJunitErrorNotifier.notify(e);
                }
                finally {
                    // TODO: recheck
                    response.close();
                }
            }
        });
    }

    @Override
    public Response executeRequestSync(Request request) throws IOException {
        sApiCounter++;
        return mHttpClient.executeRequest(request);
    }

    @Override
    public long getApiCount() {
        return sApiCounter;
    }

    @Override
    public void saveApiCount() {
        // do nothing
    }
}
