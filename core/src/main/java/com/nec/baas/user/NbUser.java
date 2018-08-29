/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.user;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.json.*;
import com.nec.baas.offline.internal.*;
import com.nec.baas.user.internal.*;
import com.nec.baas.util.*;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import okhttp3.Request;
import okhttp3.Response;

/**
 * ユーザ情報の管理、認証を行うクラス。
 *
 * <p><strong>本クラスのインスタンスはスレッドセーフではない。</strong></p>
 * @since 1.0
 */
public class NbUser {
    private static final NbLogger log = NbLogger.getLogger(NbUser.class);

    /*package*/ static NbServiceImpl sNebulaService;

    private NbServiceImpl mService;
    /*package*/ NbUserEntity mUserEntity = new NbUserEntity();

    private static final String USER_URL = "/users";
    private static final String LOGIN_URL = "/login";
    private static final String PASSWORD_RESET_URL = "/request_password_reset";
    private static final String CURRENT_PATH_COMPONENT = "current";

    /**
     * デフォルトコンストラクタ (マルチテナント非対応)
     */
    public NbUser() {
        mService = ensureNebulaService();
    }

    /**
     * コンストラクタ (マルチテナント対応)
     * @param service サービス
     */
    public NbUser(NbService service) {
        mService = (NbServiceImpl)service;
    }

    // for test
    public NbService _getService() {
        return mService;
    }

    /**
     * ログインパラメータ。
     * <p>
     * username/email, password, token などを保持する。
     * 使用例は以下のとおり。
     * <pre>
     *     // username - password の場合
     *     LoginParam param1 = new LoginParam().username("user1").password("Passw0rD");
     *
     *     // email - password の場合
     *     LoginParam param2 = new LoginParam().email("user1@example.com").password("Passw0rD");
     *
     *     // one time token の場合
     *     LoginParam param3 = new LoginParam().token("TOKEN");
     * </pre>
     * @see #login(LoginParam, NbCallback<NbUser>)
     * @see #login(NbService, LoginParam, NbCallback<NbUser>)
     * @since 6.5.0
     */
    @Data
    @Accessors(fluent = true)
    public static class LoginParam {
        /**
         * ユーザ名
         */
        String username;

        /**
         * E-mail アドレス
         */
        String email;

        /**
         * パスワード
         */
        String password;

        /**
         * ワンタイムトークン
         */
        String token;

        /*package*/ void validate() {
            if (token == null) {
                if (password == null) {
                    throw new IllegalArgumentException("both password and token are null");
                }
                //ユーザ名とE-mailどちらも指定されない場合
                if (username == null && email == null) {
                    throw new IllegalArgumentException("both username and email are null");
                }
                //ユーザ名が使用できない場合
                if (!isValidUserName(username)) {
                    throw new IllegalArgumentException("username is invalid");
                }
            }
        }

        /*package*/ NbJSONObject toJson() {
            NbJSONObject json = new NbJSONObject();

            if (email != null) json.put(NbKey.EMAIL, email);
            if (username != null) json.put(NbKey.USERNAME, username);
            if (password != null) json.put(NbKey.PASSWORD, password);
            if (token != null) json.put(NbKey.ONE_TIME_TOKEN, token);
            return json;
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            if (username != null) builder.append(" username=").append(username);
            if (email != null) builder.append(" email=").append(email);
            if (token != null) builder.append(" token=").append(token);
            return builder.toString().trim();
        }
    }

    private static void executeRequest(NbServiceImpl service, Request request, NbRestResponseHandler handler) {
        service.createRestExecutor().executeRequest(request, handler);
    }

    /**
     * BaaSサーバへログインを行う。(マルチテナント非対応)
     * クライアント証明書認証使用時は使用しないこと。
     * @param username ログインするユーザのユーザ名 (username/emailどちらか一方を指定)
     * @param email ログインするユーザのEmail (username/emailどちらか一方を指定)
     * @param password ログインするユーザのパスワード
     * @param callback ログインしたユーザを受け取るコールバック
     * @deprecated {@link #loginWithUsername(String, String, NbCallback)}, {@link #loginWithEmail(String, String, NbCallback)},
     * {@link #login(LoginParam, NbCallback)}, {@link #login(NbService, LoginParam, NbCallback)} で置き換え
     */
    @Deprecated
    public static void login(String username, String email, String password, final NbUsersCallback callback) {
        LoginParam param = new LoginParam().username(username).email(email).password(password);
        login(param, NbUsersCallbackWrapper.wrap(callback));
    }

    @Deprecated
    public static void login(String username, String email, String password, final NbCallback<NbUser> callback) {
        LoginParam param = new LoginParam().username(username).email(email).password(password);
        login(param, callback);
    }

    /**
     * ユーザ名を指定してBaaSサーバへログインを行う。(マルチテナント非対応)
     * <p>
     * ログイン動作の詳細は {@link #login(NbService, LoginParam, NbCallback)} を参照。
     * クライアント証明書認証使用時は使用しないこと。
     * @param username ログインするユーザのユーザ名
     * @param password ログインするユーザのパスワード
     * @param callback ログインしたユーザを受け取るコールバック
     */
    public static void loginWithUsername(String username, String password, final NbCallback<NbUser> callback) {
        loginWithUsername(ensureNebulaService(), username, password, callback);
    }

    /**
     * ユーザ名を指定してBaaSサーバへログインを行う。(マルチテナント対応)
     * <p>
     * ログイン動作の詳細は {@link #login(NbService, LoginParam, NbCallback)} を参照。
     * クライアント証明書認証使用時は使用しないこと。
     * @param service NbService
     * @param username ログインするユーザのユーザ名
     * @param password ログインするユーザのパスワード
     * @param callback ログインしたユーザを受け取るコールバック
     */
    public static void loginWithUsername(NbService service, String username, String password, final NbCallback<NbUser> callback) {
        LoginParam param = new LoginParam().username(username).password(password);
        login(service, param, callback);
    }

    /**
     * E-mailを指定してBaaSサーバへログインを行う。(マルチテナント非対応)
     * <p>
     * ログイン動作の詳細は {@link #login(NbService, LoginParam, NbCallback)} を参照。
     * クライアント証明書認証使用時は使用しないこと。
     * @param email ログインするユーザのEmail
     * @param password ログインするユーザのパスワード
     * @param callback ログインしたユーザを受け取るコールバック
     */
    public static void loginWithEmail(String email, String password, final NbCallback<NbUser> callback) {
        loginWithEmail(ensureNebulaService(), email, password, callback);
    }

