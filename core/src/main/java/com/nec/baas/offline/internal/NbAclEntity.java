/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.json.*;

/**
 * DB向けオブジェクト、ファイル共通のデータ情報クラス.<br>
 * @since 1.0
 */
public class NbAclEntity {
    private String aclString;
    private NbJSONObject aclJson;

    public void setAclString(String aclString) {
        this.aclString = aclString;
        this.aclJson = null;
    }

    public String getAclString() {
        if (this.aclString == null && this.aclJson != null) {
            this.aclString = this.aclJson.toJSONString();
        }
        return this.aclString;
    }

    public void setAclJson(NbJSONObject aclJson) {
        this.aclJson = aclJson.getImmutableInstance();
        this.aclString = null;
    }

    public NbJSONObject getAclJson() {
        if (this.aclJson == null && this.aclString != null) {
            this.aclJson = NbJSONParser.parse(this.aclString);
            if (this.aclJson != null) {
                this.aclJson.setImmutable();
            }
        }
        return this.aclJson;
    }
}
