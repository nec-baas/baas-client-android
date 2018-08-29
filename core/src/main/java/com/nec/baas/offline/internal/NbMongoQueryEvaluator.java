/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.json.*;
import com.nec.baas.util.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MongoDB クエリの評価器。
 * <p/>
 * 対応している演算子は以下のとおり。
 *
 * <ul>
 *     <li>論理演算子: $and, $or, $nor, $not</li>
 *     <li>比較演算子: $eq, $ne, $gt, $gte, $lte, $lt, $in, $nin, $all, $exists</li>
 *     <li>正規表現: $regex ($options は i, m のみ使用可, /re/ 形式は使用不可)</li>
 * </ul>
 *
 * 以下には対応していない
 *
 * <ul>
 *     <li>$size, $elemMatch, $type, $mod, /re/, $text, $where</li>
 *     <li>地理情報関係全部</li>
 * </ul>
 *
 * Embedded Document 比較は対応している。
 * Dot Notation も対応。ただし、配列インデックス省略形は未サポート。
 */
public class NbMongoQueryEvaluator {
    private static final NbLogger log = NbLogger.getLogger(NbMongoQueryEvaluator.class);

    /**
     * フィールドが存在しないことを表す特殊値。
     */
    private static final Object NO_FIELD = new Object();
    
    public NbMongoQueryEvaluator() { };

    /**
     * JSON ドキュメントが MongoDB のクエリ（セレクタ）にマッチするか調べる
     *
     * @param doc 評価対象データ
     * @param expr MongoDB のクエリ式
     * @return 対象データが selector にマッチすれば true、しなければ false
     */
    public boolean evaluate(NbJSONObject doc, NbJSONObject expr) {
        //log.fine("evaluate() <start>");

        try {
            //expression単位でループ
            for (Map.Entry<String,Object> entry : expr.entrySet()) {
                String key = entry.getKey();
                Object operand = entry.getValue();
                //log.fine("evaluate()"
                //        + " key=" + key + " operand=" + operand);

                boolean result;
                if (key.startsWith("$")) {
//                if (key.startsWith("$and") || key.startsWith("$or") || key.startsWith("$nor") || key.startsWith("$not")) {
                    result = evaluateLogicalOperator(doc, key, operand);
                } else {
                    result = evaluateOperand(doc, key, operand);
                }
                if (!result) {
                    //log.fine("evaluate() <end> return false");
                    return false;
                }
            }
            //log.fine("evaluate() <end> return true");
            return true;
        } catch (ClassCastException ex) {
            log.fine("evaluate() <end> return false"
                    + " [ClassCastException] ex=" + ex);
            return false;
        }
    }

    /**
     * 論理演算子処理
     * @param doc JSON
     * @param operator 演算子
     * @param operand オペランド
     * @return 成功時は true
     */
    private boolean evaluateLogicalOperator(NbJSONObject doc, String operator, Object operand) {
        //log.fine("evaluateOperand() operator=" + operator);
        switch (operator) {
            case "$and":
                return andOperator(doc, (List<NbJSONObject>) operand);
            case "$or":
                return orOperator(doc, (List<NbJSONObject>) operand);
            case "$nor":
                return !orOperator(doc, (List<NbJSONObject>) operand);
            case "$not":
                return notOperator(doc, (NbJSONObject) operand);
            default:
                log.fine("evaluateLogicalOperator() ERR unknown operator");
                return false; // unknown operator
        }
    }

    private boolean andOperator(NbJSONObject doc, List<NbJSONObject> expressions) {
        for (NbJSONObject expr : expressions) {
            if (!evaluate(doc, expr)) {
                return false;
            }
        }
        return true;
    }

    private boolean orOperator(NbJSONObject doc, List<NbJSONObject> expressions) {
        for (NbJSONObject expr : expressions) {
            if (evaluate(doc, expr)) {
                return true;
            }
        }
        return false;
    }

    private boolean notOperator(NbJSONObject doc, NbJSONObject expression) {
        return !evaluate(doc, expression);
    }

