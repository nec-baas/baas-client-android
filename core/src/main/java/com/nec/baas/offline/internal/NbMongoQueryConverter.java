/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.object.NbIndexType;
import com.nec.baas.util.NbLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MongoDBクエリをSQLクエリに変換するクラス。
 *
 * 完全な変換ではなく、DBから引き出すデータ数を絞り込むための大雑把な変換を行う。
 * 正確な評価は別クラスで行う。
 * @see NbMongoQueryEvaluator
 *
 * 変換アルゴリズムは以下。
 *
 * MongoDBクエリ式内の全フィールドをスキャンする
 *   "$and" 条件があった場合は、その中の全フィールドも再帰的にスキャンする。
 *   "$and" 以外の論理演算子は無視する
 *     $or, $not などはサポートしない (できなくはないが使用頻度は低いため当面除外)
 * スキャンしたフィールドのうち、インデックスに一致したフィールドのみを抽出する。
 * 各フィールドに指定された値を確認する。
 *   スカラ値(文字列、数値など)が指定されている場合は、一致条件として使用する。
 *   JSONオブジェクトが指定されている場合は、内部の演算子を順にチェックする。
 *     比較演算子($gt, $gte, $lt, $lte) は SQL の比較式に置換する。
 *     $in 演算子は IN に置換する。
 *     これ以外の演算子は無視する(サポート外)
 * 最後に全条件式を AND で結合する。
 */
public class NbMongoQueryConverter {
    private static final NbLogger log = NbLogger.getLogger(NbMongoQueryConverter.class);

    public NbMongoQueryConverter() {
    }

    /**
     * MongoDBクエリ式をSQLクエリ式に変換する
     *
     * @param expr MongoDBクエリ式
     * @param indexKeys インデックス設定しているキー名
     * @return 変換アルゴリズムに基づいて変換したSQLクエリ式
     */
    public NbWhere convert(Map<String,Object> expr, Map<String, NbIndexType> indexKeys) {
        log.fine("convert() <start>");

        if (expr == null) {
            log.fine("convert() <end> expr is null");
            return null;
        }

        try {
            List<NbWhere> wheresForAnd = new ArrayList<>();

            // expression単位でループ
            // それぞれをSQLクエリに変換して、リストに詰めておく
            for (Map.Entry<String,Object> entry : expr.entrySet()) {
                String key = entry.getKey();

                Object operand = entry.getValue();
                log.fine("convert()"
                        + " key=" + key + " operand=" + operand);

                NbWhere res = null;
                if (key.startsWith("$")) {
                    res = convertLogicalOperator(key, operand, indexKeys);
                } else {
                    // インデックスしていないkeyならスキップ
                    if (!indexKeys.containsKey(key)) {
                        log.info("not index key: " + key);
                        continue;
                    }
                    res = convertOperand(key, operand, indexKeys);
                }

                if (res != null) {
                    NbWhere temp = new NbWhere();
                    temp.setWhere(new StringBuilder(res.getWhere()));
                    temp.getWhereArgs().addAll(res.getWhereArgs());
                    wheresForAnd.add(temp);
                }
            }
            // リストに詰めておいたものを連結する
            NbWhere result = concatWheres(wheresForAnd, " AND ");

            log.fine("convert() <end> result=" + result);
            return result;
        } catch (ClassCastException ex) {
            log.warning("convert() <end> return null"
                    + " [ClassCastException] ex=" + ex);
            return null;
        }
    }

    /**
     * 論理演算子処理
     */
    private NbWhere convertLogicalOperator(String operator, Object operand, Map<String, NbIndexType> indexKeys) {
        log.fine("convertLogicalOperator() operator=" + operator);
        switch (operator) {
            case "$and":
                return andOperator((List<Map<String, Object>>) operand, indexKeys);
            case "$or":
            case "$nor":
            case "$not":
                // unsupported
                return null;
            default:
                log.warning("convertLogicalOperator() ERR unknown operator");
                return null; // unknown operator
        }
    }

    private NbWhere andOperator(List<Map<String,Object>> expressions, Map<String, NbIndexType> indexKeys) {
        List<NbWhere> wheresForAnd = new ArrayList<>();

        // それぞれをSQLクエリに変換して、リストに詰めておく
        for (Map<String,Object> expr : expressions) {
            NbWhere res = convert(expr, indexKeys);

            if (res != null) {
                NbWhere temp = new NbWhere();
                temp.setWhere(new StringBuilder(res.getWhere()));
                temp.getWhereArgs().addAll(res.getWhereArgs());
                wheresForAnd.add(temp);
            }
        }
        // リストに詰めておいたものを連結する
        NbWhere result = concatWheres(wheresForAnd, " AND ");

        return result;
    }

    /**
     * オペランド評価
     */
    private NbWhere convertOperand(String key, Object operand, Map<String, NbIndexType> indexKeys) {
        log.fine("convertOperand() <start>"
                + " key=" + key + " operand=" + operand);

        if (operand instanceof Map) {
            // 複合 operand 評価
            return convertCompositeOperand(key, (Map<String, Object>) operand, indexKeys);
        } else if (operand instanceof List) {
            // 配列評価
            // 配列の完全一致 例:{"a": [100, 200]}
            // 配列はインデックスを作成しないので変換する意味なし
//            List<Object> values = (List<Object>)operand;
//            log.fine("convertOperand()" + " values.length()=" + values.size() + " values=" + values);
//            log.fine("convertOperand() <end> return null");
            return null;
        } else {
            // スカラ値
            NbWhere result = new NbWhere();
            result.getWhere().append(NbDatabaseManager.getIndexKeyForColumn(key, indexKeys.get(key))).append(" = ?");
            result.getWhereArgs().add(convertScalar(operand).toString());
            log.fine("convertOperand() <end> return");
            return result;
        }
    }

