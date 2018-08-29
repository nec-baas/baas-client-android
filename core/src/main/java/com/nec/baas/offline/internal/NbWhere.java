/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 *  MongoクエリをSQLクエリに変換した際に
 *  where(値をプレースホルダに置き換えた構文)、whereArgs(プレースホルダに置き換える値の配列)に
 *  分けて受け取るためのDTOクラス
 */
public class NbWhere {
    @Getter @Setter
    private StringBuilder where = new StringBuilder();

    @Getter @Setter
    private List<String> whereArgs = new ArrayList<>();

    @Override
    public String toString() {
        return String.format("where: %s  whereArgs: %s", where.toString(), whereArgs.toString());
    }

    @Override
    public boolean equals(Object target) {
        if (this == target) return true;
        if (target == null || !(target instanceof NbWhere)) {
            return false;
        }
        return this.toString().equals(target.toString());
    }
}
