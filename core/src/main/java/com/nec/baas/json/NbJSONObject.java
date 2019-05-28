/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.json;

import lombok.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSONオブジェクト。java.util.Map インタフェースをサポートする。
 * Key, Value ペアは順序付けされる。
 *
 * <p><strong>本クラスのインスタンスはスレッドセーフではない。</strong></p>
 * @since 1.2.0
 */
// #9554: JSONの順序保証のため、継承元を HashMap から LinkedHashMap に変更する
public class NbJSONObject extends LinkedHashMap<String,Object> implements Cloneable, Serializable {
    private boolean mIsImmutable = false;

    /**
     * 空の NbJSONObject を作成します。
     */
    public NbJSONObject() {
        super();
    }

    /**
     * 指定された初期容量で NbJSONObject を作成します。
     * @param initialCapacity 初期容量
     */
    public NbJSONObject(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * コンストラクタ。
     * コピー元マップの内容が shallow copy される (deep copy ではない)
     * @param map コピー元マップ (NbJSONObject も可)
     */
    public NbJSONObject(Map<String, ?> map) {
        super(map);
    }

    /**
     * このオブジェクトが Immutable かどうかを返す。
     * Immutable の場合、書き込み操作はエラーとなる。
     * @return Immutableであれば true
     */
    public boolean isImmutable() {
        return mIsImmutable;
    }

    /**
     * このオブジェクトを Immutable にする。
     * 以後、このオブジェクトに対する書き込みはエラーとなる。
     */
    public synchronized void setImmutable() {
        if (!mIsImmutable) {
            mIsImmutable = true;

            for (Map.Entry<String,Object> entry : this.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof NbJSONObject) {
                    ((NbJSONObject) value).setImmutable();
                }
                else if (value instanceof NbJSONArray) {
                    ((NbJSONArray)value).setImmutable();
                }
                // TODO: Map, List が直接入っている場合の処理
            }
        }
    }

    private void checkMutable() {
        if (mIsImmutable) {
            throw new UnsupportedOperationException("Immutable object can't be modified");
        }
    }

    /**
     * {@inheritDoc}
     * 本オブジェクトが Immutable な場合、UnsupportedOperationException がスローされる。
     * valueにプリミティブ型配列が指定された場合、IllegalArgumentException がスローされる。
     * @param key キー
     * @param value 値
     * @return 以前の値
     */
    @Override
    public Object put(@NonNull String key, Object value) {
        //assert(!(value instanceof NbSyncState)); // TODO: NbSyncState を誤って入れないように

        checkMutable();
        return super.put(key, toList(value));
    }

    /**
     * 指定キーに値を代入する。put() と異なり、返り値は this となる。
     * <p>
     * 本オブジェクトが Immutable な場合、UnsupportedOperationException がスローされる。
     * valueにプリミティブ型配列が指定された場合、IllegalArgumentException がスローされる。
     * @param key キー
     * @param value 値
     * @return this
     */
    public NbJSONObject append(@NonNull String key, Object value) {
        this.put(key, value);
        return this;
    }

    /**
     * 引数value内に存在する配列を再帰的にListに変換する。
     * value内にプリミティブ型配列が存在した場合、IllegalArgumentException がスローされる。
     * @param value 変換したいデータ
     * @return 配列がリストに置き換わったデータ
     */
    private Object toList(Object value) {
        // プリミティブ型の配列は非サポート
        if (isPrimitiveArray(value)) {
            throw new IllegalArgumentException("Array of primitives unsupported.");
        } else if (value instanceof Object[]) {
            // オブジェクト型の配列の中もプリミティブ型配列があるかチェック
            List<Object> valueList = new ArrayList<>(Arrays.asList((Object[]) value));
            for (int i = 0; i < valueList.size(); i++) {
                Object element = valueList.get(i);
                // Object配列内の配列はObject, Primitive問わずtoList()に渡して、
                // Primitiveの場合はそちら側で例外を投げさせる
                if (element != null && element.getClass().isArray()) {
                    valueList.set(i, toList(element));
                }
            }
            return valueList;
        }
        return value;
    }

    /**
     * プリミティブ型の配列かどうかを返す
     * @param value 判定したいインスタンス
     * @return プリミティブ型の配列 - true, それ以外 - false
     */
    private boolean isPrimitiveArray(Object value) {
        return (value instanceof boolean[] ||
                value instanceof byte[] ||
                value instanceof char[] ||
                value instanceof short[] ||
                value instanceof int[] ||
                value instanceof long[] ||
                value instanceof float[] ||
                value instanceof double[]);
    }

    /*
    public Object put(String key, NbSyncState state) {
        throw new IllegalArgumentException("Invalid type");
    }
    */

    @Override
    public void putAll(Map<? extends String, ?> map) {
        checkMutable();
        super.putAll(map);
    }

    @Override
    public Object remove(Object key) {
        checkMutable();
        return super.remove(key);
    }

    @Override
    public void clear() {
        checkMutable();
        super.clear();
    }

