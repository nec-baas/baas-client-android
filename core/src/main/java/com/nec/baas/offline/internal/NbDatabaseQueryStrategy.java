/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.object.*;

/**
 * SQL データベースに対してクエリを行う Strategy
 */
public interface NbDatabaseQueryStrategy {
    /**
     * MongoDBライクなクエリを行う。
     * クエリ対象となるのは "document" カラムの JSON 文字列。
     *
     * @param table   テーブル名
     * @param columns カラム名の配列
     * @param where WHERE節
     * @param whereArgs WHERE節のパラメータ
     * @param query   クエリ条件
     * @return 検索結果。NbSelectObjectResults 型。
     */
    NbSelectObjectResults select(String table, String[] columns, String where, String[] whereArgs, NbQuery query);
}
