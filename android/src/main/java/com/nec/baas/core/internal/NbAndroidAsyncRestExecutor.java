/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core.internal;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;

import com.nec.baas.core.*;
import com.nec.baas.http.*;
import com.nec.baas.util.*;

import java.io.IOException;

import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 非同期 REST 実行 Executor
 */
public class NbAndroidAsyncRestExecutor extends AsyncTask<Request, Void, Response> implements NbRestExecutor {
    private static final NbLogger log = NbLogger.getLogger(NbAndroidAsyncRestExecutor.class);

    static final int BAD_REQUEST = 400;
    static final int COUNTER_MAX = 100000;
    private Context mContext;
    private NbRestResponseHandler mHandler;
    private SharedPreferences mPreference;
    private int mResult;
    private Request mRequest;

    private static final String PREFERENCE_NAME = "apicounter_pref";
    private static final String API_COUNTER_KEY = "apicounter";
    private static long counter = -1;

    /**
     * コンストラクタ
     * @param context コンテキスト
     */
    NbAndroidAsyncRestExecutor(Context context) {
        mContext = context;
        mResult = NbStatus.INTERNAL_SERVER_ERROR;
        mPreference = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeRequest(Request request, NbRestResponseHandler handler) {
        if (request == null) {
            Response response = new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(BAD_REQUEST)
                    .message("Execute Error")
                    .build();
            handler.handleResponse(response, BAD_REQUEST);
            throw new IllegalArgumentException("null request");
        }
        mHandler = handler;
        executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, request);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response executeRequestSync(Request request) throws IOException {
        incrementCount();
        Response response = NbHttpClient.getInstance().executeRequest(request);
        return response;
    }

    @Override
    protected Response doInBackground(Request... params) {
        if (params == null) {
            log.severe("doInBackground() <end> err param is null");
            return null;
        }
        Request request = params[0];
        mRequest = request; // save request
        Response response = null;
        String errMsg = "Execute Error";
        try {
            log.fine("doInBackground() {0} {1}", request.method() ,request.url().toString());
            response = executeRequestSync(request);
        } catch (IOException e) {
            //e.printStackTrace();
            log.severe("doInBackground() executeRequestSync() IOException : {0}", e.getMessage());
            errMsg = "I/O Error : " + e.getMessage();
        } catch (Exception e) {
            //e.printStackTrace();
            log.severe("doInBackground() executeRequestSync() Exception : {0}", e.getMessage());
            errMsg = "Execute Error : " + e.getMessage();
        }

        if (response == null) {
            response = new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(NbStatus.INTERNAL_SERVER_ERROR)
                    .message(errMsg)
                    .build();
        }

        //UIスレッドへ切り替わる前に済ませておく処理を行う。
        try {
            mResult = NbRestResponseHandlerUtil.preHandleResponse(response, mHandler);
        } catch (Exception e) {
            //e.printStackTrace();
            log.severe("doInBackground() preHandleResponse() exception: {0}", e.getMessage());
            mResult = NbStatus.UNPROCESSABLE_ENTITY_ERROR;  //クライアント内部のエラーを想定
        }
        return response;
    }

    @Override
    protected void onPostExecute(Response response) {
        String errMsg = "Execute Error";
        //アプリ側コールバック呼び出し中含み、handleResponseで例外が発生する
        //可能性があるため、ここでもtry-catch句で受ける。
        try {
            if (response != null) {
                mHandler.handleResponse(response, mResult);
            } else {
                log.severe("onPostExecute() can't receive response.");
                response = new Response.Builder()
                        .request(mRequest)
                        .protocol(Protocol.HTTP_1_1)
                        .code(NbStatus.INTERNAL_SERVER_ERROR)
                        .message(errMsg)
                        .build();
                mHandler.handleResponse(response, NbStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            log.severe("onPostExecute() handleResponse() exception : {0}", e.getMessage());
            e.printStackTrace();
        } catch (AssertionError e) {
            NbJunitErrorNotifier.notify(e);
        } finally {
            mRequest = null;
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    // ignore
                }
                //Thread looper = new Thread(new HttpResponseClose().build(response));
                //looper.start();
            }
        }
    }

    /**
     * レスポンス非同期クローズクラス。
     */
    /*
    public class HttpResponseClose implements Runnable {
        private Closeable mCloseable = null;

        //
        // コンストラクタ。
        // クローズ用のレスポンスをフィールドで保持する。
        //
        public HttpResponseClose build(Closeable response) {
            HttpResponseClose httpResponse = new HttpResponseClose();
            httpResponse.mCloseable = response;
            return httpResponse;
        }

        @Override
        public void run() {
            if (mCloseable != null) {
                try {
                    mCloseable.close();
                } catch (Exception e) {
                    // just ignore
                    // TODO: okhttp3 の場合 NPE が発生することがある(body == null の場合)ため、
                    // Exception で catch する
                }
            }
            mCloseable = null;
        }
    }
    */

    private void incrementCount() {
        synchronized (mPreference) {
            if (counter == -1) {
                counter = mPreference.getLong(API_COUNTER_KEY, 0);
            }
            counter++;
            if (COUNTER_MAX < counter) {
                counter = 1;
            }

            // 100回に1回保存する
            if (0 == (counter % 100)) {
                saveApiCount();
            }
        }
    }

    @Override
    public long getApiCount() {
        if (counter == -1) {
            synchronized (mPreference) {
                counter = mPreference.getLong(API_COUNTER_KEY, 0);
            }
        }
        return counter;
    }

    @Override
    public void saveApiCount() {
        if (counter == -1) {
            //do nothing
            return;
        }
        synchronized (mPreference) {
            Editor editor = mPreference.edit();
            editor.putLong(API_COUNTER_KEY, counter).commit();
        }
    }

}
