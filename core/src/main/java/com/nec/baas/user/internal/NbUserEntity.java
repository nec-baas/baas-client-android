/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.user.internal;

import com.nec.baas.json.*;
import com.nec.baas.util.*;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.List;

/**
 * ユーザ情報格納用クラス
 * @since 1.0
 */
@Getter
@Setter
public class NbUserEntity {
    /** ユーザID */
    private String id;
    /** ユーザ名 */
    private String username;
    /** E-mail アドレス */
    private String email;
    /** オプション情報 */
    private NbJSONObject options;
    /** ユーザが所属するグループ一覧 */
    private NbJSONArray<String> groups = new NbJSONArray<>();
    /** ユーザの作成日時 */
    private String createdTime;
    /** ユーザの更新日時 */
    private String updatedTime;
    
    /**
     * デフォルトコンストラクタ
     */
    public NbUserEntity() {
    }
    
    /**
     * ユーザ情報初期化用コンストラクタ。
     * 引数に渡されない値は初期化しない。
     * @param id ユーザのID
     * @param username ユーザの名前
     * @param email ユーザのE-mail
     */
    public NbUserEntity(String id, String username, String email) {
        this.id = id;
        this.username = username;
        this.email = email;
    }

    /**
     * ユーザの JSON 表現から NbUserEntity を生成する。
     * @param json ユーザの JSON 表現
     */
    public NbUserEntity(NbJSONObject json) {
        set(json);
    }

    public void setGroups(@NonNull List<String> groups) {
        this.groups.clear();
        for (String group : groups) {
            this.groups.add(group);
        }
    }

    /**
     * ユーザの JSON 表現から NbUserEntity を生成する。
     * @param json ユーザの JSON 表現
     */
    public NbUserEntity set(NbJSONObject json) {
        this.id = json.getString(NbKey.ID);
        this.username = json.getString(NbKey.USERNAME);
        this.email = json.getString(NbKey.EMAIL);
        this.options = json.getJSONObject(NbKey.OPTIONS);
        this.createdTime = json.getString(NbKey.CREATED_AT);
        this.updatedTime = json.getString(NbKey.UPDATED_AT);

        NbJSONArray groups = json.getJSONArray(NbKey.GROUPS);
        if (groups != null) {
            this.groups.clear();
            for (Object group : groups) {
                this.groups.add((String)group);
            }
        }

        return this;
    }

    /**
     * NbUserEntity の内容をコピーする。(shallow copy)
     * @param entity NbUserEntity
     * @return this
     */
    public NbUserEntity set(NbUserEntity entity) {
        this.id = entity.id;
        this.username = entity.username;
        this.email = entity.email;
        this.options = entity.options;
        this.groups = entity.groups;
        this.createdTime = entity.createdTime;
        this.updatedTime = entity.updatedTime;
        return this;
    }

    /**
     * NbUserEntity を ユーザの JSON 表現に変換する。
     * @return ユーザの JSON 表現
     */
    public NbJSONObject toJsonObject() {
        NbJSONObject json = new NbJSONObject();

        // NbAcl#toJsonObject()に合わせ、Stringに対するnullチェックはしない
        json.put(NbKey.ID, this.id);
        json.put(NbKey.USERNAME, this.username);
        json.put(NbKey.EMAIL, this.email);
        json.put(NbKey.OPTIONS, this.options);
        json.put(NbKey.CREATED_AT, this.createdTime);
        json.put(NbKey.UPDATED_AT, this.updatedTime);

        if (this.groups != null) {
            json.put(NbKey.GROUPS, this.groups);
        }

        return json;
    }
}
