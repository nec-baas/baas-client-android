/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU Cache。
 *
 * <p><strong>本クラスのインスタンスはスレッドセーフではない。</strong></p>
 * @since 1.2.0
 */
public class NbLruCache<K, V> {
    private Map<K, V> mMap;

    protected int mMaxSize;

    protected NbLruCache() {}

    /**
     * コンストラクタ
     * @param maxSize キャッシュの最大件数。
     */
    public NbLruCache(int maxSize) {
        this.mMaxSize = maxSize;

        mMap = new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > mMaxSize;
            }
        };
    }

    /**
     * キャッシュエントリ最大数を返す
     * @return 最大数
     */
    public int getMaxSize() {
        return mMaxSize;
    }

    /**
     * キャッシュエントリ最大数を設定する。
     * エントリ数を減らしても、すぐにキャッシュが縮むわけではない。
     * @param maxSize 最大数
     */
    public synchronized void setMaxSize(int maxSize) {
        mMaxSize = maxSize;
    }

    /**
     * キャッシュサイズを取得する
     * @return キャッシュサイズ
     */
    public int getSize() {
        return mMap.size();
    }

    /**
     * キャッシュを全クリアする
     */
    public synchronized void clear() {
        mMap.clear();
    }


    /**
     * Key - Value ペアをセットする。
     * @param key キー
     * @param value 値
     * @return セットする前の古い値。存在しない場合は null。
     */
    public synchronized V put(K key, V value) {
        return mMap.put(key, value);
    }

    /**
     * Key に対応する Value を取得する。
     * エントリが存在しない場合、あるいはすでに値が GC で抹消されている場合、
     * null が返却される。
     * @param key キー
     * @return 値
     */
    public synchronized V get(K key) {
        return mMap.get(key);
    }
}
