/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.json.*;
import com.nec.baas.offline.*;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * オブジェクトストレージのデータクラス.
  * @since 1.0
 */
@EqualsAndHashCode(callSuper = true)
public class NbObjectEntity extends NbAclEntity {
    @Setter @Getter
    private String objectId;

    private String jsonString;
    private NbJSONObject jsonObject;

    /**
     * 同期状態。
     * NbOfflineService の NbSyncState.* の値が入る。
     * @see NbOfflineService
     */
    @Setter @Getter
    private NbSyncState state;

    @Setter @Getter
    private String timestamp;

    @Setter @Getter
    private String eTag;

    /**
     * JSON テキストをセットする。JSONオブジェクトはクリアされる。
     * @param jsonString JSONテキスト
     * @see #setJsonStringDontClearObject
     */
    public void setJsonString(String jsonString) {
        this.jsonString = jsonString;
        this.jsonObject = null;
    }

    /**
     * 「JSONオブジェクトをクリアせず」に、JSONテキストをセットする。
     * JSONオブジェクトとテキストの整合性は呼び出し元で保証しなければならない。
     * @param jsonString JSONテキスト
     * @see #setJsonString
     */
    public void setJsonStringDontClearObject(String jsonString) {
        this.jsonString = jsonString;
    }

    /**
     * JSONテキストを取得する。
     * JSONオブジェクトのみが設定されている場合、自動的にテキストに変換される。
     * @return JSONテキスト
     */
    public String getJsonString() {
        if (jsonString == null && jsonObject != null) {
            jsonString = jsonObject.toJSONString();
        }
        return jsonString;
    }

    /**
     * JSONオブジェクトをセットする。JSONテキストはクリアされる。
     * <p>オブジェクトは内部にコピーされる。
     * @param jsonObject JSONオブジェクト
     * @see #setJsonObjectAsImmutable
     */
    public void setJsonObject(NbJSONObject jsonObject) {
        this.jsonString = null;
        if (jsonObject == null) {
            this.jsonObject = null;
        } else {
            this.jsonObject = jsonObject.getImmutableInstance();
        }
    }

    /**
     * JSONオブジェクトを直接セットする。
     * JSONオブジェクトは自動的に Immutable に変更される。
     *
     * <p>オブジェクトはコピーされず、内部にそのまま格納される。
     * オブジェクトは Immutable に変更されるので、以後オブジェクトの
     * 変更を行うことはできなくなる。
     * <p>JSONテキストはクリアされる。
     *
     * @param jsonObject JSONオブジェクト
     * @see #setJsonObject
     */
    public void setJsonObjectAsImmutable(NbJSONObject jsonObject) {
        this.jsonString = null;

        jsonObject.setImmutable();
        this.jsonObject = jsonObject;
    }

    /**
     * JSONオブジェクトを取得する。内部オブジェクトのコピーが返却される。
     *
     * <p>JSONテキストのみが設定されている場合、自動的にオブジェクトに変換される。
     *
     * @return JSONオブジェクト
     * @see #getImmutableJsonObject
     */
    public NbJSONObject getJsonObject() {
        NbJSONObject ret = getImmutableJsonObject();
        if (ret == null) return null;

        return ret.getMutableInstance();
    }

    /**
     * Immutable JSONオブジェクトを取得する。内部オブジェクトが直接返却される。
     *
     * <p>JSONテキストのみが設定されている場合、自動的にオブジェクトに変換される。
     * @return JSONオブジェクト
     * @see #getJsonObject
     */
    public NbJSONObject getImmutableJsonObject() {
        if (jsonObject == null && jsonString != null) {
            jsonObject = NbJSONParser.parse(jsonString);
            if(jsonObject != null) {
                jsonObject.setImmutable();
            }
        }
        return jsonObject;
    }
}
