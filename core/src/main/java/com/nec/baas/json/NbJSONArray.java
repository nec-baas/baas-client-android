/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * JSON Array。
 *
 * <p><strong>本クラスのインスタンスはスレッドセーフではない。</strong></p>
 * @since 1.2.0
 */
public class NbJSONArray<T> extends ArrayList<T> implements Cloneable {
    private boolean mIsImmutable = false;

    /**
     * 空の Array を作成します。
     */
    public NbJSONArray() {
        super();
    }

    /**
     * 指定された初期容量で Array を作成します。
     * @param initialCapacity 初期容量
     */
    public NbJSONArray(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * コンストラクタ。List の内容を shallow copy する (deep copy ではない)。
     * @param list List
     */
    public NbJSONArray(List<T> list) {
        super(list);
    }

    /**
     * このオブジェクトを Immutable にする。
     * 以後、このオブジェクトに対する書き込みはエラーとなる。
     */
    public synchronized void setImmutable() {
        if (!mIsImmutable) {
            mIsImmutable = true;
            for (Object obj : this) {
                if (obj instanceof NbJSONObject) {
                    ((NbJSONObject)obj).setImmutable();
                } else if (obj instanceof NbJSONArray) {
                    ((NbJSONArray)obj).setImmutable();
                }
                // TODO: Map, List が直接入っている場合の処理
            }
        }
    }

    private void checkMutable() {
        if (mIsImmutable) {
            throw new UnsupportedOperationException("Immutable array can't be modified!");
        }
    }

    @Override
    public boolean add(T obj) {
        checkMutable();
        return super.add(obj);
    }

    @Override
    public void add(int index, T obj) {
        checkMutable();
        super.add(index, obj);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        checkMutable();
        return super.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        checkMutable();
        return super.addAll(index, c);
    }

    @Override
    public void clear() {
        checkMutable();
        super.clear();
    }

    @Override
    public T remove(int index) {
        checkMutable();
        return super.remove(index);
    }

    @Override
    public boolean remove(Object obj) {
        checkMutable();
        return super.remove(obj);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        checkMutable();
        return super.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        checkMutable();
        return super.retainAll(c);
    }

    @Override
    public T set(int index, T obj) {
        checkMutable();
        return super.set(index, obj);
    }

    /**
     * deep copy を返す。
     * @return コピーされた JSONArray
     */
    @Override
    public Object clone() {
        return deepCopyOf((List<Object>)this);
    }

    /**
     * Deep Copy を返す。
     * @param src コピー元 List/JSONArray
     * @return コピーされた JSONArray
     */
    public static NbJSONArray deepCopyOf(List<Object> src) {
        NbJSONArray dst = new NbJSONArray(src.size());

        for (Object obj : src) {
            if (obj instanceof Map) {
                dst.add(NbJSONObject.deepCopyOf((Map<String, Object>) obj));
            }
            else if (obj instanceof List) {
                dst.add(deepCopyOf((List<Object>) obj));
            }
            else {
                dst.add(obj);
            }
        }

        return dst;
    }
}
