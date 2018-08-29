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
 * JSON ジェネレータ。
 *
 * <p>本クラスの全メソッドはスレッドセーフである。</p>
 * @since 1.2.0
 */
public class NbJSONGenerator {
    /**
     * NbJSONObject から文字列に変換する
     * @param jsonObject NbJSONObject または Map
     * @return 文字列
     * @throws IllegalArgumentException
     */
    public static String jsonToString(Map<String,?> jsonObject) {
        StringBuilder b = new StringBuilder();

        writeObject(b, jsonObject);

        return b.toString();
    }

    private static void writeObject(StringBuilder b, Map<String,?> jsonObject) {
        b.append('{');

        boolean isFirst = true;
        for (Map.Entry<String,?> entry : jsonObject.entrySet()) {
            if (!isFirst) {
                b.append(',');
            } else {
                isFirst = false;
            }
            writeString(b, entry.getKey());
            b.append(':');
            writeValue(b, entry.getValue());
        }

        b.append('}');
    }

    private static void writeArray(StringBuilder b, Collection<Object> jsonArray) {
        b.append('[');

        boolean isFirst = true;
        for (Object value : jsonArray) {
            if (!isFirst) {
                b.append(',');
            } else {
                isFirst = false;
            }
            writeValue(b, value);
        }

        b.append(']');
    }

    private static void writeArray(StringBuilder b, Object[] array) {
        b.append('[');

        boolean isFirst = true;
        for (Object value : array) {
            if (!isFirst) {
                b.append(',');
            } else {
                isFirst = false;
            }
            writeValue(b, value);
        }

        b.append(']');
    }

    private static List<Object> tryConvertPrimitiveArray(Object value) {
        List<Object> out = new ArrayList<>();
        if (value instanceof int[]) {
            for (int x : (int[])value) out.add(x);
        }
        else if (value instanceof long[]) {
            for (long x : (long[])value) out.add(x);
        }
        else if (value instanceof boolean[]) {
            for (boolean x : (boolean[])value) out.add(x);
        }
        else if (value instanceof short[]) {
            for (short x : (short[])value) out.add(x);
        }
        else if (value instanceof byte[]) {
            for (byte x : (byte[])value) out.add(x);
        }
        else if (value instanceof char[]) {
            for (char x : (char[])value) out.add(x);
        }
        else if (value instanceof float[]) {
            for (float x : (float[])value) out.add(x);
        }
        else if (value instanceof double[]) {
            for (double x : (double[])value) out.add(x);
        }
        else {
            return null;
        }
        return out;
    }

    private static void writeValue(StringBuilder b, Object value) {
        if (value == null) {
            b.append("null");
        } else if (value instanceof String) {
            writeString(b, (String) value);
        } else if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            b.append(value.toString());
        } else if (value instanceof Map) {
            writeObject(b, (Map<String, Object>) value);
        } else if (value instanceof Collection) {
            // List, Set etc.
            writeArray(b, (Collection<Object>) value);
        } else if (value instanceof Object[]) {
            writeArray(b, (Object[]) value);
        } else {
            List<Object> array = tryConvertPrimitiveArray(value);
            if (array != null) {
                writeArray(b, array);
            } else {
                throw new IllegalArgumentException("Unsupported Type: " + value.getClass().getName());
            }
        }
    }

    private static void writeString(StringBuilder b, String s) {
        b.append('"');

        int len = s.length();
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);

            switch (ch) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\b':
                    b.append("\\b");
                    break;
                case '\f':
                    b.append("\\f");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    // NOTE: ASCII以外のUnicode文字列はエスケープ(\\uxxxx)せず、UTF-8 のままにする。
                    b.append(ch);
                    break;
            }
        }

        b.append('"');
    }
}
