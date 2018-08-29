/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.json.internal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.nec.baas.json.*;

import java.io.IOException;

/**
 * JSONパーサ (Jackson Streaming API 使用)。
 *
 * <p>本クラスのインスタンスはスレッドセーフである。</p>
 */
public class NbJSONParserJackson {
    // streaming
    private static final JsonFactory sJsonFactory = new JsonFactory();

    /**
     * JSON文字列(JSON Object)をパースする。パース失敗時は例外をスローする。
     * @param jsonString JSON文字列
     * @return JSONオブジェクト
     * @throws JsonParseException パースエラー
     * @throws IOException
     */
    public static NbJSONObject parseWithException(String jsonString) throws IOException, JsonParseException {
        JsonParser parser = null;
        try {
            parser = sJsonFactory.createParser(jsonString);
            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("Not JSON Object");
            }

            NbJSONObject value = readJsonObject(parser);
            return value;
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    private static NbJSONObject readJsonObject(JsonParser parser) throws IOException {
        NbJSONObject jsonObject = new NbJSONObject();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            // フィールド名取得
            String fieldName = parser.getCurrentName();

            parser.nextToken();
            jsonObject.put(fieldName, readValue(parser));
        }

        return jsonObject;
    }

    private static NbJSONArray readJsonArray(JsonParser parser) throws IOException {
        NbJSONArray jsonArray = new NbJSONArray();

        while (parser.nextToken() != JsonToken.END_ARRAY) {
            jsonArray.add(readValue(parser));
        }
        return jsonArray;
    }

    private static Object readValue(JsonParser parser) throws IOException {
        switch (parser.getCurrentToken()) {
            case START_OBJECT:
                return readJsonObject(parser);

            case START_ARRAY:
                return readJsonArray(parser);

            case VALUE_TRUE:
                return true;

            case VALUE_FALSE:
                return false;

            case VALUE_NULL:
                return null;

            case VALUE_STRING:
                return parser.getValueAsString();

            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return parser.getNumberValue();

            default:
                throw new IllegalArgumentException("Parse error!");
        }
    }
}
