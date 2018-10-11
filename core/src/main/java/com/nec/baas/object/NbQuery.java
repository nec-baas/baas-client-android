/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.core.*;
import com.nec.baas.json.*;
import com.nec.baas.util.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * オブジェクト検索クラス。
 * <p>
 * {@link NbObjectBucket#query(NbQuery, NbCallback)} などで使用する。
 * </p>
 *
 * <p><strong>本クラスのインスタンスはスレッドセーフではない。</strong></p>
 * @since 1.0
 */
@Getter
@Accessors(prefix = "m")
@EqualsAndHashCode
public class NbQuery {
    private static final NbLogger log = NbLogger.getLogger(NbQuery.class);

    private static final String CLAUSE = "clause";
    private static final String LIMIT = "limit";
    private static final String SKIP = "skip";
    private static final String SORT = "sort";
    private static final String DELETE_MARK = "deleteMark";
    private static final String COUNT_QUERY = "countQuery";
    private static final String PROJECTION = "projection";

    /**
     * 検索条件
     */
    private NbClause mClause;

    /**
     * 検索上限数。デフォルトは -1(無制限)
     */
    private int mLimit = -1;

    /**
     * スキップカウント。デフォルトは0。
     */
    private int mSkipCount = 0;

    /**
     * ソート順序
     */
    private List<String> mSortOrders = new ArrayList<>();

    /**
     * 件数取得フラグ (true なら取得する)。デフォルトは false。
     */
    private boolean mCountQuery = false;

    /**
     * 削除取得有無フラグ
     */
    private boolean mDeleteMark = false;

    /**
     * プロジェクション設定
     * @since 7.5.0
     */
    private NbJSONObject mProjection;

    /**
     * Queryクラスのコンストラクタ。
     * @since 1.0
     */
    public NbQuery() {
    }

    /**
     * 検索条件を指定する。
     * clauseにnullを指定した場合、すでに設定済みの検索条件をクリアする。
     * @param clause 指定する検索条件。
     * @see NbClause
     */
    public NbQuery setClause(NbClause clause) {
        mClause = clause;
        return this;
    }

    /**
     * 検索上限数を設定する。
     * -1 を指定した場合は無制限。
     * デフォルト値は -1(無制限)。
     * @param limit 設定する検索上限数。
     * @return this
     * @throws IllegalArgumentException 値が範囲外
     */
    public NbQuery setLimit(int limit) {
        if (limit < -1) {
            throw new IllegalArgumentException("argument is out of range.");
        }
        mLimit = limit;
        return this;
    }

    /**
     * スキップカウントを設定する。
     * スキップカウントの設定範囲は0以上の値。
     * デフォルト値は0。
     * @param skip 設定するスキップカウント数。
     * @return this
     * @throws IllegalArgumentException 値が範囲外
     */
    public NbQuery setSkipCount(int skip) {
        if (skip < 0) {
            throw new IllegalArgumentException("argument is out of range.");
        }
        mSkipCount = skip;
        return this;
    }

    /**
     * ソート順序を取得する。
     * ソート順序はキー名の配列である。降順の場合は各キーの先頭に "-" が付与される。
     * @return ソート順序
     * @since 6.5.0
     */
    public List<String> getSortOrders() {
        return mSortOrders;
    }

    /**
     * ソート順序を指定する。
     * <p>
     * 例:<br>
     * {@code new NbQuery().setSortOrders("name", "-age");}
     * @param orders ソート順序。キーの配列。降順の場合はキーの先頭に "-" を付与する。
     * @return this
     * @since 6.5.0
     */
    public NbQuery setSortOrders(String... orders) {
        return setSortOrders(Arrays.asList(orders)); // immutable
    }

    /**
     * ソート順序を指定する(List指定)。
     * <p>
     * 例:<br>
     * {@code new NbQuery().setSortOrders(Arrays.asList("name", "-age"));}
     * @param orders ソート順序。キーの配列。降順の場合はキーの先頭に "-" を付与する。
     * @return this
     * @since 6.5.0
     */
    public NbQuery setSortOrders(List<String> orders) {
        mSortOrders = orders;
        return this;
    }

    /**
     * ソート順序を追加する。
     * @param key ソート対象フィールド。
     * @param isAsc trueの場合は昇順、falseの場合は降順でソート
     * @return this
     * @since 6.5.0
     */
    public NbQuery addSortOrder(String key, boolean isAsc) {
        if (!isAsc) {
            key = "-" + key; // 降順
        }
        try {
            mSortOrders.add(key);
        } catch (UnsupportedOperationException e) {
            // mSortOrders が immutable な場合は、mutable な array にコピーする
            mSortOrders = new ArrayList<>(mSortOrders);
            mSortOrders.add(key);
        }
        return this;
    }

    /**
     * ソート順序を追加する。
     * @param key ソート対象フィールド。
     * @param isAsc trueの場合は昇順、falseの場合は降順でソート
     * @return this
     * @deprecated 6.5.0 で廃止。{@link #addSortOrder(String, boolean)} または {@link #setSortOrders(String...)} で置き換え。
     */
    @Deprecated
    public NbQuery setSortOrder(String key, boolean isAsc) {
        return addSortOrder(key, isAsc);
    }

    /**
     * ソート順序を取得する。
     * キー名・昇順フラグをエントリとする LinkedHashMap が返る。
     * @return ソート順序
     * @deprecated 6.5.0 で廃止。{@link #getSortOrders} で置き換え。
     */
    @Deprecated
    public LinkedHashMap<String, Boolean> getSortOrder() {
        LinkedHashMap<String, Boolean> orders = new LinkedHashMap<>();
        for (String order : mSortOrders) {
            if (order.startsWith("-")) {
                orders.put(order.substring(1), false);
            } else {
                orders.put(order, true);
            }
        }
        return orders;
    }

    /**
     * 設定された件数取得フラグ(数値)を取得する。
     * @return 件数取得フラグ。取得する場合は 1、そうでなければ 0。
     */
    public int getCountQueryAsNum() {
        return mCountQuery ? 1 : 0;
    }

    /**
     * 検索条件に合致した件数を取得する。
     * デフォルトは false (取得しない)
     * @param countQuery true の場合は件数を取得する。
     * @return this
     */
    public NbQuery setCountQuery(boolean countQuery) {
        mCountQuery = countQuery;
        return this;
    }

    /**
     * 削除データの取得有無を指定する。デフォルトは false。
     * @param isDeleteMark trueの場合は削除データ含む。falseの場合は削除データ除く。
     * @return this
     */
    public NbQuery setDeleteMark(boolean isDeleteMark) {
        mDeleteMark = isDeleteMark;
        return this;
    }

    /**
     * プロジェクションを設定する。<br>
     * projectionJsonにnull、又は空オブジェクトを指定した場合、すでに設定済みのプロジェクション設定をクリアする。
     * @param projectionJson プロジェクション設定
     * @since 7.5.0
     * @return this
     */
    public NbQuery setProjection(NbJSONObject projectionJson) {
        mProjection = projectionJson;
        return this;
    }

    /**
     * Query から JSON に変換する
     * @return JSON
     */
    public NbJSONObject toJson() {
        NbJSONObject queryJson = new NbJSONObject();
        if (mClause != null) {
            NbJSONObject jsonClause = mClause.getConditions();
            queryJson.put(CLAUSE, jsonClause);
        }

        queryJson.put(LIMIT, mLimit);
        queryJson.put(SKIP, mSkipCount);

        NbJSONArray<String> sortArray = new NbJSONArray<>(mSortOrders);
        queryJson.put(SORT, sortArray);

        queryJson.put(DELETE_MARK, mDeleteMark);
        queryJson.put(COUNT_QUERY, mCountQuery);

        if (mProjection != null) {
            queryJson.put(PROJECTION, mProjection);
        }

        return queryJson;
    }

    /**
     * NbJSONObject から NbQuery に変換する。
     * 例外が発生した場合は null を返す。
     * @param queryJson NbJSONObject
     * @return NbQuery
     */
    public static NbQuery fromJson(NbJSONObject queryJson) {
        NbQuery query = new NbQuery();

        try {
            if (queryJson.containsKey(LIMIT)) {
                query.setLimit(queryJson.getInt(LIMIT));
            }

            if (queryJson.containsKey(SKIP)) {
                query.setSkipCount(queryJson.getInt(SKIP));
            }

            if (queryJson.containsKey(SORT)) {
                NbJSONArray sortArray = queryJson.getJSONArray(SORT);
                if (sortArray != null) {
                    for (Object aOrder : sortArray) {
                        String key;
                        boolean isAsc;
                        if (aOrder instanceof NbJSONObject) {
                            // [{"name": true}, {"age": false}] 形式 (旧ブリッジ形式)
                            NbJSONObject order = (NbJSONObject) aOrder;
                            key = order.keySet().iterator().next();
                            isAsc = order.getBoolean(key);
                        } else {
                            // ["name", "-age"] 形式 (新形式)
                            key = aOrder.toString();
                            isAsc = true;
                            if (key.startsWith("-")) {
                                isAsc = false;
                                key = key.substring(1);
                            }
                        }
                        query.addSortOrder(key, isAsc);
                    }
                }
            }

            if (queryJson.containsKey(CLAUSE)) {
                NbJSONObject clauseJson = queryJson.getJSONObject(CLAUSE);
                NbClause clause = NbClause.fromJSONObject(clauseJson);
                query.setClause(clause);
            }

            if (queryJson.containsKey(DELETE_MARK)) {
                query.setDeleteMark(queryJson.getBoolean(DELETE_MARK));
            }

            if (queryJson.containsKey(COUNT_QUERY)) {
                query.setCountQuery(queryJson.getBoolean(COUNT_QUERY));
            }

            if (queryJson.containsKey(PROJECTION)) {
                NbJSONObject projectionJson = queryJson.getJSONObject(PROJECTION);
                query.setProjection(projectionJson);
            }
            return query;

        } catch (Exception e) {
            //e.printStackTrace();
            log.warning("fromJson(), e=" + e.toString());
            return null;
        }
    }
}