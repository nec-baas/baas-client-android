/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.json.*;
import com.nec.baas.object.*;
import com.nec.baas.offline.*;
import com.nec.baas.util.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SQL Database に対して Mongo ライクなクエリを実行するロジック
 */
public class NbDatabaseQueryStrategyImpl implements NbDatabaseQueryStrategy {
    private static final NbLogger log = NbLogger.getLogger(NbDatabaseQueryStrategy.class);

    /**
     * SELECT 発行時の最大件数 (limit)。
     * Android 実装の場合、Cursor 内に格納できるデータサイズに上限がある(1MB程度)
     * ため、limit をかける必要がある。
     */
    public static final int SELECT_LIMIT = 200;

    private NbDatabaseManager mManager;

    public NbDatabaseQueryStrategyImpl(NbDatabaseManager manager) {
        mManager = manager;
    }

    /**
     * MongoDBライクなクエリを行う。
     * クエリ対象となるのは "document" カラムの JSON 文字列。
     * @param table テーブル名
     * @param columns カラム名の配列
     * @param where SQL の where 節
     * @param whereArgs SQL の where 引数
     * @param query クエリ条件
     * @return 検索結果。NbSelectObjectResults 型。
     */
    @Override
    public NbSelectObjectResults select(String table, String[] columns, String where, String[] whereArgs, NbQuery query) {
        /*
         * Step 1: クエリ条件整理
         */
        //MongoDBのwhere句
        NbJSONObject expr = null;
        int limit = NbConsts.DEFAULT_QUERY_LIMIT;
        int skip = 0;
        boolean includeDeletedData = false;

        if (query != null) {
            if (query.getClause() != null && query.getClause().getConditions() != null) {
                expr = query.getClause().getConditions();
            }
            limit = query.getLimit();
            skip = query.getSkipCount();
            includeDeletedData = query.isDeleteMark();
        }

        /*
         * Step 2: SELECT 実行 / JSON 比較 / 結果格納
         */
        NbSelectObjectResults results = selectWithMongoQuery(table, columns, where, whereArgs, expr, includeDeletedData);

        /*
         * Step 3: sort, offset, limit 処理
         */
        NbSelectObjectResults outResults = new NbSelectObjectResults();

        //条件に合致した件数を格納
        outResults.setTotalCount(results.getResults().size());

        //ヒット有の場合は、全体ソート→QUERY条件対応を行う
        if (!results.getResults().isEmpty()) {
            //全体ソート（指定ありのみ）
            if (query != null && !query.getSortOrder().isEmpty()) {
                LinkedHashMap<String, Boolean> sort = query.getSortOrder();
                results.sortResults(sort);
            }

            int checkedCount = 0; //チェックした件数
            int outCount = 0;   //OUTデータに格納する件数

            //QUERY条件に従い、取得開始位置、件数を制限
            for (NbSelectObjectResults.Result result : results.getResults()) {
                //skipの位置から取得を開始
                //取得上限件数に達するまではoutResultsに格納。
                if ((checkedCount >= skip) && (limit < 0 || outCount < limit)) {
                    NbJSONObject json = result.getJson();

                    // result に入っている JSON は不変(キャッシュ)なので、コピーが必要
                    json = json.getMutableInstance();

                    outResults.addResult(json, result.getColumns());
                    outCount++;
                }
                checkedCount++;
            }
        }

        return outResults;
    }

