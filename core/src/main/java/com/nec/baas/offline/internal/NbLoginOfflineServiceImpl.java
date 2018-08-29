/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.json.*;
import com.nec.baas.user.internal.*;
import com.nec.baas.util.*;

import java.util.List;

/**
 * ログインオフラインサービス
 */
public class NbLoginOfflineServiceImpl implements NbLoginOfflineService {
    private NbLogger log = NbLogger.getLogger(NbLoginOfflineServiceImpl.class);
    private CurrentUserData mCurrentUserData;
    /*package*/ NbDatabaseManager mDatabaseManager;
    private long mLoginCacheValidTime = NbOfflineUtil.LOGINCACHE_VALID_TIME;

    public NbLoginOfflineServiceImpl(NbDatabaseManager databaseManager) {
        mDatabaseManager = databaseManager;
        mCurrentUserData = null;
    }

    @Override
    public boolean isLoggedIn() {
        expireCheck();
        if (null == mCurrentUserData) {
            log.finest("isLoggedIn()=false");
            return false;
        }
        return true;
    }

    @Override
    public NbJSONObject getLoginUserInfo() {
        expireCheck();
        if (null == mCurrentUserData) {
            return null;
        }
        return mCurrentUserData.mUserInfo;
    }

    @Override
    public List<String> getGroupList() {
        if (null == mCurrentUserData) {
            return null;
        }
        return mCurrentUserData.mGroupList;
    }

    private void expireCheck() {
        if (null != mCurrentUserData) {
            long currentTime = System.currentTimeMillis() / 1000;
            if ( mCurrentUserData.mLoginCacheExpire <= currentTime ) {
                log.info("expireCheck() currentTime=" + currentTime);
                log.info("expireCheck() mLoginCacheExpire="
                        + mCurrentUserData.mLoginCacheExpire);

                try {
                    mDatabaseManager.deleteLoginCache(mCurrentUserData.mUserInfo.getString(NbKey.ID));
                } catch (NbDatabaseException e) {
                    log.severe("expireCheck() deleteLoginCache Err NbDatabaseException e=" + e);
                }
                mCurrentUserData = null;
            }
        }
    }

    @Override
    public void logout() {
        if (null != mCurrentUserData) {
            mCurrentUserData = null;
        }
    }

    @Override
    public int login(String username, String email, String password) {
        log.fine("login() <start>");

        if ((username == null && email == null) || password == null) {
            return NbStatus.REQUEST_PARAMETER_ERROR;
        }

        NbLoginCacheEntity cache = null;
        try {
            if (username != null) {
                cache = mDatabaseManager.readLoginCache(NbDatabaseManager.KEY_USERNAME, username);
            } else {
                cache = mDatabaseManager.readLoginCache(NbDatabaseManager.KEY_EMAIL, email);
            }
        } catch (NbDatabaseException e) {
            log.fine("login() readLoginCache Err"
                    + " NbDatabaseException e=" + e);
        }

        if (null == cache) {
            log.severe("login() <end> ERR null == cache");
            //ログインキャッシュなし
            return NbStatus.UNAUTHORIZED;
        }

        long currentTime = System.currentTimeMillis() / 1000;
        long loginCacheExpire = cache.getLoginCacheExpire();
        if (loginCacheExpire <= currentTime) {
            log.severe("login() <end>"
                    + " ERR expire currentTime=" + currentTime);
            //有効期限切れ & キャッシュ削除
            try {
                mDatabaseManager.deleteLoginCache(cache.getUserId());
            } catch (NbDatabaseException e) {
                log.fine("login() deleteLoginCache Err"
                        + " NbDatabaseException e=" + e);
            }
            return NbStatus.UNAUTHORIZED;
        }

        if (!NbOfflineUtil.checkPasswordHash(cache.getUserId(), password, cache.getPasswordHash())) {
            log.severe("login() <end> ERR checkPassword");
            //ハッシュチェックエラー
            return NbStatus.UNAUTHORIZED;
        }

        //ログインOK ユーザ情報格納
        mCurrentUserData = new CurrentUserData(makeLoginCacheData(cache), cache.getGroupList(),
                loginCacheExpire);
        log.fine("login() <end>");
        return NbStatus.OK;
    }

