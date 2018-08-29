/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.json.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * MongoDBクエリ結果格納用クラス
 */
public class NbSelectObjectResults {
    /**
     * 検索結果1件
     */
    @Getter
    public static class Result {
        /**
         * SQL行のカラム-値マップ
         */
        private Map<String, String> columns;

        /**
         * JSONデータ
         */
        private NbJSONObject json;
    }

    /**
     * 検索結果のリスト
     */
    @Getter
    private List<Result> results = new ArrayList<>();

    /**
     * 全件数 (skip/limit前のもの)
     */
    @Getter @Setter
    private int totalCount = 0;

    /**
     * 結果追加
     */
    public void addResult(NbJSONObject json, Map<String, String> columns) {
        Result res = new Result();
        res.json = json;
        res.columns = columns;
        results.add(res);
    }

    /**
     * 結果をソートする。ソート対象は JSON 部分のみである (columns は見ない)
     *
     * <p>ソート条件は、ソートに使用するキーと、昇順フラグ(true:昇順, false:降順)のペア
     * を優先順にならべた LinkedHashMap で指定する。
     * @param sort ソート条件
     */
    public void sortResults(LinkedHashMap<String, Boolean> sort) {
        Collections.sort(results, new NbSelectObjectResults.ResultComparator(sort));
    }

    /**
     * Result の ソート用比較クラス
     */
    private static class ResultComparator implements Comparator<Result> {
        private LinkedHashMap<String, Boolean> mSort;

        /**
         * コンストラクタ
         */
        public ResultComparator(LinkedHashMap<String, Boolean> sort) {
            mSort = sort;
        }

        @Override
        public int compare(NbSelectObjectResults.Result lResult, NbSelectObjectResults.Result rResult) {
            int result = 0;
            if ((lResult == null || rResult == null) ||
                    (lResult.getJson() == null || rResult.getJson() == null)) {
                return -1;
            }

            //第1キーから順番に抜き取り、各々で比較する。
            for (String key : mSort.keySet()) {
                //比較データ
                Object lData, rData;

                lData = lResult.getJson().get(key);
                rData = rResult.getJson().get(key);

                if (mSort.get(key)) { //昇順
                    result = compareObject(lData, rData);
                } else { //降順
                    result = -compareObject(lData, rData); // 逆順
                }

                if (result != 0) {
                    return result;
                }
                //キーの大小比較が一致する場合は次のキーで比較
            }
            return result;
        }

        private int compareObject(Object lObj, Object rObj) {
            // null は無限小扱い (MongoDB動作)
            if (lObj == null) {
                return -1;
            }
            if (rObj == null) {
                return 1;
            }

            if (lObj instanceof String && rObj instanceof String) {
                return ((String) lObj).compareTo((String) rObj);
            }
            if (lObj instanceof Number && rObj instanceof Number) {
                double d = ((Number) lObj).doubleValue() - ((Number) rObj).doubleValue();
                if (d < 0) return -1;
                if (d > 0) return 1;
                return 0;
            }
            return 0; // incompatible type
        }
    }
}
