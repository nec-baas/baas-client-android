/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.json.*;

import java.util.Map;

import lombok.Getter;
import lombok.NonNull;


/**
 * オブジェクト検索条件クラス。
 * <p>
 * {@link NbQuery} の検索条件として使用する。
 * MongoDB のクエリ演算子と機能的にほぼ等価。
 * 各APIのObject型の引数に対してプリミティブ型の配列を指定した場合は例外が発生する。
 * </p>
 * 使用例1:
 *
 * <pre>
 * {@code
 * // AND条件は連結記述が可能
 * NbClause clause = new NbClause().equals("key1", "xyz").greaterThan("key2", 100);
 * NbQuery query = new NbQuery().setClause(clause);
 * }
 * </pre>
 *
 * 使用例2:
 * <pre>
 * {@code
 * // OR 条件の場合
 * NbClause clause = new NbClause().or(
 *     new NbClause().equals("key1", "xyz"),
 *     new NbClause().greaterThan("key2", 100),
 *     new NbClause().in("key3", "A", "B", "C");
 * );
 * NbQuery query = new NbQuery().setClause(clause);
 * }
 * </pre>
 * <p><strong>本クラスのインスタンスはスレッドセーフではない。</strong></p>
 * @since 1.0
 */
public class NbClause {
    @Getter
    private NbJSONObject conditions = new NbJSONObject();

    /**
     * 大文字と小文字を区別しない。
     * regex()のオプションで使用する。
     * @see NbClause#regex(String, String, int)
     */
    public static final int IGNORE_CASE = 0x0001;

    /**
     * 文字列を複数行として扱う。
     * 正規表現の ^, $ は各行の行頭・行末にマッチする。
     * regex()のオプションで使用する。
     * @see NbClause#regex(String, String, int)
     */
    public static final int MULTILINE = 0x0010;

    /**
     * 拡張正規表現を使用する。
     * regex()のオプションで使用する。
     * @see NbClause#regex(String, String, int)
     */
    public static final int EXTENDED = 0x0100;

    /**
     * 正規表現の "." を改行(LF)にもマッチさせる。
     * regex()のオプションで使用する。
     * @see NbClause#regex(String, String, int)
     */
    public static final int DOT_MATCH_NEWLINE = 0x1000;

    /**
     * コンストラクタ
     */
    public NbClause() {
    }

    private NbClause(@NonNull NbJSONObject conditions) {
        this.conditions = conditions;
    }

    /**
     * MongoDB クエリ同等の JSONオブジェクトから NbClause を生成する
     * @param conditions JSONオブジェクト
     * @return NbClause
     */
    public static NbClause fromJSONObject(NbJSONObject conditions) {
        return new NbClause(conditions);
    }

    /**
     * MongoDB クエリ同等のJSON文字列から NbClause を生成する
     * @param jsonString JSON文字列
     * @return NbClause
     */
    public static NbClause fromJSONString(String jsonString) {
        return fromJSONObject(NbJSONParser.parse(jsonString));
    }

