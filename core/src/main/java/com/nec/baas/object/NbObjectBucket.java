/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.object;

import com.nec.baas.core.*;
import com.nec.baas.json.NbJSONArray;
import com.nec.baas.json.NbJSONObject;

import java.util.List;

/**
 * オブジェクトバケット
 * @since 1.0
 */
public interface NbObjectBucket extends NbBaseBucket<NbObjectBucket>, NbBaseOfflineBucket, NbOfflineObjectBucket {
    /**
     * このバケットに所属する新しいオブジェクトを生成する。<br>
     * 生成するオブジェクトのモードは、バケットと同じモードとなる。
     * @return オブジェクト
     */
    NbObject newObject();

    /**
     * オブジェクトIDを指定してバケットからNbObjectを検索する。
     * <p>
     * @deprecated {@link #getObject(String, NbCallback)} で置き換え
     */
    @Deprecated
    void getObject(final String objectId, final NbObjectCallback callback);

    /**
     * オブジェクトIDを指定してバケットからNbObjectを検索する。
     * <p>
     * 取得したオブジェクトは callback に渡される。該当するオブジェクトが存在しなかった場合は、
     * statusCode = 404 (Not Found) で {@link NbCallback#onFailure(int, NbErrorInfo)} が呼び出される。
     * <p>
     *
     * 対象バケットと対象オブジェクトのread権限が必要となる。<p>
     *
     * オンラインモードの場合、サーバからデータの取得を実行する。<br>
     * レプリカ・ローカルモードの場合、オフライン用データベースからデータの取得を実行する。<br>
     * @param objectId 取得するオブジェクトのid。
     * @param callback NbObjectを受け取るためのコールバック。
     */
    void getObject(final String objectId, final NbCallback<NbObject> callback);

    /**
     * バケットに対しクエリを行う。
     * <p>
     * @deprecated {@link #queryWithCount(NbQuery, NbCountCallback)} または {@link #query(NbQuery, NbCallback)} で置き換え。
     */
    @Deprecated
    void query(final NbQuery query, final NbObjectCallback callback);

    /**
     * バケットに対しクエリを行う(全件数取得付き)。
     * <p>
     * 指定した検索条件でバケットに対しクエリを実行する。
     * <p>
     * オンラインモードの場合、サーバに対しクエリを実行する。
     * レプリカ・ローカルモードの場合、オフライン用データベースに対しクエリを実行する。
     * <p>
     * <p>
     * queryにlimitを指定しない場合、取得件数はデフォルトの100件で動作する。
     * queryがnullの場合も条件無しのデフォルト件数(100件)で動作する。
     * <p>
     * {@link NbQuery#setCountQuery(boolean)} を設定した場合、
     * callback のcount には検索条件に一致した全データ数を返却する。queryにskip/limitを指定した場合、
     * コールバック引数のcountは返却データ数と一致しないので注意すること。
     * <p>
     * また、本メソッドの制限事項として、返すオブジェクトのレンジも、実際のレンジとは
     * 異なる場合がありうる。これは検索レンジ外のデータに関してクライアントでデータが
     * 削除されていることがあるためである。
     * @param query クエリの検索条件。
     * @param callback クエリ結果を受け取るコールバック。
     * @see NbQuery
     * @since 6.5.0
     */
    void queryWithCount(final NbQuery query, final NbCountCallback<List<NbObject>> callback);

    /**
     * バケットに対しクエリを行う(全件数取得無し)。
     * <p>
     * <p>
     * 指定した検索条件でバケットに対しクエリを実行する。
     * <p>
     * オンラインモードの場合、サーバに対しクエリを実行する。
     * レプリカ・ローカルモードの場合、オフライン用データベースに対しクエリを実行する。
     * <p>
     * <p>
     * queryにlimitを指定しない場合、取得件数はデフォルトの100件で動作する。
     * queryがnullの場合も条件無しのデフォルト件数(100件)で動作する。
     * <p>
     * 本APIでは全件数は取得できない。全件数を取得したい場合は {@link #queryWithCount(NbQuery, NbCountCallback)}
     * を使用すること。
     * <p>
     * また、本メソッドの制限事項として、返すオブジェクトのレンジも、実際のレンジとは
     * 異なる場合がありうる。これは検索レンジ外のデータに関してクライアントでデータが
     * 削除されていることがあるためである。
     * @param query クエリの検索条件。
     * @param callback クエリ結果を受け取るコールバック。
     * @see NbQuery
     * @since 6.5.0
     */
    void query(final NbQuery query, final NbCallback<List<NbObject>> callback);