    @Override
    public boolean setLoginCache(String pass, String token, Long expire, NbUserEntity user,
                                 List<String> groupList) {
//      log.info("setLoginCache() pass=" + pass);
        log.info("setLoginCache() token=" + token);
        log.info("setLoginCache() expire=" + expire);
        log.info("setLoginCache() user=" + user);
//      log.info("setLoginCache() groupList=" + groupList);

        if (null == pass || null == token || null == expire || null == user || null == groupList) return false;
        if (null == user.getId() || null == user.getUsername()) return false;
        String hash = NbOfflineUtil.createPasswordHash(user.getId(), pass);
        long currentTime = System.currentTimeMillis() / 1000;
        NbLoginCacheEntity readData = null;
        try {
            readData =  mDatabaseManager.readLoginCache(NbDatabaseManager.KEY_USERNAME, user.getUsername());
        } catch (NbDatabaseException e) {
            log.fine("setLoginCache() readLoginCache Err"
                    + " NbDatabaseException e=" + e);
        }

        long loginCacheExpire = currentTime + getLoginCacheValidTime();
        if (loginCacheExpire < 0L) {
            // オーバーフロー対策。
            // Long.MAX_VALUE のままでもよいが、別の箇所でオーバーフローするのリスクを
            // 防止するため、1/10 しておく。
            loginCacheExpire = Long.MAX_VALUE / 10L;
        }

        NbLoginCacheEntity saveData = new NbLoginCacheEntity();
        saveData.setUserId(user.getId());
        saveData.setUserName(user.getUsername());
        saveData.setEmailAddress(user.getEmail());
        saveData.setOptions(user.getOptions());
        saveData.setPasswordHash(hash);
        saveData.setGroupList(groupList);
        saveData.setCreatedTime(user.getCreatedTime());
        saveData.setUpdatedTime(user.getUpdatedTime());
        saveData.setLoginCacheExpire(loginCacheExpire);
        saveData.setSessionToken(token);
        saveData.setSessionTokenExpire(expire);

        long createCount = 0;
        try {
            if (null == readData) {
                createCount = mDatabaseManager.createLoginCache(saveData);
            } else {
                createCount = mDatabaseManager.updateLoginCacheWithUsername(user.getUsername(), saveData);
            }
        } catch (NbDatabaseException e) {
            log.fine("setLoginCache()"
                    + " NbDatabaseException e=" + e);
        }

        boolean result = true;
        if (createCount == 0 || createCount == NbDatabaseManager.INSERT_ERROR_CODE) {
            result = false;
        } else {
            //ログインOK ユーザ情報格納
            mCurrentUserData = new CurrentUserData(makeLoginCacheData(saveData),
                    saveData.getGroupList(), saveData.getLoginCacheExpire());
        }

        log.fine("setLoginCache() result=" + result);
        return result;
    }

    @Override
    public boolean updateLoginCache(String pass, NbUserEntity user) {
        log.fine("updateLoginCache() <start>");
//        log.info("updateLoginCache() pass=" + pass);
        log.info("updateLoginCache() user=" + user);

        boolean result = false;
        if (null == user) return result;
        if (null == user.getId()) return result;

        NbLoginCacheEntity updateData = null;
        try {
            updateData = mDatabaseManager.readLoginCache(NbDatabaseManager.KEY_USERID, user.getId());
        } catch (NbDatabaseException e) {
            log.fine("updateLoginCache() readLoginCache Err"
                    + " NbDatabaseException e=" + e);
        }
        if (null == updateData) {
            return result;
        }

        if (null != pass) {
            String hash = NbOfflineUtil.createPasswordHash(user.getId(), pass);
            updateData.setPasswordHash(hash);
        }
        updateData.setUserId(user.getId());
        updateData.setUserName(user.getUsername());
        updateData.setEmailAddress(user.getEmail());
        updateData.setOptions(user.getOptions());
        updateData.setCreatedTime(user.getCreatedTime());
        updateData.setUpdatedTime(user.getUpdatedTime());
        long createCount = 0;
        try {
            createCount = mDatabaseManager.updateLoginCache(user.getId(), updateData);
        } catch (NbDatabaseException e) {
            log.fine("updateLoginCache() updateLoginCache Err"
                    + " NbDatabaseException e=" + e);
        }
        if (createCount == 0 || createCount == NbDatabaseManager.INSERT_ERROR_CODE) {
            result = false;
        } else {
            //ユーザ情報更新
            result = true;
            mCurrentUserData = new CurrentUserData(makeLoginCacheData(updateData),
                    updateData.getGroupList(), updateData.getLoginCacheExpire());
        }

        log.fine("updateLoginCache() <end>"
                + " result=" + result);
        return result;
    }

    private NbJSONObject makeLoginCacheData(NbLoginCacheEntity data) {
        NbJSONObject json = new NbJSONObject();
        if (null == data) {
            return null;
        }
        json.put(NbKey.ID, data.getUserId());
        json.put(NbKey.USERNAME, data.getUserName());
        json.put(NbKey.EMAIL, data.getEmailAddress());
        json.put(NbKey.OPTIONS, data.getOptions());
        json.put(NbKey.CREATED_AT, data.getCreatedTime());
        json.put(NbKey.UPDATED_AT, data.getUpdatedTime());
        json.put(NbKey.SESSION_TOKEN, data.getSessionToken());
        json.put(NbKey.EXPIRE, data.getSessionTokenExpire());

        return json;
    }

