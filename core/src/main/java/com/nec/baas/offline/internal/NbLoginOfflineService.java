/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.core.internal.*;
import com.nec.baas.json.*;
import com.nec.baas.user.internal.*;

import java.util.List;

/**
 * オフラインログイン用インタフェース
 */
public interface NbLoginOfflineService {
    /**
     * ログイン状態を確認する。<br>
     * saveLoginCache(), login(), userIdActivate()成功時はログイン完了となる。<br>
     * @return ログイン状態 true:ログイン完了 false:未ログイン
     */
    boolean isLoggedIn();

    /**
     * ログイン中のユーザ情報を取得する。
     *
     * @return ユーザ情報
     */
    NbJSONObject getLoginUserInfo();


    List<String> getGroupList();

    /**
     * ログイン処理を行う。
     * @param username ログインするユーザのユーザ名 (username/emailどちらか一方を指定)
     * @param email ログインするユーザのE-mail (username/emailどちらか一方を指定)
     * @param password ログインするユーザのパスワード
     * @return ステータスコード
     */
    int login(String username, String email, String password);

    /**
     * ログアウトする。<br>
     * オフラインサービスのログイン状態を未ログインにする。
     */
    void logout();

    /**
     * ログインキャッシュを保存する。<br>
     * オンラインのログイン成功時にログインキャッシュを保存する。
     * @param pass パスワード
     * @param token セッショントークン
     * @param expire セッショントークン有効期限
     * @param user ユーザ情報
     * @param groupList 所属グループリスト
     * @return true:保存成功 false:保存失敗
     */
    boolean setLoginCache(String pass, String token, Long expire, NbUserEntity user,
                                 List<String> groupList);

    /**
     * ログインキャッシュを更新する。<br>
     * オンラインのユーザ情報の更新、取得成功時にログインキャッシュを更新する。
     * パスワードが変更された場合はパスワードハッシュを再生成し保存する。
     *
     * @param pass パスワード（オプション）
     * @param user ユーザ情報
     * @return true:保存成功 false:保存失敗
     */
    boolean updateLoginCache(String pass, NbUserEntity user);

    /**
     * ログインキャッシュを取得する。
     * @param username ログインするユーザのユーザ名 (username/emailどちらか一方を指定)
     * @param email ログインするユーザのE-mail (username/emailどちらか一方を指定)
     */
    NbOfflineResult getLoginCache(String username, String email);

    /**
     * 有効期限切れログインキャッシュを削除する。
     */
    void cleanLoginCache();

    /**
     * checkExpireがtrueの場合、有効期限切れログインキャッシュを削除する。
     * checkExpireがfalseの場合、有効期限切れかどうかに関わらずログインキャッシュを削除する。
     * @param checkExpire true: 有効期限切れのみ削除する, false: 全て削除する
     */
    void cleanLoginCache(boolean checkExpire);

    /**
     * 指定したユーザのログインキャッシュを削除する。
     * @param username 削除するユーザのユーザ名 (username/emailどちらか一方を指定)
     * @param email 削除するユーザのE-mail (username/emailどちらか一方を指定)
     * @return true:削除成功 false:削除失敗
     */
    boolean deleteLoginCache(String username, String email);

    /**
     * ログインキャッシュを有効化する<br>
     * サービス生成時にセッショントークンが保持されていた場合、ログイン完了となる。
     * @param userId ユーザID
     * @return ステータスコード
     */
    int userIdActivate(String userId);

    /**
     * ログインキャッシュの有効期間を設定する。
     * @param time ログインキャッシュの有効期間(秒)
     */
    void setLoginCacheValidTime(long time);

    /**
     * ログインキャッシュの有効期間を取得する。
     * @return ログインキャッシュの有効期間(秒)
     */
    long getLoginCacheValidTime();

}
