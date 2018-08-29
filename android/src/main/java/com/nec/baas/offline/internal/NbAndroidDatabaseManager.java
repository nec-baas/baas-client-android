/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.nec.baas.json.NbJSONObject;
import com.nec.baas.json.NbJSONParser;
import com.nec.baas.object.NbIndexType;
import com.nec.baas.util.*;
import com.nec.jp.sde4sd.commons.db.SDEDatabaseException;
import com.nec.jp.sde4sd.commons.db.sqlite.CipherSQLDatabase;

import net.sqlcipher.database.SQLiteException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * データベース管理クラス（Android側）。
 */
public class NbAndroidDatabaseManager extends NbDatabaseManager {
    private static final NbLogger log = NbLogger.getLogger(NbAndroidDatabaseManager.class);

    /*package*/ CipherSQLDatabase mDatabase;

    /**
     * コンストラクタ
     */
    public NbAndroidDatabaseManager(String password, Context context) {
        mDatabase = new CipherSQLDatabase(context, password);
        initialize();
    }

    @Override
    public void changePassword(String oldPass, String newPass) {
        mDatabase.changePassword(oldPass, newPass);
    }

    @Override
    public void open() {
        mDatabase.open();
    }

    @Override
    public void close() {
        mDatabase.close();
    }

    @Override
    public void begin() {
        super.begin();
        mDatabase.begin();
    }

    @Override
    public void commit() {
        mDatabase.commit();
        super.commit();
    }

    @Override
    public void rollback() {
        mDatabase.rollback();
        super.rollback();
    }

    @Override
    public int delete(String table, String where, String[] whereArgs) {
        int result = 0;
        try {
            log.finer("delete() table=" + table
                    + " where=" + where + " whereArgs=" + Arrays.toString(whereArgs));

            result = mDatabase.delete(table, where, whereArgs);
        } catch (SDEDatabaseException ex) {
            throw new NbDatabaseException(ex);
        }
        log.finer("delete() <END> result=" + result);
        return result;
    }

    @Override
    public void execSQL(String sql) {
        try {
            log.finer("execSQL() sql={0}", sql);

            mDatabase.execSQL(sql);
        } catch (SDEDatabaseException ex) {
            throw new NbDatabaseException(ex);
        }
    }

