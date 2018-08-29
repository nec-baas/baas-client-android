/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import java.util.List;
import java.util.Map;

/**
 * SQL DB ラッパーインタフェース。
 *
 * <p>SQL DB 関連の API をまとめたもの。
 * SDK 内部でのみ使用する。
 */
public interface NbDatabaseWrapper {
    /**
     * カーソルラッパ
     */
    interface CursorWrapper {
        void close();
        int getCount();
        int getColumnCount();
        boolean moveToFirst();
        boolean moveToNext();
        String getColumnName(int idx);
        String getString(int idx);
        int getInt(int idx);
    }

    /**
     * データベースを open する
     */
    void open();

    /**
     * データベースを close する
     */
    void close();

    /**
     * SELECT でクエリを行う
     * @param table テーブル名
     * @param columns カラム名の配列
     * @param where 検索条件(WHERE)
     * @param whereArgs 検索条件プレースホルダに指定する引数配列
     * @param orderBy ソート条件(ORDER BY)
     * @param offset 検索スキップ数。limit を1以上にしないと効果なし。
     * @param limit 検索数上限。0 または負の値を指定した場合は制限なし。
     * @return 検索結果。テーブルの1行はカラム名-値のマップで、このマップの Listで返却される。
     *         エラー時は例外が throw される。nullが返却されることはない。
     */
    List<Map<String, String>> select(String table, String[] columns,
                                                        String where, String[] whereArgs, String orderBy, int offset, int limit);

    /**
     * SELECT でクエリを行う。結果はカーソルで返却される。
     * 返却されたカーソルは、使用後必ず close すること。
     *
     * <p>エラーとなった場合は NbDatabaseException が throw される。
     *
     * @param table テーブル名
     * @param columns カラム名の配列
     * @param where 検索条件(WHERE)
     * @param whereArgs 検索条件プレースホルダに指定する引数はいえrつ
     * @param orderBy ソート条件(ORDER BY)
     * @param offset 検索スキップ数
     * @param limit 検索数上限
     * @return カーソル
     */
    CursorWrapper selectForCursor(String table, String[] columns, String where,
                                         String[] whereArgs, String orderBy, int offset, int limit);

    long insert(String table, Map<String, String> values);
    int update(String table, Map<String, String> values, String where, String[] whereArgs);
    int delete(String table, String where, String[] whereArgs);
    void begin();
    void commit();
    void rollback();
    void execSQL(String sql);


    /**
     * ローカルDBのパスワード変更を行う。<br>
     * @param oldPassword 古いパスワード
     * @param newPassword 新しいパスワード
     */
    void changePassword(String oldPassword, String newPassword);
}