    /**
     * E-mailを指定してBaaSサーバへログインを行う。(マルチテナント非対応)
     * <p>
     * ログイン動作の詳細は {@link #login(NbService, LoginParam, NbCallback)} を参照。
     * クライアント証明書認証使用時は使用しないこと。
     * @param service NbService
     * @param email ログインするユーザのEmail
     * @param password ログインするユーザのパスワード
     * @param callback ログインしたユーザを受け取るコールバック
     */
    public static void loginWithEmail(NbService service, String email, String password, final NbCallback<NbUser> callback) {
        LoginParam param = new LoginParam().email(email).password(password);
        login(service, param, callback);
    }

    /**
     * BaaSサーバへログインを行う。(マルチテナント非対応)
     * <p>
     * ログイン動作の詳細は {@link #login(NbService, LoginParam, NbCallback)} を参照。
     * クライアント証明書認証使用時は使用しないこと。
     * @param param ログインパラメータ。username, email, token いずれか必須。
     * @param callback ログインしたユーザを受け取るコールバック
     * @since 6.5.0
     */
    public static void login(LoginParam param, final NbCallback<NbUser> callback) {
        login(ensureNebulaService(), param, callback);
    }

    /**
     * BaaSサーバへログインを行う。(マルチテナント対応)。
     * <p>
     * クライアント証明書認証使用時は使用しないこと。
     * @deprecated {@link #login(NbService, LoginParam, NbCallback)} で置き換え
     */
    @Deprecated
    public static void login(NbService service, String username, String email, String password, final NbUsersCallback callback) {
        LoginParam param = new LoginParam().username(username).email(email).password(password);
        login(service, param, NbUsersCallbackWrapper.wrap(callback));
    }

    @Deprecated
    public static void login(NbService service, String username, String email, String password, final NbCallback<NbUser> callback) {
        LoginParam param = new LoginParam().username(username).email(email).password(password);
        login(service, param, callback);
    }

    /**
     * BaaSサーバへログインを行う (マルチテナント対応)。
     * <p>
     * ID/パスワードを使用してログインする場合は、param には username, email のいずれかと password を指定すること。
     * 認証連携によるワンタイムトークンログインを行う場合は、param には token のみを指定すること。
     *
     * <p>ログインに成功した場合はログアウトするか、
     * セッショントークンの有効期限を迎えるまでログイン状態が維持される。
     *
     * <p>オフライン時はログインキャッシュを使用する。ログインキャッシュが
     * ローカルDBに存在しない場合はログイン不可となる。
     * オフライン時もログアウトするか、ログインキャッシュの有効期限を
     * 迎えるまでログイン状態が維持される。
     *
     * <p>オフライン時にログインし、その後オンライン状態に遷移した場合、
     * ログインキャッシュは使わずセッショントークンを使用する。
     * セッショントークンが利用不可であれば、オンラインでAPIを実行した際に
     * 認証エラーとなる。その際は再ログインが必要。
     * <p>
     * ワンタイムトークンを使用したログインは、オフライン時には使用できない。
     * クライアント証明書認証使用時は使用しないこと。
     * @param service NbService
     * @param param ログインパラメータ。username, email, token いずれか必須。
     * @param callback ログインしたユーザを受け取るコールバック
     * @since 6.5.0
     */
    public static void login(@NonNull NbService service, @NonNull LoginParam param, @NonNull final NbCallback<NbUser> callback) {
        log.fine("login() <start> " + param.toString());
        param.validate();

        NbOfflineService offlineService = ((NbServiceImpl)service).getOfflineService();
        if (offlineService == null || offlineService.isOnline()) {
            log.fine("login() online");
            loginOnLine((NbServiceImpl)service, param, callback);
        } else {
            log.fine("login() offline");
            //オフライン処理
            loginOnLocal((NbServiceImpl)service, param, callback);
        }
        log.fine("login() <end>");
    }

    /**
     * NbCallback<List<NbUser>> を NbCallback<NbUser> で wrap する
     */
    private static class NbUsersCallbackWrapper extends NbListToSingleCallbackWrapper<NbUser> implements NbCallback<NbUser> {
        public static NbUsersCallbackWrapper wrap(NbCallback<List<NbUser>> callback) {
            if (callback == null) return null;
            return new NbUsersCallbackWrapper(callback);
        }

        private NbUsersCallbackWrapper(NbCallback<List<NbUser>> callback) {
            super(callback);
        }
    }

    /**
     * オンライン状態でのログイン処理
     * @param param パラメータ
     * @param callback コールバック
     */
    private static void loginOnLine(final NbServiceImpl service, final LoginParam param, final NbCallback<NbUser> callback) {
        log.fine("loginOnLine() <start> " + param.toString());
        //リクエスト作成
        NbJSONObject bodyJson = param.toJson();

        Request request = service.getHttpRequestFactory().post(LOGIN_URL).body(bodyJson).sessionNone().build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbUser.loginOnLine()") {
            @Override
            public void onSuccess(Response response, NbJSONObject json) {
                String token = json.getString(NbKey.SESSION_TOKEN);
                long expire = json.getLong(NbKey.EXPIRE);
                List<String> groupList = json.getJSONArray(NbKey.GROUPS);

                NbSessionToken sessionToken = service.getSessionToken();
                sessionToken.setSessionToken(token, expire);
                service.getHttpRequestFactory().setSessionToken(sessionToken);

                NbUser user = new NbUser(service);
                user.setUserEntity(new NbUserEntity(json));
                sessionToken.setSessionUserEntity(user.mUserEntity);

                // オフラインキャッシュ生成
                if (param.password() != null) {
                    NbOfflineService offlineService = service.getOfflineService();
                    if (offlineService != null) {
                        offlineService.loginService().setLoginCache(param.password(),
                                token, expire, user.mUserEntity, groupList);
                    }
                }

                callback.onSuccess(user);
            }

            @Override
            public void onFailure(int statusCode, NbJSONObject json) {
                log.severe("Server Login Fail " + param.toString());
                // 401の場合はログインキャッシュを削除する
                if (statusCode == NbStatus.UNAUTHORIZED) {
                    NbOfflineService offlineService = service.getOfflineService();
                    if (offlineService != null) {
                        offlineService.loginService().deleteLoginCache(param.username(), param.email());
                    }
                }
                super.onFailure(statusCode, json);
            }
        };