    @Override
    public List<Map<String, String>> select(String table, String[] columns, String where,
            String[] whereArgs, String orderBy, int offset, int limit) {
        Cursor cursor = null;
        try {
            cursor = mDatabase.select(table, columns, where, whereArgs, orderBy, offset, limit);
            return readAll(cursor);
        } catch (SDEDatabaseException | SQLiteException ex) {
            throw new NbDatabaseException(ex);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private List<Map<String, String>> readAll(Cursor cursor) {
        List<Map<String, String>> result = new ArrayList<>();
        boolean next = cursor.moveToFirst();
        int columnCount = cursor.getColumnCount();
        while (next) {
            Map<String, String> data = new HashMap<>();
            for (int i = 0; i < columnCount; i++) {
                String key = cursor.getColumnName(i);
                data.put(key, cursor.getString(i));
            }
            if (!data.isEmpty()) {
                result.add(data);
            }
            next = cursor.moveToNext();
        }
        return result;
    }

    @Override
    public CursorWrapper selectForCursor(String table, String[] columns, String where,
                                         String[] whereArgs, String orderBy, int offset, int limit) {
        try {
            Cursor cursor = mDatabase.select(table, columns, where, whereArgs, orderBy, offset, limit);
            return new AndroidCursorWrapper(cursor);
        } catch (SDEDatabaseException | SQLiteException ex) {
            throw new NbDatabaseException(ex);
        }
    }

    public static class AndroidCursorWrapper implements CursorWrapper {

        private Cursor cursor;

        public AndroidCursorWrapper(Cursor cursor) {
            this.cursor = cursor;
        }

        @Override
        public void close() {
            cursor.close();
        }

        @Override
        public int getCount() {
            return cursor.getCount();
        }

        @Override
        public int getColumnCount() {
            return cursor.getColumnCount();
        }

        @Override
        public boolean moveToFirst() {
            return cursor.moveToFirst();
        }

        @Override
        public boolean moveToNext() {
            return cursor.moveToNext();
        }

        @Override
        public String getColumnName(int idx) {
            return cursor.getColumnName(idx);
        }

        @Override
        public String getString(int idx) {
            return cursor.getString(idx);
        }

        @Override
        public int getInt(int idx) {
            return cursor.getInt(idx);
        }
    }

    @Override
    public long insert(String table, Map<String, String> values) {
        try {
            ContentValues contentValues = makeContentValues(table, values);
            return mDatabase.insert(table, contentValues);
        } catch (SDEDatabaseException ex) {
            throw new NbDatabaseException(ex);
        }
    }

    @Override
    public int update(String table, Map<String, String> values, String where,
            String[] whereArgs) {
        try {
            ContentValues contentValues = makeContentValues(table, values);
            return mDatabase.update(table, contentValues, where, whereArgs);
        } catch (SDEDatabaseException ex) {
            throw new NbDatabaseException(ex);
        }
    }

    protected ContentValues makeContentValues(String table, Map<String, String> values) {
        ContentValues contentValues = new ContentValues();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            // indexカラム以外はStringのままputする
            contentValues.put(entry.getKey(), entry.getValue());

            // JSONドキュメントを見つけたら、パースしてindexカラムの値を抽出する
            // indexカラムは型を判別して保存する
            if (entry.getKey().equals(DOCUMENT_COLUMN)) {
                // JSONをパースする
                //log.fine("jsonString: " + values.get(DOCUMENT_COLUMN));
                NbJSONObject jsonObject = NbJSONParser.parse(values.get(DOCUMENT_COLUMN));

                // 現在のindexキーを取得する
                Map<String, NbIndexType> currentIndexes = getIndexWithTable(table);
                //log.fine("currentIndexes: " + currentIndexes);

                // indexキーをループしてJSONドキュメント(トップレベルのみ)に存在するものを探す
                for (Map.Entry<String, NbIndexType> indexEntry : currentIndexes.entrySet()) {
                    String indexKey = indexEntry.getKey();
                    NbIndexType indexType = indexEntry.getValue();
                    //log.fine("indexEntry: " + indexKey + ": " + indexType);

                    // 現状トップレベルのキーのみをインデックス可能とする
                    Object jsonValue = jsonObject.get(indexKey);
                    //log.fine("| jsonEntry: " + indexKey + ": " + jsonValue);

                    // DB上の型つきのキー名に変換する
                    String indexKeyOnColumn = getIndexKeyForColumn(indexKey, indexType);

                    // index用カラムは型を判別して適切な型のputを呼び出す
                    if (jsonValue == null) {
                        // 値がnullの場合とキーが存在しない場合(MongoDB上でも2つは同等とみなしている)
                        contentValues.put(indexKeyOnColumn, "null");
                        log.fine("null: " + indexKeyOnColumn);
                    } else if (jsonValue instanceof String) {
                        if (indexType.equals(NbIndexType.STRING)) {
                            contentValues.put(indexKeyOnColumn, (String) jsonValue);
                        } else {
                            log.warning("Don't create index for key: " + indexKeyOnColumn + " value: " + jsonValue);
                        }
                        log.fine("String: " + jsonValue);
                    } else if (jsonValue instanceof Boolean) {
                        if (indexType.equals(NbIndexType.BOOLEAN)) {
                            // DB側にBoolean型がないので文字列としてDBへ保存する
                            contentValues.put(indexKeyOnColumn, (Boolean) jsonValue ? "true" : "false");
                        } else {
                            log.warning("Don't create index for key: " + indexKeyOnColumn + " value: " + jsonValue);
                        }
                        log.fine("Boolean: " + jsonValue);
                    } else if (jsonValue instanceof Number) {
                        if (indexType.equals(NbIndexType.NUMBER)) {
                            contentValues.put(indexKeyOnColumn, ((Number) jsonValue).doubleValue());
                        } else {
                            log.warning("Don't create index for key: " + indexKeyOnColumn + " value: " + jsonValue);
                        }
                        log.fine("Number: " + jsonValue);
                    } else {
                        // JSONObject, JSONArrayなどはインデックス対象外のため、何もしない
                        log.warning("Don't create index for key: " + indexKeyOnColumn + " value: " + jsonValue);
                    }

                }
            }

        }
        return contentValues;
    }

    /**
     * カラム名一覧を取得する。
     * SQLCipher(SQLite) の PRAGMA table_info を使用する。
     * @param table テーブル名
     * @return カラム名の List
     * @throws NbDatabaseException テーブルが存在しない
     */
    @Override
    protected List<String> getColumnNames(String table) {
        List<String> list = new ArrayList<>();

        // スキーマキャッシュを破棄・リロードする
        refreshSchemaCache();

        Cursor cursor = null;
        try {
            cursor = mDatabase.rawQuery("PRAGMA table_info(" + table + ")", null);
            if (cursor == null) {
                // テーブルが存在しない。例外とする。
                throw new NbDatabaseException("No such table: " + table);
            }

            boolean hasNext = cursor.moveToFirst();
            while (hasNext) {
                list.add(cursor.getString(1));
                hasNext = cursor.moveToNext();
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (list.size() == 0) {
            // カラム数が0のときはテーブルが存在しないものと見なし、例外とする。
            throw new NbDatabaseException("No such table: " + table);
        }

        return list;
    }

    /**
     * スキーマキャッシュを強制的に破棄・リロードさせる。
     * sqlite_master テーブルを読むことで、実施する。
     */
    private void refreshSchemaCache() {
        Cursor cursor = null;
        try {
            cursor = mDatabase.select("sqlite_master", null, null, null, null, 0, 1);
            cursor.moveToFirst(); // 内容を読みだす必要はない。
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * デバッグ用: sqlite_master テーブルスキーマをダンプする。
     */
    /*
    protected void dumpSqlMaster(String table) {
        // DEBUG
        Cursor cursor = null;
        try {
            String where = null;
            String[] whereArgs = null;

            if (table != null) {
                where = "tbl_name = ?";
                whereArgs = new String[] { table };
            }

            cursor = mDatabase.select("sqlite_master",
                    new String[]{"type", "name", "tbl_name", "sql"}, where, whereArgs, null, 0, -1);
            boolean hasNext = cursor.moveToFirst();
            while (hasNext) {
                String type = cursor.getString(0);
                String name = cursor.getString(1);
                String tbl_name = cursor.getString(2);
                String sql = cursor.getString(3);
                Log.d("DatabaseSchema", String.format("%s|%s|%s|%s", type, name, tbl_name, sql));
                hasNext = cursor.moveToNext();
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    */
}