    /**
     * バケットの設定を保存する。<p/>
     * ROOTバケットと本バケットのupdate権限が必要となる。<br>
     * ACLの変更を行う際は本バケットのadmin権限が必要となる。<br>
     * 尚、本メソッドはレプリカモードでは使用はできない。<br>
     * @param callback　保存したバケットを受け取るコールバック。
     */
    void save(final NbCallback<NbObjectBucket> callback);

    /**
     * 集計(Aggregation)を実行する。
     * <p>
     * 集計結果は JSONArray で返される。<br>
     * 尚、本メソッドはオンラインモード以外のバケットモードでは使用はできない。<br>
     * @param pipeline Aggregation Pipeline JSON配列
     * @param options オプション
     * @param callback 実行結果を受け取るコールバック
     * @since 7.0.0
     */
    void aggregate(final NbJSONArray pipeline, final NbJSONObject options, final NbCallback<NbJSONArray> callback);

    /**
     * バッチ処理要求データを作成する。<br>
     *
     * 指定のtypeに合わせてobjectからバッチ処理要求用のNbJSONObjectを作成する。<br>
     *
     * INSERT指定時は、オブジェクトの新規追加要求を作成する。<br>
     * UPDATE指定時は、オブジェクトの上書き更新要求を作成する。<br>
     * DELETE指定時は、オブジェクトの削除要求を作成する。<br>
     * 尚、本メソッドはオンラインモード以外のバケットモードでは使用はできない。<br>
     * @param object 要求情報元オブジェクトデータ
     * @param type 要求種別
     * @return バッチ処理要求データ
     * @since 3.0.0
     */
    NbJSONObject createBatchRequest(final NbObject object, NbBatchOperationType type);

    /**
     * バッチ処理要求を実行する。
     * <p>
     * バッチ処理要求データを用いてサーバへのバッチ処理要求を行う。
     * バッチ処理要求データは、NbJSONArrayに格納すること。
     * <p>
     * 結果は JSONArray で返される。格納される各データの詳細は REST API リファレンスを参照。
     * dataキーにはNbObjectインスタンスが格納される
     * <p>
     * リクエストトークンを指定することで実行済みバッチ処理の結果を受けることが可能。<br>
     * 尚、本メソッドはオンラインモード以外のバケットモードでは使用はできない。<br>
     * @param batchList バッチ処理要求データ一覧
     * @param requestToken リクエストトークン
     * @param callback 実行結果を受け取るコールバック
     * @since 3.0.0
     */
    void executeBatchOperation(final NbJSONArray batchList, final String requestToken, final NbCallback<NbJSONArray> callback);

    /**
     * バッチ処理要求を実行する。
     * <p>
     * バッチ処理要求データを用いてサーバへのバッチ処理要求を行う。
     * バッチ処理要求データは、REST APIリファレンスに従った記載で格納すること。
     * <p>
     * 結果は JSONArray で返される。格納される各データの詳細は REST API リファレンスを参照。
     * dataキーにはNbObjectインスタンスが格納される
     * <p>
     * リクエストトークンを指定することで実行済みバッチ処理の結果を受けることが可能。
     * 尚、本メソッドはオンラインモード以外のバケットモードでは使用はできない。
     * @param requests バッチ処理要求データ一覧
     * @param requestToken リクエストトークン
     * @param callback 実行結果を受け取るコールバック
     * @since 3.0.0
     */
    void executeBatchOperation(final NbJSONObject requests, final String requestToken, final NbCallback<NbJSONArray> callback);

    /**
     * 複数オブジェクトを一括削除する。オンラインバケットでのみ使用可能。
     * オブジェクトは論理削除される。
     * @param query クエリ条件
     * @param callback コールバック。成功時は削除数が返却される。
     * @since 6.5.0
     */
    void deleteObjects(final NbQuery query, final NbCallback<Integer> callback);

    /**
     * 複数オブジェクトを一括削除する。オンラインバケットでのみ使用可能。
     * @param query クエリ条件
     * @param softDelete true にした場合は論理削除、false 時は物理削除
     * @param callback コールバック。成功時は削除数が返却される。
     * @since 6.5.0
     */
    void deleteObjects(final NbQuery query, final boolean softDelete, final NbCallback<Integer> callback);
}