    @Override
    public NbOfflineResult getLoginCache(String username, String email) {
        log.fine("getLoginCache() <start>");
        log.info("getLoginCache() user=" + username + " email=" + email);

        NbOfflineResult container = new NbOfflineResult();
        if (username == null && email == null) {
            container.setStatusCode(NbStatus.REQUEST_PARAMETER_ERROR);
            return container;
        }
        NbLoginCacheEntity cache = null;
        try {
            if (username != null) {
                cache = mDatabaseManager.readLoginCache(NbDatabaseManager.KEY_USERNAME, username);
            } else {
                cache = mDatabaseManager.readLoginCache(NbDatabaseManager.KEY_EMAIL, email);
            }
        } catch (NbDatabaseException e) {
            log.fine("getLoginCache() readLoginCache Err"
                    + " NbDatabaseException e=" + e);
        }
        if (null == cache) {
            container.setStatusCode(NbStatus.NOT_FOUND);
        } else {
            container.setJsonData(makeLoginCacheData(cache));
            container.setStatusCode(NbStatus.OK);
        }
        log.fine("getLoginCache() <end>"
                + " container=" + container);
        return container;
    }

    @Override
    public void cleanLoginCache() {
        cleanLoginCache(true);
    }

    @Override
    public void cleanLoginCache(boolean checkExpire) {
        List<NbLoginCacheEntity> cacheList = null;
        try {
            cacheList = mDatabaseManager.readAllLoginCache();
        } catch (NbDatabaseException e) {
            log.fine("cleanLoginCache()"
                    + " NbDatabaseException e=" + e);
        }
        if (cacheList == null) {
            log.severe("cleanLoginCache() Err cacheList == null");
            return;
        }
        long currentTime = System.currentTimeMillis() / 1000;
        for (NbLoginCacheEntity cache : cacheList) {
            if (checkExpire) {
                if (cache.getLoginCacheExpire() <= currentTime) {
                    //有効期限切れ & キャッシュ削除
                    try {
                        mDatabaseManager.deleteLoginCache(cache.getUserId());
                    } catch (NbDatabaseException e) {
                        log.fine("cleanLoginCache() deleteLoginCache Err"
                                + " NbDatabaseException e=" + e);
                    }
                }
            } else {
                // 期限切れかどうかに関わらずキャッシュ削除(主にテスト用)
                try {
                    mDatabaseManager.deleteLoginCache(cache.getUserId());
                } catch (NbDatabaseException e) {
                    log.fine("cleanLoginCache() deleteLoginCache Err"
                            + " NbDatabaseException e=" + e);
                }
            }
        }
    }

    @Override
    public boolean deleteLoginCache(String username, String email) {
        if (null == username && null == email) return false;

        int deleteCount = 0;
        try {
            deleteCount = mDatabaseManager.deleteLoginCache(username, email);
        } catch (NbDatabaseException e) {
            log.fine("deleteLoginCache() deleteLoginCache Err"
                    + " NbDatabaseException e=" + e);
        }
        return deleteCount != 0;
    }

    @Override
    public int userIdActivate(String userId) {
        NbLoginCacheEntity cache = null;
        try {
            cache = mDatabaseManager.readLoginCache(NbDatabaseManager.KEY_USERID, userId);
        } catch (NbDatabaseException e) {
            log.fine("userIdActivate() readLoginCache Err"
                    + " NbDatabaseException e=" + e);
        }
        if (null == cache) {
            //ログインキャッシュなし
            return NbStatus.NOT_FOUND;
        }

        long currentTime = System.currentTimeMillis() / 1000;
        long loginCacheExpire = cache.getLoginCacheExpire();
        if (loginCacheExpire <= currentTime) {
            log.severe("login() <end>"
                    + " ERR expire currentTime=" + currentTime);
            //有効期限切れ & キャッシュ削除
            try {
                mDatabaseManager.deleteLoginCache(cache.getUserId());
            } catch (NbDatabaseException e) {
                log.fine("userIdActivate() deleteLoginCache Err"
                        + " NbDatabaseException e=" + e);
            }
            return NbStatus.NOT_FOUND;
        }

        //ログインOK ユーザ情報格納
        mCurrentUserData = new CurrentUserData(makeLoginCacheData(cache), cache.getGroupList(),
                loginCacheExpire);
        return NbStatus.OK;

    }

    @Override
    public void setLoginCacheValidTime(long time) {
        if (0 < time) {
            mLoginCacheValidTime = time;
        }
    }

    @Override
    public long getLoginCacheValidTime() {
        return mLoginCacheValidTime;
    }

    /**
     * ログイン中のカレントユーザを格納するクラス
     */
    private static class CurrentUserData {
        /** ユーザ情報 */
        private NbJSONObject mUserInfo;
        /** 所属するグループのリスト */
        private List<String> mGroupList;
        /** ログインキャッシュ有効期限 */
        private long mLoginCacheExpire;

        public CurrentUserData(NbJSONObject currentUserInfo, List<String> groupList, long loginCacheExpire) {
            this.mUserInfo = currentUserInfo;
            this.mGroupList = groupList;
            this.mLoginCacheExpire = loginCacheExpire;
        }
    }
}