    /**
     * NbJSONObject から指定されたキーの位置の値を取得する。
     * key が "." 区切りの場合は、その階層をたどる。
     * @param doc JSON
     * @param key キー
     * @return 値。存在しない場合は NO_FIELD。
     */
    private Object getValue(Object doc, String key) {
        try {
            if (!key.contains(".")) {
                // "." 区切りなし
                NbJSONObject json = (NbJSONObject)doc;
                return json.containsKey(key) ? json.get(key) : NO_FIELD;
            }

            // "." で区切って階層をたどる
            String[] keyArray = key.split("\\.");
            for (String k : keyArray) {
                try {
                    // 配列インデックスの場合の処理
                    int index = Integer.parseInt(k);
                    doc = ((List<Object>) doc).get(index);
                } catch (NumberFormatException e) {
                    NbJSONObject json = (NbJSONObject)doc;
                    if (!json.containsKey(k)) {
                        return NO_FIELD;
                    }
                    doc = json.get(k);
                }
                if (doc == null) {
                    return null;
                }
            }
            return doc;
        } catch (IndexOutOfBoundsException e) {
            // 配列インデックス外の場合
            return null;
        } catch (ClassCastException e) {
            return null;
        }
    }

    /**
     * オペランド評価
     * @param doc JSON
     * @param key キー
     * @param operand オペランド
     * @return 評価値
     */
    private boolean evaluateOperand(NbJSONObject doc, String key, Object operand) {
        //log.fine("evaluateOperand() <start>"
        //            + " key=" + key + " doc=" + doc);
        // 評価対象となる値を取得
        Object value = getValue(doc, key);

        //log.fine("evaluateOperand() value=" + value);

        // Operand null チェック
        // Operand = null は、以下の場合に条件が合致する。これは MongoDBの動作と同じ。
        // 1) 指定したキーが存在しない
        // 2) 指定したキーが存在するが、値が null
        if (operand == null) {
            return (value == NO_FIELD || value == null);
        }

        // 最初に直接比較を試みる
        // (スカラ値および Embedded Document 完全一致)
        if (operand.equals(value)) {
            return true;
        }

        if (operand instanceof NbJSONObject) {
            // 複合 operand 評価
            return evaluateCompositeOperand(doc, key, value, (NbJSONObject) operand);
        } else if (operand instanceof List) {
            // 配列完全一致。これは上記の equals で一致チェックしているので、
            // ここに来た時点で不一致。
            return false;
        } else if (value instanceof List) {
            // 配列内要素評価
            List<Object> values = (List<Object>)value;

            for (Object obj : values) {
                if (obj != null && obj.equals(operand)) return true;
            }
            return false;
        }
        return false;
    }

    /**
     * 複合 (NbJSONObject) オペランド評価。
     * (中に比較演算子を含む NbJSONObject)
     * @param doc JSON
     * @param key キー
     * @param value 値
     * @param operand オペランド
     * @return 評価値
     */
    private boolean evaluateCompositeOperand(NbJSONObject doc, String key,
                Object value, NbJSONObject operand) {
        for (Map.Entry<String,Object> entry : operand.entrySet()) {
            String operator = entry.getKey();
            //log.fine("evaluateCompositeOperand()"
            //+ " operator=" + operator + " operand=" + operand
            //+ " operand.get(" + operator + ")=" + operand.get(operator));
            if (!evaluateOperator(doc, key, value, operator, entry.getValue(), operand)) {
                //log.fine("evaluateCompositeOperand() return false");
                return false;
            }
        }
        //log.fine("evaluateCompositeOperand() return true");
        return true;
    }

    /**
     * 比較演算子評価
     * @param doc JSON
     * @param key キー
     * @param value 値
     * @param operator 演算子
     * @param operatorArg 演算子引数
     * @param parentOperand 親オペランド
     * @return 評価値
     */
    private boolean evaluateOperator(NbJSONObject doc, String key,
            Object value, String operator, Object operatorArg, NbJSONObject parentOperand) {
        /*
         log.fine("evaluateOperator"
                 + " key=" + key);
         log.fine("evaluateOperator"
                 + " value=" + value);
         log.fine("evaluateOperator"
                 + " operator=" + operator);
         log.fine("evaluateOperator"
                 + " operatorArg=" + operatorArg);
         log.fine("evaluateOperator"
                 + " parentOperand=" + parentOperand);
         */
         switch (operator) {
            case "$in":
                return inOperator(value, (List<Object>) operatorArg);

            case "$nin":
                return !inOperator(value, (List<Object>) operatorArg);

            case "$all":
                return allOperator(value, (List<Object>) operatorArg);

            case "$exists":
                return existsOperator(value, (Boolean)operatorArg);

             case "$eq":
                 if (operatorArg == null) {
                     return value == null;
                 }
                 return operatorArg.equals(value);

             case "$ne":
                 if (operatorArg == null) {
                     return !(value == null);
                 }
                 return !operatorArg.equals(value);

            case "$gt":
            case "$gte":
            case "$lte":
            case "$lt":
                return compareOperator(operator, value, operatorArg);

            case "$regex":
                return regexOperator(value, operatorArg, parentOperand);

            case "$options":
                return true; // ignore
            case "$not":
                return !evaluateOperand(doc, key, operatorArg);
            default:
                log.fine("evaluateOperator() ERR unknown operator");
                return false; // unsupported operator
        }
    }

