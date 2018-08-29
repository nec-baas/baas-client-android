/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.core.*;
import com.nec.baas.json.*;
import com.nec.baas.util.*;
import lombok.NonNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;

/**
 * オフライン機能用のユーティリティクラス.
 * @since 1.0
 */
public final class NbOfflineUtil {
    private static final NbLogger log = NbLogger.getLogger(NbOfflineUtil.class);

    private NbOfflineUtil() { }

    static final String MACHINE_ID_FORMAT = "%6.6s";

    private static int mCount = new Random().nextInt(0xffffff);
    private static final Object COUNT_LOCK = new Object();
    private static String mMachineId = "";
    private static int mPid = 0;

    static final int HASH_STRETCH_COUNT = 10000;
    static final String HASH_ALGORITHM = "SHA-256";
    static final long LOGINCACHE_VALID_TIME = 259200;

    static final int COUNT_MAX = 0xffffff;
    //キャッシュファイルディレクトリ
    static final String CACHE_FILE_FOLDER_NAME = "nebula_cache";
    //キャッシュファイルディレクトリ
    static final String TEMPORARY_FILE_FOLDER_NAME = "nebula_temp";

    //内部処理結果
    //static final int LOCAL_RESULT_OK = 0;
    //static final int LOCAL_RESULT_ERROR = 1;

    //DataSecurity
    public static final String IMPORTANCE_NONE = "none";
    public static final String TRAILER_BUCKET_NAME = "trailer_log";
    public static final String TRAIL_TRACKER_KEY = "tracker";
    public static final String TRAIL_EVENTS_KEY = "events";
    public static final String TRAIL_BUCKET_KEY = "bucket";
    public static final String TRAIL_OBJECTS_KEY = "object_ids";
    public static final String TRAIL_CUSTOM_KEY = "custom";
    public static final String TRAIL_TRACKER_NAME = "object_storage";
    public static final String TRAIL_EVENT_STORE_NAME = "store_cache";
    public static final String TRAIL_EVENT_CLEAR_NAME = "clear_cache";

    //再送間隔ベース時間(msec)
    static final long RETRY_BASE_TIME_MSEC = (1 * 60 * 1000); // (1分) minutes * second * msecond
    //リトライ回数
    static final long RETRY_COUNT = 4; //1分→2分→4分→8分の4回

    static String makeObjectId() {
        long time = System.currentTimeMillis();
        //単位を秒に変換 タイムスタンプ取得
        time = time / 1000;
        String timestamp = String.format("%08x",time);
        timestamp = String.format("%.8s", timestamp);
        //マシンID整形
        String formatMachineId = String.format(MACHINE_ID_FORMAT, mMachineId);
        //プロセスID整形
        String pid = String.format("%04x", mPid);
        pid = String.format("%.4s", pid);

        //カウンタ
        String counter;
        synchronized (COUNT_LOCK) {
            if (mCount >= COUNT_MAX) {
                //カウンタ周回
                mCount = 0;
            } else {
                mCount++;
            }

            counter = String.format("%06x", mCount);
            counter = String.format("%.6s", counter);
        }

        String objectId = timestamp + formatMachineId + pid + counter;
        return objectId;
    }

    static void setMachineId(String machineId) {
        mMachineId = machineId;
    }

    static void setPid(int pid) {
        mPid = pid;
    }

    static NbAcl makeAcl(NbJSONObject json) {
        NbAcl acl = null;
        if (json != null) {
            acl = new NbAcl();
            for (String key : json.keySet()) {
                switch (key) {
                    case NbBaseAcl.KEY_OWNER:
                        acl.setOwner(json.getString(key));
                        break;
                    case NbBaseAcl.KEY_READ:
                        acl.setRead((List<String>)json.get(key));
                        break;
                    case NbBaseAcl.KEY_WRITE:
                        acl.setWrite((List<String>)json.get(key));
                        break;
                    case NbBaseAcl.KEY_CREATE:
                        acl.setCreate((List<String>)json.get(key));
                        break;
                    case NbBaseAcl.KEY_UPDATE:
                        acl.setUpdate((List<String>)json.get(key));
                        break;
                    case NbBaseAcl.KEY_DELETE:
                        acl.setDelete((List<String>)json.get(key));
                        break;
                    case NbBaseAcl.KEY_ADMIN:
                        acl.setAdmin((List<String>)json.get(key));
                        break;
                    default:
                        break;
                }
            }
        }
        return acl;
    }

    static NbContentAcl makeContentAcl(NbJSONObject json) {
        NbContentAcl contentAcl = null;
        if (json != null) {
            contentAcl = new NbContentAcl();
            for (String key : json.keySet()) {
                switch (key) {
                    case NbBaseAcl.KEY_READ:
                        contentAcl.setRead((List<String>)json.get(key));
                        break;
                    case NbBaseAcl.KEY_WRITE:
                        contentAcl.setWrite((List<String>)json.get(key));
                        break;
                    case NbBaseAcl.KEY_CREATE:
                        contentAcl.setCreate((List<String>)json.get(key));
                        break;
                    case NbBaseAcl.KEY_UPDATE:
                        contentAcl.setUpdate((List<String>)json.get(key));
                        break;
                    case NbBaseAcl.KEY_DELETE:
                        contentAcl.setDelete((List<String>)json.get(key));
                        break;
                    default:
                        break;
                }
            }
        }
        return contentAcl;
    }

