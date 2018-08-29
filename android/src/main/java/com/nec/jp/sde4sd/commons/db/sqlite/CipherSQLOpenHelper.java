/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.jp.sde4sd.commons.db.sqlite;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;
import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * 暗号化データベースの作成およびバージョン管理用ヘルパクラス。<br>
 * 通常、本クラスはSDE部品からのみ使用される。
 */
public class CipherSQLOpenHelper extends SQLiteOpenHelper {
	/**
	 * データベースデフォルトファイル名。
	 */
    private static final String DB_NAME = "sdecipherdb.db";
    /**
     * データベースデフォルトバージョン。
     */
    private static final int DB_VERSION = 1;

    /**
     * デフォルトファイル名、デフォルトバージョンにてデータベースを作成する。
     * @param context コンテキスト
     */
    public CipherSQLOpenHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * デフォルトファイル名、デフォルトバージョンにてデータベースを作成する。
     * @param context コンテキスト
     * @param name データベースファイル名
     * @param version データベースバージョン
     */
    public CipherSQLOpenHelper(Context context, String name, int version) {
        super(context, name, null, version);
    }

    /**
     * 
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
    }

    /**
     * 
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
    }

    /**
     * データベースファイルを強制削除する
     * @param context
     */
    /*package*/ static void forceDeleteDatabase(Context context) {
        //context.deleteDatabase(DB_NAME);
        File file = context.getDatabasePath(DB_NAME);
        if (file.exists()) {
            if (!file.delete()) {
                throw new RuntimeException("can't delete database");
            }
        }
    }
}