    /**
     * 大小比較
     * @param operator 演算子
     * @param op1 引数1
     * @param op2 引数2
     * @return 評価値
     */
    private boolean compareOperator(String operator, Object op1, Object op2) {
        if (op1 == null) return false;

        double comp;
        if (op1 instanceof String && op2 instanceof String) {
            comp = ((String)op1).compareTo((String)op2);
        } else if (op1 instanceof Number && op2 instanceof Number) {
            comp = ((Number) op1).doubleValue() - ((Number) op2).doubleValue();
        } else {
            return false;
        }

        switch (operator) {
            case "$gt":
                return comp > 0.0;
            case "$gte":
                return comp >= 0.0;
            case "$lte":
                return comp <= 0.0;
            case "$lt":
                return comp < 0.0;
            default:
                return false;
        }
    }

    /**
     * $exists 演算子
     * @param value 値
     * @param exists true なら存在、false なら非存在チェック
     * @return 評価値
     */
    private boolean existsOperator(Object value, Boolean exists) {
        return ((value != NO_FIELD && exists) || (value == NO_FIELD && !exists));
    }

    /**
     * $in 演算子
     * @param value 値
     * @param args 引数
     * @return 評価値
     */
    private boolean inOperator(Object value, List<Object> args) {
        //log.fine("inOperator() <start> value=" + value);
        if (value instanceof List) {
            List<Object> values = (List<Object>)value;
            for (Object v: values) {
                //log.fine("inOperator()"
                //+ " values.get(" + i + ")=" + values.get(i));
                if (inOperator(v, args)) {
                    return true;
                }
            }
            //log.fine("inOperator() <end> return[1] false");
            return false;
        } else {
            for (Object arg : args) {
                //log.fine("inOperator()"
                //+ " args.get(" + i + ")=" + args.get(i));
                if (value == arg || (value != null && value.equals(arg))) {
                    return true;
                }
            }
            //log.fine("inOperator() <end> return[2] false");
            return false;
        }
    }

    /**
     * $all 演算子
     * @param value 値
     * @param args 引数
     * @return 評価値
     */
    private boolean allOperator(Object value, List<Object> args) {
        if (value == null || !(value instanceof List)) return false;

        List<Object> values = (List<Object>)value;

        for (Object arg : args) {
            boolean match = false;
            for (Object v : values) {
                if (arg == v || (arg != null && arg.equals(v))) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                return false;
            }
        }
        return true;
    }

    /**
     * $regex 演算子
     * @param value 検査対象値
     * @param regex 正規表現 (String または Pattern)
     * @param parentOperand 上位オペランド ($options 条件を取得するために必要)
     * @return 評価値
     */
    private boolean regexOperator(Object value, Object regex, NbJSONObject parentOperand) {
        if (value == null || !(value instanceof String)) {
            return false;
        }

        Pattern pattern;
        if (regex instanceof Pattern) {
            pattern = (Pattern)regex;
        } else {
            // $options チェック
            int flags = setOptionsFlg(parentOperand);
            pattern = Pattern.compile((String)regex, flags);
        }

        Matcher matcher = pattern.matcher((String)value);

        return matcher.find();
    }

    private int setOptionsFlg(NbJSONObject parentOperand) {
        int flags = 0;
        if (parentOperand.containsKey("$options")) {
            String options = (String)parentOperand.get("$options");
            if (options.contains("i")) {
                flags |= Pattern.CASE_INSENSITIVE; // | Pattern.UNICODE_CASE;
            }
            if (options.contains("m")) {
                flags |= Pattern.MULTILINE;
            }
            if (options.contains("s")) {
                flags |= Pattern.DOTALL;
            }
            if (options.contains("x")) {
                flags |= Pattern.COMMENTS;
            }
        }
        return flags;
    }
}