    /**
     * バイト列からハッシュを生成する。
     * @param data バイト列
     * @return ハッシュ化したバイト列
     */
    private static byte[] getHash(byte[] data) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] tmp = data;

            for (int i = 0; i < HASH_STRETCH_COUNT; i++ ) {
                md.reset();
                tmp = md.digest(tmp);
            }

            return tmp;
        } catch (NoSuchAlgorithmException e) {
            // アルゴリズムなし
            //e.printStackTrace();
            log.warning("getHash: {0}", e.getMessage());
            throw new RuntimeException("No Such Algorithm");
        }
    }

    /**
     * ユーザ名とパスワードからハッシュを生成する。
     * @param userId ユーザID
     * @param password パスワード
     * @return ハッシュ文字列
     */
    static synchronized String createPasswordHash(String userId, String password) {
        if ((userId == null) || (password == null)) {
            return null;
        }
        return bin2hex(createPasswordHashSub(userId,password));
    }
    /**
     * ユーザ名とユーザIDを使用してハッシュを生成する。
     * @param userId ユーザID
     * @param password パスワード
     * @return ハッシュ化したバイト列
     */
    private static synchronized byte[] createPasswordHashSub(String userId, String password) {
        byte[] dat = (userId + password).getBytes();
        return getHash(dat);
    }


    /**
     * ユーザ名とユーザIDからハッシュを生成し、指定したハッシュ値
     * と一致するかチェックする。
     * @param userId ユーザID
     * @param password パスワード
     * @param checkData ハッシュ値
     * @return true:チェックOK false:チェックNG
     */
    static synchronized boolean checkPasswordHash(String userId, String password, String checkData) {
        if ((userId == null) || (password == null) || (checkData == null)) {
            return false;
        }

        byte[] targetHash = createPasswordHashSub(userId, password);

        return MessageDigest.isEqual(targetHash, hex2bin(checkData));
    }

    /**
     * srcとdstでACLに差分があるかチェック判定する。<br>
     * @param srcPermission 比較元パーミッション（JSON形式）
     * @param dstPermission 比較先パーミッション（JSON形式）
     * @return 差分が無ければtrue,差分があればfalse
     */
    static boolean isEqualAclPermission(String srcPermission, String dstPermission) {
        NbJSONObject srcAclJson = NbJSONParser.parse(srcPermission);
        NbJSONObject dstAclJson = NbJSONParser.parse(dstPermission);

        return isEqualAclPermission(srcAclJson, dstAclJson);
    }

    static boolean isEqualAclPermission(@NonNull NbJSONObject srcAclJson, @NonNull NbJSONObject dstAclJson) {

        if (srcAclJson.size() != dstAclJson.size() ) {
            log.finer("isEqualAclPermission() return false");
            return false;
        }

        for (String key: srcAclJson.keySet()) {
            Object src = srcAclJson.get(key);
            Object dst = dstAclJson.get(key);
            if (src == null || dst == null) {
                if (src == dst) {
                    log.finer("isEqualAclPermission() key:" + key + " value is null");
                    continue;
                } else {
                    log.finer("isEqualAclPermission() key:" + key + " value is null eigher src or dst ");
                    return false;
                }
            }

            if ( (src instanceof List<?>) && (dst instanceof List<?>)) {
                List<String> srcList = (List<String>)src;
                List<String> dstList = (List<String>)dst;
                if (srcList.size() != dstList.size()) {
                    log.finer("isEqualAclPermission()"
                             + " return false. size unmatch srcList:{0} dstList:{1}", srcList, dstList);
                    return false;
                }

                //リスト内は順不同のため含まれているか否かで判断
                for (String value : srcList) {
                    if (!dstList.contains(value)) {
                        log.finer("isEqualAclPermission()"
                                + " return false. not contains srcList:{0} dstList:{1}", srcList, dstList);
                        return false;
                    }
                }
            } else {
                 //１箇所でも異なればfalseなので、以降の検索は不要。
                if (!src.toString().equals(dst.toString())) {
                    log.finer("isEqualAclPermission() return false. string unmatch src:{0} dst:{1}",
                            src, dst);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Binary→Hex変換
     */
    public static String bin2hex(byte[] data) {
        StringBuffer sb = new StringBuffer();
        for (byte b : data) {
            String s = Integer.toHexString(0xff & b).toUpperCase();
            if (s.length() == 1) {
                sb.append("0");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Hex→Binary変換
     */
    public static byte[] hex2bin(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, (i + 1) * 2), 16);
        }
        return bytes;
    }
}
