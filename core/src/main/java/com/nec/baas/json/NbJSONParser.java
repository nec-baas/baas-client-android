/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.json;

import com.fasterxml.jackson.core.JsonParseException;
import com.nec.baas.json.internal.*;
import com.nec.baas.util.*;

import java.io.IOException;

/**
 * JSONパーサ。
 *
 * <p>本クラスの全メソッドはスレッドセーフである。</p>
 * @since 1.2.0
 */
public class NbJSONParser {
    private static final NbLogger log = NbLogger.getLogger(NbJSONParser.class);

    /**
     * JSON キャッシュ有効化フラグ
     */
    public static final boolean ENABLE_JSON_CACHE = true;

    /**
     * JSON キャッシュエントリ最大数。
     */
    public static final int DEFAULT_JSON_CACHE_SIZE = 1000;

    /**
     * JSON キャッシュ。
     *
     * <p>
     *     JSON 文字列をキーに、JSON Map をキャッシュする。
     *     古いエントリは自動的に削除される。アルゴリズムは LRU。
     * </p>
     */
    private static NbLruCache<String, NbJSONObject> sJsonCache = new NbLruCache<>(DEFAULT_JSON_CACHE_SIZE);

    /**
     * JSON キャッシュサイズ変更
     */
    public static synchronized void setJsonCacheSize(int size) {
        sJsonCache.setMaxSize(size);
    }

    /**
     * JSON キャッシュを全クリアする
     */
    public static synchronized void clearJsonCache() {
        sJsonCache.clear();
    }

    /**
     * JSON文字列(JSON Object)をパースする
     * @param jsonString JSON文字列
     * @return JSONオブジェクト。パース失敗時は null。
     */
    public static NbJSONObject parse(String jsonString) {
        try {
            return parseWithException(jsonString);
        } catch (Exception e) {
            //e.printStackTrace();
            log.warning("NbJSONObject.parse: {0}", e.getMessage());
            return null;
        }
    }

    /**
     * JSON文字列(JSON Object)をパースする。パース失敗時は例外をスローする。
     * @param jsonString JSON文字列
     * @return JSONオブジェクト
     * @throws JsonParseException パースエラー
     * @throws IOException
     */
    public static NbJSONObject parseWithException(String jsonString) throws IOException, JsonParseException {
        return NbJSONParserJackson.parseWithException(jsonString);
    }

    /**
     * JSON文字列を NbJSONObject に変換する (キャッシュ付き)
     *
     * <p>
     *     変換した NbJSONObject は、JSON 文字列をキーとしてキャッシュされる。
     *     キャッシュされる最大エントリ数は {@link #DEFAULT_JSON_CACHE_SIZE} で定義される。
     *     古いエントリは LRU で自動的に削除される。
     * </p>
     * <p>
     *     mutable が false の場合、返却される NbJSONObject は不変であり、
     *     変更操作を行うと {@link UnsupportedOperationException} がスローされる。
     *     mutable が true の場合は、キャッシュの deep コピーが返却される。
     * </p>
     *
     * @param jsonText JSON文字列
     * @param mutable true を指定すると Mutable な NbJSONObject が返る(コピー発生する)。false の場合は Immultable な NbJSONObject が返る。
     * @return JSON Map。パースエラー時は null。
     */
    public static synchronized NbJSONObject parseWithCache(String jsonText, boolean mutable) {
        if (!ENABLE_JSON_CACHE) {
            return NbJSONParser.parse(jsonText);
        }

        NbJSONObject json = sJsonCache.get(jsonText);
        if (json == null) {
            // キャッシュなし。パース実行。
            json = NbJSONParser.parse(jsonText);
            if (json == null) {
                // パースエラー
                return null;
            }

            // Immutable にしてキャッシュ格納する。
            json.setImmutable();
            sJsonCache.put(jsonText, json);
        }
        return mutable ? (NbJSONObject)json.clone() : json;
    }
}
