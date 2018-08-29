/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.jp.sde4sd.commons.db;

import android.content.ContentValues;
import android.database.Cursor;

/**
 * SDE向けデータベース操作用インタフェース
 */
public interface SDEDatabaseInterface {
    public void open();
    public void close();
    public void createTable(String sql);
    public Cursor select(String table, String[] columns, String where, String[] whereArgs, String orderBy, int offset, int limit);
    public long insert(String table, ContentValues values);
    public int update(String table, ContentValues values, String where, String[] whereArgs);
    public int delete(String table, String where, String[] whereArgs);
    public void begin();
    public void commit();
    public void rollback();
    public void execSQL(String sql);
    public Cursor rawQuery(String sql, String[] columns);
}