    /**
     * NbJSONObject 形式のクエリを返却する。
     * 内部 NbJSONObject のコピーが返却される。
     * @return NbJSONObjectのコピー
     */
    public NbJSONObject toJSONObject() {
        return (NbJSONObject)conditions.clone();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof NbClause)) {
            return false;
        }
        NbClause that = (NbClause)obj;
        return this.conditions.equals(that.conditions);
    }

    /**
     * keyで指定されたフィールドの値が、valueで指定された値と等しいこと。
     * @param key 演算対象のフィールド
     * @param value 閾値。
     */
    public NbClause equals(String key, Object value) {
        if (value instanceof NbJSONObject) {
            // value が JSON Object の場合は $eq 演算子を使用する。
            // JSON Object 直比較にすると演算子使用と混同しやすいため。
            NbJSONObject eq = new NbJSONObject();
            eq.put("$eq", value);
            conditions.put(key, eq);
        } else {
            conditions.put(key, value);
        }
        return this;
    }

    /**
     * 条件マージ。
     * <p>指定キーに対して条件がすでに存在している場合はマージする。
     * ない場合は新規追加。
     * @param key キー
     * @param condition 条件
     */
    private void mergePut(String key, NbJSONObject condition) {
        if (!conditions.containsKey(key)) {
            conditions.put(key, condition);
            return;
        }

        try {
            NbJSONObject targetConditions = conditions.getJSONObject(key);
            for (Map.Entry<String, Object> entry : condition.entrySet()) {
                targetConditions.put(entry.getKey(), entry.getValue());
            }
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException("Can't merge conditions");
        }
    }

    /**
     * keyで指定されたフィールドの値が、valueで指定された値と等しくないこと。
     * @param key 演算対象のフィールド
     * @param value 閾値。
     */
    public NbClause notEqual(String key, Object value) {
        return addSimpleOp("$ne", key, value);
    }

    /**
     * keyで指定されたフィールドの値が、valueで指定された値より小さいこと。
     * @param key 演算対象のフィールド
     * @param value 閾値。
     */
    public NbClause lessThan(String key, Object value) {
        return addSimpleOp("$lt", key, value);
    }

    /**
     * keyで指定されたフィールドの値が、valueで指定された値以下であること。
     * @param key 演算対象のフィールド
     * @param value 閾値。
     */
    public NbClause lessThanOrEqual(String key, Object value) {
        return addSimpleOp("$lte", key, value);
    }

    /**
     * keyで指定されたフィールドの値が、valueで指定された値より大きいこと。
     * @param key 演算対象のフィールド
     * @param value 閾値。
     */
    public NbClause greaterThan(String key, Object value) {
        return addSimpleOp("$gt", key, value);
    }

    /**
     * keyで指定されたフィールドの値が、valueで指定された値以上であること。
     * @param key 演算対象のフィールド
     * @param value 閾値。
     */
    public NbClause greaterThanOrEqual(String key, Object value) {
        return addSimpleOp("$gte", key, value);
    }

    private NbClause addSimpleOp(String op, String key, Object value) {
        NbJSONObject j = new NbJSONObject();
        j.put(op, value);
        mergePut(key, j);
        return this;
    }

    private NbClause addSimpleOp(String op, String key, Object[] values) {
        NbJSONObject j = new NbJSONObject();
        j.put(op, values);
        mergePut(key, j);
        return this;
    }

    /**
     * keyで指定されたフィールドの値が、valuesで指定された値の何れかと一致すること。
     * @param key 演算対象のフィールド
     * @param values 比較する値のリスト。
     */
    public NbClause in(String key, Object... values) {
        return addSimpleOp("$in", key, values);
    }

    /**
     * keyで指定されたフィールドの値が、valuesで指定された値の全てと一致すること。
     * @param key 演算対象のフィールド
     * @param values 比較する値のリスト。
     */
    public NbClause all(String key, Object... values) {
        return addSimpleOp("$all", key, values);
    }

    /**
     * keyで指定されたフィールドの値が存在するorしないこと。
     * @param key 演算対象のフィールド
     * @param isExist trueの場合存在すること、falseの場合存在しないこと。
     * @deprecated {@link #exists(String)} で置き換え
     */
    @Deprecated
    public NbClause exists(String key, Boolean isExist) {
        if (isExist) {
            return exists(key);
        } else {
            return notExist(key);
        }
    }

    /**
     * keyで指定されたフィールドの値が存在すること。
     * @param key 演算対象のフィールド
     */
    public NbClause exists(String key) {
        return addSimpleOp("$exists", key, true);
    }

    /**
     * keyで指定されたフィールドの値が存在しないこと。
     * @param key 演算対象のフィールド
     */
    public NbClause notExist(String key) {
        return addSimpleOp("$exists", key, false);
    }

    /**
     * keyで指定されたフィールドの値が正規表現で与えられた表記と一致すること。<br>
     * optionを複数指定する場合は|でフラグを結合する。
     * @param key 演算対象のフィールド
     * @param expression 正規表現
     * @param option オプション
     * @see NbClause#IGNORE_CASE
     * @see NbClause#MULTILINE
     * @see NbClause#EXTENDED
     * @see NbClause#DOT_MATCH_NEWLINE
     */
    public NbClause regex(String key, String expression, int option) {
        NbJSONObject regex = new NbJSONObject();
        regex.put("$regex", expression);

        // オプションフラグチェック
        String optionStr = "";
        if ((option & IGNORE_CASE) == IGNORE_CASE) {
            optionStr += "i";
        }
        if ((option & MULTILINE) == MULTILINE) {
            optionStr += "m";
        }
        if ((option & EXTENDED) == EXTENDED) {
            optionStr += "x";
        }
        if ((option & DOT_MATCH_NEWLINE) == DOT_MATCH_NEWLINE) {
            optionStr += "s";
        }

        if (!optionStr.equals("")) {
            regex.put("$options", optionStr);
        }

        mergePut(key, regex);
        return this;
    }

    /**
     * 引数で与えられた検索条件と保持している検索条件でandをとる。
     * @param clauses 結合する検索条件
     */
    public NbClause and(NbClause... clauses) {
        return concatClauses("$and", clauses);
    }

    /**
     * 引数で与えられた検索条件と保持している検索条件でorをとる。
     * @param clauses 結合する検索条件
     */
    public NbClause or(NbClause... clauses) {
        return concatClauses("$or", clauses);
    }

    /**
     * 論理演算連結
     * @param op オペレータ ($and, $or など)
     * @param clauses 連結する clause
     * @return this
     */
    private NbClause concatClauses(String op, NbClause[] clauses) {
        NbJSONArray<NbJSONObject> list = new NbJSONArray<>();
        if (!conditions.isEmpty()) {
            list.add(conditions);
        }
        if (clauses != null) {
            for (NbClause clause : clauses) {
                list.add(clause.getConditions());
            }
        }
        NbJSONObject cond = new NbJSONObject();
        cond.put(op, list);
        conditions = cond;
        return this;
    }

    /**
     * 保持している条件をすべて反転(not)する。
     * @return this
     * @deprecated {@link #not(String)} で置き換え。
     */
    @Deprecated
    public NbClause not() {
        NbJSONObject newConditions = new NbJSONObject();

        for (Map.Entry<String,Object> entry : conditions.entrySet()) {
            NbJSONObject not = new NbJSONObject();
            not.put("$not", entry.getValue());
            newConditions.put(entry.getKey(), not);
        }

        conditions = newConditions;
        return this;
    }

    /**
     * 指定したキーの条件を反転($not)する
     * @param key キー
     * @return this
     */
    public NbClause not(String key) {
        if (!conditions.containsKey(key)) {
            throw new IllegalArgumentException("No such key: " + key);
        }

        Object value = conditions.get(key);
        NbJSONObject orgCondition;
        if (value instanceof NbJSONObject) {
            // Note: JSON Object との直比較はサポートしない
            // JSON Object 直比較をしたい場合は必ず $eq 演算子を使うこと
            orgCondition = (NbJSONObject)value;
        } else {
            // クエリ値が JSON Object でない場合は、$eq 演算子入りの
            // JSON Object に置換する
            orgCondition = new NbJSONObject();
            orgCondition.put("$eq", value);
        }

        NbJSONObject not = new NbJSONObject();
        not.put("$not", orgCondition);
        conditions.put(key, not);

        return this;
    }
}
