/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.json.NbJSONObject;

import java.util.List;

import lombok.Getter;
import lombok.Setter;


/**
 * ログインキャッシュ情報のデータクラス。
 * @since 1.0
 */
@Getter
@Setter
public class NbLoginCacheEntity {
    private String userId;
    private String userName;
    private String emailAddress;
    private NbJSONObject options;
    private String createdTime;
    private String updatedTime;
    private String passwordHash;
    private String sessionToken;
    private List<String> groupList;
    private long loginCacheExpire;
    private long sessionTokenExpire;
}