    /**
     * 複合 (Map<String,Object>) オペランド評価。
     * (中に比較演算子を含む Map<String,Object>)
     */
    // 例) {"a":{"$gt":3, "$lt":10}}
    private NbWhere convertCompositeOperand(String key,
                Map<String,Object> operand, Map<String, NbIndexType> indexKeys) {
        List<NbWhere> wheresForAnd = new ArrayList<>();

        // それぞれをSQLクエリに変換して、リストに詰めておく
        for (Map.Entry<String,Object> entry : operand.entrySet()) {
            String operator = entry.getKey();
            NbWhere res = convertOperator(key, operator, entry.getValue(), indexKeys);

            if (res != null) {
                NbWhere temp = new NbWhere();
                temp.setWhere(new StringBuilder(res.getWhere()));
                temp.getWhereArgs().addAll(res.getWhereArgs());
                wheresForAnd.add(temp);

                log.fine("convertCompositeOperand()"
                        + " operator=" + operator + " operand=" + operand
                        + " operand.get(" + operator + ")=" + operand.get(operator));
            }
        }
        // リストに詰めておいたものを連結する
        NbWhere result = concatWheres(wheresForAnd, " AND ");

        log.fine("convertCompositeOperand() return");
        return result;
    }

    /**
     * 比較演算子評価
     */
    private NbWhere convertOperator(String key, String operator, Object operatorArg, Map<String, NbIndexType> indexKeys) {

//        log.fine("convertOperator"
//                 + " key=" + key);
//        log.fine("convertOperator"
//                 + " operator=" + operator);
//        log.fine("convertOperator"
//                 + " operatorArg=" + operatorArg);

        switch (operator) {
            case "$in":
                return inOperator(key, (List<Object>) operatorArg, indexKeys);

            case "$gt":
            case "$gte":
            case "$lte":
            case "$lt":
                return compareOperator(operator, key, operatorArg, indexKeys);

            case "$all":
            case "$nin":
            case "$exists":
            case "$ne":
            case "$regex":
            case "$options":
            case "$not":
                // unsupported
                return null;

            default:
                log.warning("convertOperator() ERR unknown operator");
                return null; // unsupported operator
        }
    }

    /**
     * 大小比較
     */
    private NbWhere compareOperator(String operator, String key, Object operand, Map<String, NbIndexType> indexKeys) {
        // 大小比較の値がnullのとき 例:{"b":{"$gte":null}}
        if (operand == null) {
            log.info("compareOperator() null");
            return null;
        }

        NbWhere result = new NbWhere();
        switch (operator) {
            case "$gt":
                result.getWhere().append(NbDatabaseManager.getIndexKeyForColumn(key, indexKeys.get(key))).append(" > ?");
                result.getWhereArgs().add(convertScalar(operand).toString());
                return result;
            case "$gte":
                result.getWhere().append(NbDatabaseManager.getIndexKeyForColumn(key, indexKeys.get(key))).append(" >= ?");
                result.getWhereArgs().add(convertScalar(operand).toString());
                return result;
            case "$lte":
                result.getWhere().append(NbDatabaseManager.getIndexKeyForColumn(key, indexKeys.get(key))).append(" <= ?");
                result.getWhereArgs().add(convertScalar(operand).toString());
                return result;
            case "$lt":
                result.getWhere().append(NbDatabaseManager.getIndexKeyForColumn(key, indexKeys.get(key))).append(" < ?");
                result.getWhereArgs().add(convertScalar(operand).toString());
                return result;
            default:
                return null;
        }
    }

    /**
     * $in 演算子
     */
    private NbWhere inOperator(String key, List<Object> args, Map<String, NbIndexType> indexKeys) {
        log.fine("inOperator() <start>");

        if (args == null) {
            log.info("inOperator() null");
            return null;
        }

        NbWhere result = new NbWhere();
        result.getWhere().append(NbDatabaseManager.getIndexKeyForColumn(key, indexKeys.get(key))).append(" IN (");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) result.getWhere().append(", ");
            result.getWhere().append("?");
            result.getWhereArgs().add(convertScalar(args.get(i)).toString());
        }
        result.getWhere().append(")");

        log.fine("inOperator() <end> return");
        return result;
    }

    private Object convertScalar(Object operand) {
        Object result = operand;
        if (operand == null) {
            // JSONのnullはDB上は文字列"null"として保存しているので、検索クエリ式では文字列に変換する
            result = "null";
        }
        return result;
    }

    /**
     * NbWhereのリストを1つのNbWhereに連結する。リストが複数個の場合は、前後に"(", ")"をつける。
     * @param wheres 連結したいNbWhereのリスト
     * @param delimiter whereを連結するときの区切り文字列
     * @return 連結した結果
     */
    private NbWhere concatWheres(List<NbWhere> wheres, String delimiter) {
        NbWhere result = new NbWhere();

        // リストが空の場合は何もしない
        if (wheres.isEmpty()) {
            return null;
        }

        if (wheres.size() > 1) result.getWhere().append("(");
        for (int i = 0; i < wheres.size(); i++) {
            if (i > 0) result.getWhere().append(delimiter);
            result.getWhere().append(wheres.get(i).getWhere());
            result.getWhereArgs().addAll(wheres.get(i).getWhereArgs());
        }
        if (wheres.size() > 1) result.getWhere().append(")");
        return result;
    }
}