    /**
     * Immutable インスタンスを返す。
     * 本オブジェクトが Immutable な場合、コピーせずに this を返す。
     * Immutable でない場合は、コピーを作り Immutable にして返す。
     * @return Immutableコピー。
     */
    public NbJSONObject getImmutableInstance() {
        if (mIsImmutable) {
            return this;
        } else {
            NbJSONObject copy = (NbJSONObject)clone();
            copy.setImmutable();
            return copy;
        }
    }

    /**
     * Mutable インスタンスを返す。
     * 本インスタンスが Mutable な場合、コピーせずに this を返す。
     * Immutable な場合は、コピーを作って返す。
     * @return Mutableインスタンス
     */
    public NbJSONObject getMutableInstance() {
        if (mIsImmutable) {
            return (NbJSONObject)clone();
        } else {
            return this;
        }
    }

    /**
     * deep copy を返す。コピーは Immutable ではない。
     * @return コピーされた NbJSONObject
     */
    @Override
    public Object clone() {
        return deepCopyOf(this);
    }

    /**
     * Deep Copy を返す。コピーは Immutable ではない。
     * @param src コピー元 Map/NbJSONObject
     * @return コピーされた NbJSONObject
     */
    public static NbJSONObject deepCopyOf(Map<String, Object> src) {
        NbJSONObject dst = new NbJSONObject(src.size());

        for (Map.Entry<String,Object> entry : src.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) { // NbJSONObject 含む
                dst.put(key, deepCopyOf((Map<String,Object>)value));
            }
            else if (value instanceof List) {
                dst.put(key, NbJSONArray.deepCopyOf((List<Object>) value));
            }
            else {
                // String, Number, Boolean, null etc.
                dst.put(key, value);
            }
        }
        return dst;
    }

    /**
     * JSON文字列に変換する
     * @return JSON文字列
     */
    public String toJSONString() {
        return NbJSONGenerator.jsonToString(this);
    }

    @Override
    public String toString() {
        return toJSONString();
    }

    /**
     * キーに対応する NbJSONObject を取得する。
     * @param key キー
     * @return NbJSONObject
     * @throws ClassCastException 型が一致しない
     */
    public NbJSONObject getJSONObject(String key) {
        return (NbJSONObject)get(key);
    }

    /**
     * キーに対応する NbJSONArray を取得する。
     * @param key キー
     * @return NbJSONArray
     * @throws ClassCastException 型が一致しない
     */
    public NbJSONArray getJSONArray(String key) {
        return (NbJSONArray)get(key);
    }

    /**
     * キーに対応する String を取得する
     * @param key キー
     * @return 文字列
     * @throws ClassCastException 型が一致しない
     */
    public String getString(String key) {
        return (String)get(key);
    }

    /**
     * キーに対応する String を取得する(デフォルト値付き)。
     * キーに対応する値がない場合はデフォルト値が返却される。
     * 値の型が String でない場合は自動変換(toString()) される。
     * @param key キー
     * @param defValue デフォルト値
     * @return 文字列
     */
    public String optString(String key, String defValue) {
        Object value = get(key);
        if (value == null) {
            return defValue;
        } else if (value instanceof String) {
            return (String)value;
        } else {
            return value.toString();
        }
    }

    /**
     * キーに対応する Number を取得する
     * @param key キー
     * @return Number
     * @throws ClassCastException 型が一致しない
     */
    public Number getNumber(String key) {
        return (Number)get(key);
    }

    /**
     * キーに対応する int 値を取得する。
     * Number 型の場合は int に変換する。
     * @param key キー
     * @return 値
     * @throws ClassCastException 型が一致しない
     */
    public int getInt(String key) {
        Object value = get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            throw new ClassCastException("not number");
        }
    }

    /**
     * キーに対応する int 値を取得する(デフォルト値つき)。
     * キーが存在しない場合は defValue が返却される。
     * 値が Number でない場合は、自動変換(文字列変換してパース)する。
     * @param key キー
     * @param defValue デフォルト値
     * @return 数値
     */
    public int optInt(String key, int defValue) {
        Object value = get(key);
        if (value == null) {
            return defValue;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            return Integer.parseInt(value.toString());
        }
    }

    /**
     * キーに対応する long 値を取得する。
     * Number 型の場合は long に変換する。
     * @param key キー
     * @return 値
     * @throws ClassCastException 型が一致しない
     */
    public long getLong(String key) {
        Object value = get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            throw new ClassCastException("Not number");
        }
    }

    public long optLong(String key, long defValue) {
        Object value = get(key);
        if (value == null) {
            return defValue;
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            return Long.parseLong(value.toString());
        }
    }

    /**
     * キーに対応する Boolean を取得する
     * @param key キー
     * @return Boolean
     * @throws ClassCastException 型が一致しない
     */
    public Boolean getBoolean(String key) {
        return (Boolean)get(key);
    }

    public boolean optBoolean(String key, boolean defValue) {
        Object value = get(key);
        if (value == null) {
            return defValue;
        } else if (value instanceof Boolean) {
            return (Boolean)value;
        } else {
            return Boolean.parseBoolean(value.toString());
        }
    }
}