    /**
     * SQL DB に対して、MongoDB クエリ付きで検索を行う
     * @param table テーブル名
     * @param columns カラム名の配列
     * @param expr クエリ条件 (MongoDB Query 相当)
     * @param includeDeletedData 削除データを読み込む
     * @return 検索に合致した Result セット。totalCount に件数が入る。
     */
    protected NbSelectObjectResults selectWithMongoQuery(String table, String[] columns, String where, String whereArgs[],
                                                       NbJSONObject expr, boolean includeDeletedData) {
        // "state", "document" カラム位置を算出しておく
        int stateColumnIdx = -1;
        int documentColumnIdx = -1;
        for (int i = 0; i < columns.length; i++) {
            String key = columns[i];
            if (key.equals(NbDatabaseManager.DOCUMENT_COLUMN)) {
                documentColumnIdx = i;
            }
            else if (key.equals(NbDatabaseManager.STATE_COLUMN)) {
                stateColumnIdx = i;
            }
        }
        if (documentColumnIdx < 0) {
            throw new IllegalArgumentException("no document column");
        }

        NbDatabaseWrapper.CursorWrapper cursor = null;
        try {
            NbSelectObjectResults results = new NbSelectObjectResults();

            mManager.begin();

            // 変換アルゴリズムを通してMongoクエリをSQLクエリに変換する
            log.fine("selectWithMongoQuery()  expr=" + expr);
            Map<String, NbIndexType> indexKeys = mManager.getIndexWithTable(table);
            NbWhere convertedWhere = new NbMongoQueryConverter().convert(expr, indexKeys);
            log.fine("selectWithMongoQuery()  convertedWhere=" + convertedWhere);
            if (convertedWhere != null) {
                where = convertedWhere.getWhere().toString();
                whereArgs = convertedWhere.getWhereArgs().toArray(new String[convertedWhere.getWhereArgs().size()]);
            }
            log.fine("selectWithMongoQuery()  where=" + where);
            log.fine("selectWithMongoQuery()  whereArgs=" + (whereArgs == null ? null : Arrays.asList(whereArgs)));

            int sqlOffset = 0;

            while (true) {
                // 分割 SELECT を行う。
                // _id でソートし、SQL_LIMIT 件数ずつ取り出す。
                cursor = mManager.selectForCursor(table, columns, where, whereArgs, "_id", sqlOffset, SELECT_LIMIT);
                int count = cursor.getCount();
                if (count == 0) {
                    break; // 残データなし
                }
                sqlOffset += count;

                // 結果処理
                processSqlResults(cursor, results, expr, documentColumnIdx, stateColumnIdx, includeDeletedData);

                cursor.close();
                cursor = null;

                if (count < SELECT_LIMIT) {
                    break; // 後続データなし
                }
            }

            return results;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            mManager.commit();
        }
    }

    /**
     * SQL クエリ結果を MongoDB Query と照合し、マッチしたものを results に格納。
     * @param cursor カーソル
     * @param results 結果格納用オブジェクト
     * @param expr クエリ条件 (MongoDB Query 相当)
     * @param includeDeletedData 削除マークを読む場合は true
     */
    private void processSqlResults(NbDatabaseWrapper.CursorWrapper cursor, NbSelectObjectResults results, NbJSONObject expr,
                                   int documentColumnIdx, int stateColumnIdx, boolean includeDeletedData) {
        final int columnCount = cursor.getColumnCount();

        boolean hasNext;
        for (hasNext = cursor.moveToFirst(); hasNext; hasNext = cursor.moveToNext()) {
            // deleteMark チェック。
            // 削除データを読まない、かつ削除データの場合はスキップする
            if (!includeDeletedData && stateColumnIdx >= 0) {
                NbSyncState state = NbSyncState.fromObject(cursor.getInt(stateColumnIdx));
                if (state.isDeleted()) {
                    //NbUtil.nebulaLog(Level.FINER, "AndroidDatabaseManager() select [SET] isSkip = true");
                    continue;
                }
            }

            // ドキュメント取得
            String jsonString = cursor.getString(documentColumnIdx);
            NbJSONObject json = mManager.matchJsonWithQuery(jsonString, expr);
            if (json != null) {
                // マッチ
                Map<String, String> data = new HashMap<>();
                for (int i = 0; i < columnCount; i++) {
                    data.put(cursor.getColumnName(i), cursor.getString(i));
                }
                // この JSON データはキャッシュで不変の場合があるので注意
                results.addResult(json, data);
            }
        }
    }
}