        execLoginOnline(service, request, handler);
        log.fine("loginOnLine() <end>");
    }

    protected static void execLoginOnline(NbServiceImpl service, Request request, NbRestResponseHandler handler) {
        executeRequest(service, request, handler);
    }

    // オフラインログイン
    private static void loginOnLocal(final NbServiceImpl service, final LoginParam param, final NbCallback<NbUser> callback) {
        log.fine("loginOnLocal() <start> " + param.toString());

        NbOfflineService offlineService = service.getOfflineService();
        int status = offlineService.loginService().login(param.username(), param.email(), param.password());
        log.fine("loginOnLocal() run() status=" + status);

        if (NbStatus.isSuccessful(status)) {
            NbOfflineResult result = offlineService.loginService().getLoginCache(param.username(), param.email());
            NbUserEntity entity = new NbUserEntity(result.getJsonData());
            NbUser user = new NbUser(service);
            user.setUserEntity(entity);

            //取得したキャッシュ情報をユーザ情報へ反映
            String token = (String) result.getJsonData().get(NbKey.SESSION_TOKEN);
            Long expire = ((Number) result.getJsonData().get(NbKey.EXPIRE)).longValue();
            NbSessionToken sessionToken = service.getSessionToken();
            sessionToken.setSessionToken(token, expire);
            service.getHttpRequestFactory().setSessionToken(sessionToken);
            sessionToken.setSessionUserEntity(entity);

            callback.onSuccess(user);
        } else {
            log.severe("Local Login Fail " + param.toString());
            callback.onFailure(status, new NbErrorInfo("Failed to login on local."));
        }

        log.fine("loginOnLocal() <end>");
    }

    /**
     * ログアウトを行う。(マルチテナント非対応)
     * <p>
     * クライアント証明書認証使用時は使用しないこと。
     * @deprecated {@link #logout(NbCallback)} で置き換え
     * @since 1.0
     */
    @Deprecated
    public static void logout(final NbUsersCallback callback) {
        logout(NbUsersCallbackWrapper.wrap(callback));
    }

    /**
     * ログアウトを行う。(マルチテナント非対応)
     * セッショントークンを破棄し、ログイン状態を解除する。
     * ログアウト処理ではログインキャッシュの削除は行わない。
     * クライアント証明書認証使用時は使用しないこと。
     * @param callback 実行結果を受け取るコールバック
     * @see com.nec.baas.user.NbUsersCallback
     * @since 6.5.0
     */
    public static void logout(final NbCallback<NbUser> callback) {
        log.fine("logout() <start>");

        logout(ensureNebulaService(), callback);
    }

    /**
     * ログアウトを行う (マルチテナント対応)
     * クライアント証明書認証使用時は使用しないこと。
     * @deprecated {@link #logout(NbService, NbCallback)} で置き換え
     * @since 3.0.0
     */
    @Deprecated
    public static void logout(final NbService service, final NbUsersCallback callback) {
        logout(service, NbUsersCallbackWrapper.wrap(callback));
    }

    /**
     * ログアウトを行う (マルチテナント対応)
     * セッショントークンを破棄し、ログイン状態を解除する。
     * ログアウト処理ではログインキャッシュの削除は行わない。
     * クライアント証明書認証使用時は使用しないこと。
     * @param service NbService
     * @param callback 実行結果を受け取るコールバック
     * @see com.nec.baas.user.NbUsersCallback
     * @since 6.5.0
     */
    public static void logout(@NonNull final NbService service, @NonNull final NbCallback<NbUser> callback) {
        final NbServiceImpl _service = (NbServiceImpl)service;

        NbOfflineService offlineService = _service.getOfflineService();
        if (offlineService == null || offlineService.isOnline()) {
            log.fine("logout() online");
            //リクエスト作成
            Request request;
            try {
                // Logout APIはsessionTokenが必須だが、クライアント証明書認証向けにOptionalとしてサーバに問い合わせる
                request = _service.getHttpRequestFactory().delete(LOGIN_URL).sessionOptional().build();
            } catch (SecurityException e) {
                log.severe("logout() makeDeleteRequest() ERR");
                callback.onFailure(NbStatus.UNAUTHORIZED, new NbErrorInfo("Failed to make DELETE request."));
                return;
            }

            NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbUser.logout()") {
                @Override
                public void onSuccess(Response response, NbJSONObject body) {
                    clearSessionToken(_service);
                    NbUserEntity entity = new NbUserEntity(body);
                    NbUser user = new NbUser(service);
                    user.setUserEntity(entity);
                    callback.onSuccess(user);
                }

                @Override
                public void onFailure(int statusCode, Response response) {
                    clearSessionToken(_service);
                    super.onFailure(statusCode, response);
                }
            };
            execLogout(_service, request, handler);
        } else {
            log.fine("logout() offline");

            if (!isLoggedIn(service)) {
                log.severe("logout() <end> ERR isLoggedIn=false");
                callback.onFailure(NbStatus.UNAUTHORIZED, new NbErrorInfo("Not logged in."));
                return;
            }

            //オフライン処理
            //オンライン時と同じく、ユーザIDを返すようにする
            String userId = clearSessionToken(_service);

            NbUser user = new NbUser(service);
            user.setUserId(userId);
            callback.onSuccess(user);
        }
        log.fine("logout() <end>");
    }

    private static String clearSessionToken(NbServiceImpl _service) {
        NbSessionToken sessionToken = _service.getSessionToken();
        String userId = getCurrentUser(_service).getUserId();

        sessionToken.clearSessionToken();

        NbOfflineService offlineService = _service.getOfflineService();
        if (offlineService != null) {
            offlineService.loginService().logout();
        }
        return userId;
    }

    protected static void execLogout(NbServiceImpl service, Request request, NbRestResponseHandler handler) {
        executeRequest(service, request, handler);
    }

    /**
     * ログイン状態の確認を行う (マルチテナント非対応)
     * クライアント証明書認証使用時は使用しないこと。
     * @return ログイン中であればtrue、未ログインであればfalseを返す
     * @since 1.0
     */
    public static boolean isLoggedIn() {
        return isLoggedIn(ensureNebulaService());
    }

    /**
     * ログイン状態の確認を行う (マルチテナント対応)
     * クライアント証明書認証使用時は使用しないこと。
     * @param service NbService
     * @return ログイン中であればtrue、未ログインであればfalseを返す
     * @since 3.0.0
     */
    public static boolean isLoggedIn(@NonNull NbService service) {
        NbServiceImpl _service = (NbServiceImpl)service;
        boolean result = false;

        NbSessionToken sessionToken = _service.getSessionToken();
        if (sessionToken.getSessionToken() != null) {
            result = true;
        }
        NbOfflineService offlineService = _service.getOfflineService();
        if (offlineService != null && !offlineService.isOnline()) {
            if (!offlineService.loginService().isLoggedIn()) {
                //ログインキャッシュ有効期限切れ
                sessionToken.clearSessionToken();
                result = false;
            }
        }
        return result;
    }

    /**
     * 設定されたユーザ名、E-mail、パスワードを使用しユーザの登録を行う。
     * <p>
     * @deprecated  {@link #register(String, NbCallback)} で置き換え
     * @since 1.0
     */
    @Deprecated
    public void register(String password, final NbUsersCallback callback) {
        register(password, NbUsersCallbackWrapper.wrap(callback));
    }

    /**
     * 設定されたユーザ名、E-mail、パスワードを使用しユーザの登録を行う。
     *
     * <p>登録にはUSERSバケットに対する anonymousユーザのcreate権限が必要となる。
     *
     * <p>E-mail アドレスは事前に設定されていなければならない。
     * ユーザ名はオプションであるため、指定しなくても良い。
     * @param password 登録するユーザのパスワード
     * @param callback 実行結果を受け取るコールバック
     * @see com.nec.baas.user.NbUsersCallback
     * @see NbUser#setUserName(String)
     * @see NbUser#setEmail(String)
     * @since 6.5.0
     */
    public void register(@NonNull String password, @NonNull final NbCallback<NbUser> callback) {
        log.fine("register() <start>");

        if (!NbUtil.checkOnline(mService.getOfflineService(), "[NbUser]", callback)) {
            log.severe("register() <end> ERR");
            return;
        }
        if (getEmail() == null || !isValidUserName(getUserName())) {
            log.severe("register() <end> ERR param invalid");
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("Null email or bad user name."));
            return;
        }
        // リクエスト作成
        NbJSONObject bodyJson = new NbJSONObject();
        bodyJson.put(NbKey.EMAIL, getEmail());
        bodyJson.put(NbKey.PASSWORD, password);
        if (getUserName() != null) {
            bodyJson.put(NbKey.USERNAME, getUserName());
        }
        if (getOptions() != null) {
            bodyJson.put(NbKey.OPTIONS, getOptions());
        }
        Request request = mService.getHttpRequestFactory().post(USER_URL).body(bodyJson).sessionNone().build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbUser.register()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                setUserEntity(new NbUserEntity(body));
                callback.onSuccess(NbUser.this);
            }
        };
        execRegister(request, handler);
        log.fine("register() <end>");
    }

    protected void execRegister(Request request, NbRestResponseHandler handler) {
        executeRequest(mService, request, handler);
    }

    /**
     * ユーザクエリ条件。
     * username, email いずれかを指定した場合は1件検索となり、いずれも指定しない場合は全件検索となる。
     * 全件検索を行う場合は skip と limit を指定できる。
     * <p>
     * 例:
     * <pre>
     *     new Query().username("user1");     // ユーザ名指定検索
     *     new Query().skip(300).limit(100);  // 全件検索(skip/limit指定付き)
     * </pre>
     */
    @Accessors(fluent = true)
    @Getter
    @Setter
    public static class Query {
        /** ユーザ名 */
        private String username;
        /** E-mail */
        private String email;
        /** スキップカウント */
        private int skip = -1;
        /** 検索数上限。0は無制限。 */
        private int limit = -1;

        /** 検索条件チェック */
        protected void validate() {
            if (!isValidUserName(username)) {
                throw new IllegalArgumentException("username is invalid");
            }
        }

        /** クエリパラメータへの変換 */
        protected Map<String,String> toParams() {
            Map<String,String> params = new HashMap<>();
            if (username != null) {
                params.put(NbKey.USERNAME, username);
            }
            if (email != null) {
                params.put(NbKey.EMAIL, email);
            }
            if (skip >= 0) {
                params.put(NbKey.SKIP, Integer.toString(skip));
            }
            if (limit >= 0){
                params.put(NbKey.LIMIT, Integer.toString(limit));
            }
            return params;
        }
    }

    /**
     * ユーザ情報の検索を行う。(マルチテナント非対応)
     * ユーザ名、E-mailどちらも指定されない場合は全件検索を行う。
     * 実行にはUSERSバケットおよび対象ユーザに対するread権限が必要となる。
     * @param username 検索対象のユーザ名(オプション)
     * @param email 検索対象のE-mail(オプション)
     * @param callback 実行結果を受け取るコールバック
     * @deprecated {@link #query(Query, NbCallback)} で置き換え
     * @since 1.0
     */
    @Deprecated
    public static void query(String username, String email, final NbCallback<List<NbUser>> callback) {
        query(ensureNebulaService(), username, email, callback);
    }

    /**
     * ユーザ情報の検索を行う(マルチテナント対応)
     * ユーザ名、E-mailどちらも指定されない場合は全件検索を行う。
     * 実行にはUSERSバケットおよび対象ユーザに対するread権限が必要となる。
     * @param username 検索対象のユーザ名(オプション)
     * @param email 検索対象のE-mail(オプション)
     * @param callback 実行結果を受け取るコールバック
     * @deprecated {@link #query(NbService, Query, NbCallback)} で置き換え
     * @since 3.0.0
     */
    @Deprecated
    public static void query(NbService service, String username, String email, final NbCallback<List<NbUser>> callback) {
        log.fine("query() <start>"
                + " username=" + username + " email=" + email);
        _query(service, new Query().username(username).email(email), callback);
    }

    /**
     * ユーザ情報の検索を行う(マルチテナント非対応)
     * 実行にはUSERSバケットおよび対象ユーザに対するread権限が必要となる。
     * @param query クエリ条件 {@link Query}
     * @param callback コールバック
     * @since 1.0
     */
    public static void query(Query query, final NbCallback<List<NbUser>> callback) {
        query(ensureNebulaService(), query, callback);
    }

    /**
     * ユーザ情報の検索を行う(件数取得付き)(マルチテナント非対応)
     * @deprecated {@link #queryWithCount(Query, NbCountCallback)} で置き換え
     * @since 1.0
     */
    @Deprecated
    public static void query(Query query, final NbUsersCountCallback callback) {
        queryWithCount(query, callback);
    }

    /**
     * ユーザ情報の検索を行う(件数取得付き)(マルチテナント非対応)
     * 実行にはUSERSバケットおよび対象ユーザに対するread権限が必要となる。
     * @param query クエリ条件 {@link Query}
     * @param callback コールバック
     * @since 6.5.0
     */
    public static void queryWithCount(Query query, final NbCountCallback<List<NbUser>> callback) {
        queryWithCount(ensureNebulaService(), query, callback);
    }

    /**
     * ユーザ情報の検索を行う(マルチテナント対応)
     * 実行にはUSERSバケットおよび対象ユーザに対するread権限が必要となる。
     * @param service NbService
     * @param query クエリ条件 {@link Query}
     * @param callback コールバック
     * @since 3.0.0
     */
    public static void query(NbService service, Query query, final NbCallback<List<NbUser>> callback) {
        _query(service, query, callback);
    }

    /**
     * ユーザ情報の検索を行う(件数取得付き)(マルチテナント対応)
     * @deprecated {@link #queryWithCount(NbService, Query, NbCountCallback)} で置き換え
     * @since 3.0.0
     */
    @Deprecated
    public static void query(NbService service, Query query, final NbUsersCountCallback callback) {
        queryWithCount(service, query, callback);
    }

    /**
     * ユーザ情報の検索を行う(件数取得付き)(マルチテナント対応)
     * 実行にはUSERSバケットおよび対象ユーザに対するread権限が必要となる。
     * @param service NbService
     * @param query クエリ条件 {@link Query}
     * @param callback コールバック
     * @since 6.5.0
     */
    public static void queryWithCount(NbService service, Query query, final NbCountCallback<List<NbUser>> callback) {
        _query(service, query, callback);
    }

    // ユーザ検索本体
    private static void _query(@NonNull NbService service, @NonNull Query query, @NonNull final NbBaseCallback callback) {
        final NbServiceImpl _service = (NbServiceImpl)service;

        if (!NbUtil.checkOnline(_service.getOfflineService(), "[NbUser]", callback)) {
            log.severe("in offline mode");
            return;
        }

        query.validate();

        // リクエスト作成
        Request request = _service.getHttpRequestFactory()
                .get(USER_URL)
                .params(query.toParams())
                .build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbUser.query()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                NbJSONArray<NbJSONObject> bodyList = body.getJSONArray(NbKey.RESULTS);
                List<NbUser> users = makeUsersFromJsonArray(_service, bodyList);
                int count = body.optInt(NbKey.COUNT, -1);

                if (callback instanceof NbCountCallback) {
                    ((NbCountCallback<List<NbUser>>)callback).onSuccess(users, count);
                } else {
                    ((NbCallback<List<NbUser>>)callback).onSuccess(users);
                }
            }
        };
        execQuery(_service, request, handler);
    }

    protected static void execQuery(NbServiceImpl service, Request request, NbRestResponseHandler handler) {
        executeRequest(service, request, handler);
    }

    /**
     * ユーザ情報の取得を行う。(マルチテナント非対応)
     * <p>
     * @deprecated {@link #getUser(String, NbCallback)}で置き換え
     * @since 1.0
     */
    @Deprecated
    public static void getUser(String userId, final NbUsersCallback callback) {
        getUser(userId, NbUsersCallbackWrapper.wrap(callback));
    }

    /**
     * ユーザ情報の取得を行う。(マルチテナント非対応)
     * <p>
     * ユーザ情報の取得はUSERSバケットおよび対象ユーザに対するread権限が必要となる。
     * @param userId 検索対象のユーザID
     * @param callback 実行結果を受け取るコールバック
     * @since 6.5.0
     */
    public static void getUser(String userId, final NbCallback<NbUser> callback) {
        getUser(ensureNebulaService(), userId, callback);
    }

    /**
     * ユーザ情報の取得を行う(マルチテナント対応)。
     * <p>
     * @deprecated {@link #getUser(NbService, String, NbCallback)}で置き換え
     * @since 3.0.0
     */
    @Deprecated
    public static void getUser(NbService service, String userId, final NbUsersCallback callback) {
        getUser(service, userId, NbUsersCallbackWrapper.wrap(callback));
    }

    /**
     * ユーザ情報の取得を行う(マルチテナント対応)
     * <p>
     * ユーザ情報の取得はUSERSバケットおよび対象ユーザに対するread権限が必要となる。
     * @param service NbService
     * @param userId 検索対象のユーザID
     * @param callback 実行結果を受け取るコールバック
     * @since 6.5.0
     */
    public static void getUser(@NonNull NbService service, @NonNull String userId, @NonNull final NbCallback<NbUser> callback) {
        log.fine("getUser() <start> userId=" + userId);
        final NbServiceImpl _service = (NbServiceImpl)service;

        if (!NbUtil.checkOnline(_service.getOfflineService(), "[NbUser]", callback)) {
            log.severe("getUser() <end> ERR");
            return;
        }

        //リクエスト作成
        Request request = _service.getHttpRequestFactory().get(USER_URL).addPathComponent(userId).build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbUser.getUser()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                NbUser user = makeUserFromJson(_service, body);
                callback.onSuccess(user);
            }
        };
        execGetUser(_service, request, handler, userId);
        log.fine("getUser() <end>");
    }

    protected static void execGetUser(NbServiceImpl service, Request request, NbRestResponseHandler handler, String userId) {
        executeRequest(service, request, handler);
    }

    /**
     * 現在ログインしているユーザの情報を取得する。(マルチテナント非対応)
     * <p>
     * クライアント証明書認証使用時は、オフラインでは動作しない。
     * @deprecated {@link #refreshCurrentUser(NbCallback)} で置き換え
     * @since 1.0
     */
    @Deprecated
    public static void refreshCurrentUser(final NbUsersCallback callback) {
        refreshCurrentUser(NbUsersCallbackWrapper.wrap(callback));
    }

    /**
     * 現在ログインしているユーザの情報を取得する。(マルチテナント非対応)
     * クライアント証明書認証使用時は、オフラインでは動作しない。
     * @param callback 実行結果を受け取るコールバック
     * @since 1.0
     */
    public static void refreshCurrentUser(final NbCallback<NbUser> callback) {
        refreshCurrentUser(ensureNebulaService(), callback);
    }

    /**
     * 現在ログインしているユーザの情報を取得する。(マルチテナント対応)
     * <p>
     * クライアント証明書認証使用時は、オフラインでは動作しない。
     * @deprecated {@link #refreshCurrentUser(NbService, NbCallback)} で置き換え
     * @since 3.0.0
     */
    @Deprecated
    public static void refreshCurrentUser(final NbService service, final NbUsersCallback callback) {
        refreshCurrentUser(service, NbUsersCallbackWrapper.wrap(callback));
    }

    /**
     * 現在ログインしているユーザの情報を取得する。(マルチテナント対応)
     * クライアント証明書認証使用時は、オフラインでは動作しない。
     * @param service NbService
     * @param callback 実行結果を受け取るコールバック
     * @since 3.0.0
     */
    public static void refreshCurrentUser(@NonNull final NbService service, @NonNull final NbCallback<NbUser> callback) {
        log.fine("refreshCurrentUser() <start>");

        final NbServiceImpl _service = (NbServiceImpl)service;

        NbOfflineService offlineService = _service.getOfflineService();
        if (offlineService == null || offlineService.isOnline()) {
            log.fine("refreshCurrentUser() online");
            refreshCurrentUserOnline(_service, callback);
        } else {
            if (!isLoggedIn(service)) {
                log.severe("refreshCurrentUser() <end> ERR isLoggedIn=false");
                callback.onFailure(NbStatus.UNAUTHORIZED, new NbErrorInfo("Not logged in."));
                return;
            }
            log.fine("refreshCurrentUser() offline");
            //オフライン処理
            NbJSONObject json = offlineService.loginService().getLoginUserInfo();
            if (json != null) {
                NbUser user = makeUserFromJson(_service, json);
                callback.onSuccess(user);
            } else {
                //ログインキャッシュorセッショントークン有効期限切れ
                _service.getSessionToken().clearSessionToken();
                callback.onFailure(NbStatus.UNAUTHORIZED, new NbErrorInfo("No user info."));
            }
        }
        log.fine("refreshCurrentUser() <end>");
     }

    /**
     * 現在ログインしているユーザの情報を取得する。
     * @param callback 実行結果を受け取るコールバック
     */
    private static void refreshCurrentUserOnline(@NonNull final NbServiceImpl service, @NonNull final NbCallback<NbUser> callback) {
        log.fine("refreshCurrentUserOnline() <start>");

        //リクエスト作成
        Request request;
        // クライアント証明書認証時はセッショントークンが存在しないケースがあるため、optionalとする
        // サーバ問い合わせ時にセッショントークンが付与されていない場合は、401エラーとなる
        request = service.getHttpRequestFactory().get(USER_URL).addPathComponent(CURRENT_PATH_COMPONENT).sessionOptional().build();
//        try {
//            request = service.getHttpRequestFactory().get(USER_URL).addPath(CURRENT_URL).sessionRequired().build();
//        } catch (SecurityException e) {
//            log.severe("refreshCurrentUserOnline ERR");
//            callback.onFailure(NbStatus.UNAUTHORIZED, new NbErrorInfo("Failed to make GET request."));
//            return;
//        }

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbUser.refreshCurrentUser()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                NbUser user = makeUserFromJson(service, body);

                NbOfflineService offlineService = service.getOfflineService();
                if (offlineService != null) {
                    offlineService.loginService().updateLoginCache(null, user.mUserEntity);
                }

                callback.onSuccess(user);
            }
        };
        execRefreshCurrentUser(service, request, handler);

        log.fine("refreshCurrentUserOnline() <end>");
    }

    protected static void execRefreshCurrentUser(NbServiceImpl service, Request request, NbRestResponseHandler handler) {
        executeRequest(service, request, handler);
    }

    /**
     * パスワードのリセット要求を行う(マルチテナント非対応)
     * 実行にはユーザ名もしくはE-mailどちらかを必ず指定する必要がある。
     * 10分に1回のみ使用可能。
     * @param username リセット対象のユーザ名
     * @param email リセット対象のE-mail
     * @param callback 実行結果を受け取るコールバック
     * @since 1.0
     */
    public static void resetPassword(String username, String email, final NbResultCallback callback) {
        resetPassword(ensureNebulaService(), username, email, callback);
    }

    /**
     * パスワードのリセット要求を行う（マルチテナント対応)
     * 実行にはユーザ名もしくはE-mailどちらかを必ず指定する必要がある。
     * 10分に1回のみ使用可能。
     * @param username リセット対象のユーザ名
     * @param email リセット対象のE-mail
     * @param callback 実行結果を受け取るコールバック
     * @since 3.0.0
     */
    public static void resetPassword(@NonNull final NbService service, String username, String email, @NonNull final NbResultCallback callback) {
        log.fine("resetPassword() <start>"
                + " username=" + username + " email=" + email);

        NbServiceImpl _service = (NbServiceImpl)service;

        //ユーザ名とE-mailどちらも指定されない場合
        if (username == null && email == null) {
            throw new IllegalArgumentException("both username and email are null");
        }
        //ユーザ名が使用できない場合
        if (!isValidUserName(username)) {
            throw new IllegalArgumentException("username is invalid");
        }

        NbOfflineService offlineService = _service.getOfflineService();
        if (!NbUtil.checkOnline(offlineService, "[NbUser]", callback)) {
            log.severe("resetPassword() <end> ERR");
            return;
        }

        //リクエスト作成
        NbJSONObject bodyJson = new NbJSONObject();
        if (username != null) {
            bodyJson.put(NbKey.USERNAME, username);
        }
        if (email != null) {
            bodyJson.put(NbKey.EMAIL, email);
        }
        Request request = _service.getHttpRequestFactory().post(PASSWORD_RESET_URL).body(bodyJson).sessionNone().build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbUser.resetPassword()") {
            @Override
            public void onSuccess(Response response) {
                callback.onSuccess();
            }
        };
        execResetPassword(_service, request, handler);

        log.fine("resetPassword() <end>");
    }

    protected static void execResetPassword(NbServiceImpl service, Request request, NbRestResponseHandler handler) {
        executeRequest(service, request, handler);
    }

    /**
     * ユーザ情報の保存を行う。
     * <p>
     * @deprecated {@link #save(String, NbCallback)} で置き換え。
     * @since 1.0
     */
    @Deprecated
    public void save(final String password, final NbUsersCallback callback) {
        save(password, NbUsersCallbackWrapper.wrap(callback));
    }

    /**
     * ユーザ情報の保存を行う。
     * <p>
     * 本メソッドはログイン後のみ実行できる。
     * <p>
     * 情報の変更は自ユーザのみ可能。
     * 但し、マスターキーを使用した場合のみどのユーザの情報も変更可能である。
     * @param password 保存するユーザのパスワード
     * @param callback 保存したユーザを受け取るコールバック
     * @since 6.5.0
     */
    public void save(final String password, @NonNull final NbCallback<NbUser> callback) {
        log.fine("save() <start>");

        if (!NbUtil.checkOnline(mService.getOfflineService(), "[NbUser]", callback)) {
            log.severe("save() <end> ERR");
            return;
        }
        if (getUserId() == null) {
            log.severe("save() <end> ERR param invalid");
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("userId is null"));
            return;
        }
        if (!isValidUserName(getUserName())) {
            log.severe("save() <end> ERR param invalid");
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("Bad username."));
            return;
        }
        //リクエスト作成
        NbJSONObject bodyJson = new NbJSONObject();
        if (getUserName() != null) {
            bodyJson.put(NbKey.USERNAME, getUserName());
        }
        if (getEmail() != null) {
            bodyJson.put(NbKey.EMAIL, getEmail());
        }
        if (getOptions() != null) {
            bodyJson.put(NbKey.OPTIONS, getOptions());
        }
        if (password != null) {
            bodyJson.put(NbKey.PASSWORD, password);
        }

        Request request = mService.getHttpRequestFactory()
                .put(USER_URL).addPathComponent(getUserId())
                .body(bodyJson)
                .build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbUser.save()") {
            @Override
            public void onSuccess(Response response, NbJSONObject body) {
                NbUserEntity entity = new NbUserEntity(body);
                setUserEntity(entity);
                //ログイン中のユーザを更新した場合はセッショントークン、ログインキャッシュを更新する
                NbSessionToken sessionToken = mService.getSessionToken();
                NbUserEntity userEntity = sessionToken.getSessionUserEntity();
                if (userEntity != null && userEntity.getId() != null &&
                        userEntity.getId().equals(getUserId())) {
                    sessionToken.setSessionUserEntity(entity);
                    NbOfflineService offlineService = mService.getOfflineService();
                    if (offlineService != null) {
                        offlineService.loginService().updateLoginCache(password, mUserEntity);
                    }
                }

                callback.onSuccess(NbUser.this);
            }
        };
        execSave(request, handler, bodyJson);
        log.fine("save() <end>");
    }

    protected void execSave(Request request, NbRestResponseHandler handler, NbJSONObject bodyJson) {
        executeRequest(mService, request, handler);
    }

    /**
     * ユーザの削除を行う。
     * <p>
     * 本メソッドはログイン後のみ実行できる。
     * <p>
     * ユーザの削除は自ユーザのみ可能。
     * 但し、マスターキーを使用した場合どのユーザの情報も削除可能となる。
     * @param callback APIの実行結果を取得するコールバック
     * @since 1.0
     */
    public void delete(@NonNull final NbResultCallback callback) {
        log.fine("delete() <start>");

        if (!NbUtil.checkOnline(mService.getOfflineService(), "[NbUser]", callback)) {
            log.severe("delete() <end> ERR");
            return;
        }
        if (getUserId() == null) {
            log.severe("delete() <end> ERR param invalid");
            callback.onFailure(NbStatus.REQUEST_PARAMETER_ERROR, new NbErrorInfo("userId is null"));
            return;
        }

        //リクエスト作成
        Request request = mService.getHttpRequestFactory().delete(USER_URL).addPathComponent(getUserId()).build();

        NbRestResponseHandler handler = new NbSimpleRestResponseHandler(callback, "NbUser.delete()") {
            @Override
            public void onSuccess(Response response) {
                //ログイン中のユーザを削除した場合はセッショントークン、ログインキャッシュを破棄する
                NbSessionToken sessionToken = mService.getSessionToken();
                NbUserEntity userEntity = sessionToken.getSessionUserEntity();
                if (userEntity != null && userEntity.getId() != null &&
                        userEntity.getId().equals(getUserId())) {
                    sessionToken.clearSessionToken();
                    NbOfflineService offlineService = mService.getOfflineService();
                    if (offlineService != null) {
                        offlineService.loginService().logout();
                    }
                }
                callback.onSuccess();
            }
        };
        execDelete(request, handler);

        log.fine("delete() <end>");
    }

    protected void execDelete(Request request, NbRestResponseHandler handler) {
        executeRequest(mService, request, handler);
    }

    private synchronized static NbServiceImpl ensureNebulaService() {
        if (sNebulaService == null) {
            sNebulaService = (NbServiceImpl) NbService.getInstance();
        }
        return sNebulaService;
    }

    public synchronized static void __resetNebulaService() {
        sNebulaService = null;
    }

    /**
     * ユーザ名を設定する。
     *
     * <p>
     * ユーザ名には以下の条件がある。ただし、規定外のユーザ名を設定しても
     * ここではエラーにはならない。register や save のタイミングでエラーとなる。
     * <ul>
     *     <li>ユーザ名に使用できる文字は半角英数と"_"のみ</li>
     *     <li>ユーザ名の文字数上限は32文字</li>
     * </ul>
     * @param userName 設定後のユーザ名。
     * @see #save(String, NbCallback)
     * @see #register(String, NbCallback)
     * @since 1.0
     */
    public NbUser setUserName(String userName) {
        mUserEntity.setUsername(userName);
        log.finest("setUserName() name=" + userName);
        return this;
    }

    /**
     * ユーザ名を取得する。
     * @return 設定されたユーザ名
     * @since 1.0
     */
    public String getUserName() {
        return mUserEntity.getUsername();
    }

    /**
     * E-mailアドレスを設定する。
     * @param email 設定後のE-mail。
     * @see #save(String, NbCallback)
     * @since 1.0
     */
    public NbUser setEmail(String email) {
        mUserEntity.setEmail(email);
        log.finest("setEmail() email=" + email);
        return this;
    }

    /**
     * E-mailを取得する。
     * @return 設定されたE-mail。
     * @since 1.0
     */
    public String getEmail() {
        return mUserEntity.getEmail();
    }

    /**
     * オプション情報を設定する。
     * @param options 設定後のオプション情報。
     * @see #save(String, NbCallback)
     * @since 3.0.0
     */
    public NbUser setOptions(NbJSONObject options) {
        mUserEntity.setOptions(options);
        log.finest("setOptions() options=" + options);
        return this;
    }

    /**
     * オプション情報を取得する。
     * @return 設定されたオプション情報。
     * @since 3.0.0
     */
    public NbJSONObject getOptions() {
        return mUserEntity.getOptions();
    }

    /**
     * ユーザが所属するグループ一覧を取得する
     * @return グループ名一覧
     * @since 4.0.0
     */
    public List<String> getGroups() {
        return mUserEntity.getGroups();
    }

    /* 内部メソッド: グループ一覧セット */
    protected NbUser setGroups(@NonNull List<String> groups) {
        mUserEntity.setGroups(groups);
        log.finest("setGroups() groups=" + groups);
        return this;
    }

    /**
     * ユーザIDを設定する。
     * ユーザIDを設定する場合、マスターキーを使用する必要がある。
     * @param userId 設定後のユーザID
     * @see #save(String, NbCallback)
     * @since 1.0
     */
    protected NbUser setUserId(String userId) {
        mUserEntity.setId(userId);
        log.finest("setEmail() userId=" + userId);
        return this;
    }

    /**
     * ユーザIDを取得する。
     * @return 設定されたユーザID。
     * @since 1.0
     */
    public String getUserId() {
        return mUserEntity.getId();
    }

    /**
     * ユーザ情報を設定する。
     * ユーザIDを設定する場合、マスターキーを使用する必要がある。
     * @param entity 設定するユーザ情報。null を指定した場合は、初期状態に戻す。
     * @see #save(String, NbCallback)
     * @since 1.0
     */
    protected NbUser setUserEntity(NbUserEntity entity) {
        if (entity == null) {
            //throw new IllegalArgumentException("NbUser.setUserEntity: entity is null");
            mUserEntity = new NbUserEntity();
        } else {
            mUserEntity.set(entity);
        }
        return this;
    }

    protected NbUser setCreatedTime(String time) {
        mUserEntity.setCreatedTime(time);
        return this;
    }

    protected NbUser setUpdatedTime(String time) {
        mUserEntity.setUpdatedTime(time);
        return this;
    }

    /**
     * ユーザ情報作成時間を取得する。
     * @return ユーザ情報作成時間。
     * @since 1.0
     */
    public String getCreatedTime() {
        return mUserEntity.getCreatedTime();
    }

    /**
     * ユーザ情報更新時間を取得する。
     * @return ユーザ情報更新時間。
     * @since 1.0
     */
    public String getUpdatedTime() {
        return mUserEntity.getUpdatedTime();
    }

    /**
     * ログインの有効期限を取得する。(マルチテナント非対応)
     * @return ログイン有効期限。
     * @since 1.0
     */
    public static long getSessionTokenExpiration() {
        return getSessionTokenExpiration(ensureNebulaService());
    }

    /**
     * ログインの有効期限を取得する。(マルチテナント対応)
     * @return ログイン有効期限。
     * @since 3.0.0
     */
    public static long getSessionTokenExpiration(NbService service) {
        return ((NbServiceImpl)service).getSessionToken().getExpireAt();
    }

    /**
     * セッショントークンを取得する。(マルチテナント非対応)
     * @return セッショントークン
     * @since 1.0
     */
    public static String getSessionToken() {
        return getSessionToken(ensureNebulaService());
    }

    /**
     * セッショントークンを取得する。(マルチテナント対応)
     * @return セッショントークン
     * @since 3.0.0
     */
    public static String getSessionToken(@NonNull NbService service) {
        return ((NbServiceImpl)service).getSessionToken().getSessionToken();
    }

    /**
     * 現在ログインしているユーザの情報を取得する(キャッシュから読み出す)。(マルチテナント非対応)
     *
     * クライアント証明書認証使用時は使用しないこと。
     * @return ログイン中のNbUserインスタンス、未ログイン時はユーザ情報が空のNbUserインスタンス
     * @since 1.0
     */
    public static NbUser getCurrentUser() {
        return getCurrentUser(ensureNebulaService());
    }

    /**
     * 現在ログインしているユーザの情報を取得する(キャッシュから読み出す)。(マルチテナント対応)
     *
     * クライアント証明書認証使用時は使用しないこと。
     * @return ログイン中のNbUserインスタンス、未ログイン時はユーザ情報が空のNbUserインスタンス
     * @since 3.0.0
     */
    public static NbUser getCurrentUser(@NonNull NbService service) {
        NbUserEntity entity = ((NbServiceImpl)service).getSessionToken().getSessionUserEntity();
        NbUser user = new NbUser(service);
        user.setUserEntity(entity);
        return user;
    }

    /**
     * NbUser を ユーザの JSON 表現に変換する。
     * @return ユーザの JSON 表現
     */
    public NbJSONObject toJsonObject() {
        return mUserEntity.toJsonObject();
    }

    /**
     * JSON配列からユーザ一覧を作成する。
     * @param service NbService
     * @param infoList レスポンスからkey:resultsで取得できる情報のリスト
     * @return NbUserのList
     */
    private static List<NbUser> makeUsersFromJsonArray(NbServiceImpl service, NbJSONArray<NbJSONObject> infoList) {
        List<NbUser> resultList = new ArrayList<>(infoList.size());

        for (NbJSONObject json : infoList) {
            NbUser user = makeUserFromJson(service, json);
            resultList.add(user);
        }
        return resultList;
    }

    /**
     * JSONからユーザを作成する
     * @param service service
     * @param json JSON
     * @return NbUser
     */
    private static NbUser makeUserFromJson(NbServiceImpl service, NbJSONObject json) {
        NbUserEntity entity = new NbUserEntity(json);
        NbUser user = new NbUser(service);
        user.setUserEntity(entity);
        return user;
    }

    /**
     * ユーザ名が使用できるかチェックする。<br>
     * チェック項目は文字数チェック、使用不可文字チェックを行う。
     * @param userName ユーザ名
     * @return 使用可能 or nullであればtrue、使用不可であればfalse
     */
    private static boolean isValidUserName(String userName) {
        //ユーザ名はオプションのためnullの場合はチェックを行わない
        if (userName == null) {
            return true;
        }

        //文字数チェック
        if (userName.isEmpty()) {
            return false;
        }

        return true;
    }

    /**
     * リダイレクト URI からワンタイムトークンを取り出す
     * @param uriString URI文字列
     * @return LoginParam ワンタイムトークンを格納した LoginParam
     * @throws IllegalArgumentException URI文字列にワンタイムトークンが含まれていない
     * @throws URISyntaxException URI不正
     * @since 6.5.0
     */
    public static LoginParam extractOneTimeTokenFromUri(String uriString) throws URISyntaxException {
        URI uri = new URI(uriString);

        String query = uri.getQuery();
        if (query != null) {
            for (String pair : uri.getQuery().split("&")) {
                String[] p = pair.split("=");
                if (p.length == 2 && p[0].equals("token")) {
                    String token = p[1];
                    try {
                        token = URLDecoder.decode(token, "UTF-8");
                    } catch (UnsupportedEncodingException never) {
                        // ignore
                    }
                    return new LoginParam().token(token);
                }
            }
        }
        throw new IllegalArgumentException("No one time token");
    }
}
