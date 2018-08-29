/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline;

import com.nec.baas.util.*;

/**
 * 同期状態
 * @since 1.2.0
 */
public enum NbSyncState {
    /** 状態なし */
    NOSTATE(-1),

    /** 同期済み */
    SYNC(0),

    /** 更新された/新規追加された */
    DIRTY(1),

    /** 削除された */
    DELETE(2),

    /** サーバーで追加または更新されたデータを同期中 */
    SYNCING(3),

    /** サーバーで削除されたデータを同期中 */
    SYNCING_DELETE(4),

    /** 更新されたデータで衝突発生 */
    CONFLICTED(5),

    /** 削除されたデータで衝突発生 */
    CONFLICTED_DELETE(6),

    /**
     * 完全上書きで更新された。
     * 内部的なステータスで、APIとして状態を出力する際にはSyncState.DIRTYとして出力する。
     */
    DIRTY_FULL(7),

    /** 完全上書きで更新されたデータで衝突発生 */
    CONFLICTED_FULL(8);

    private static final NbLogger log = NbLogger.getLogger(NbSyncState.class);

    public final int id;
    public final String idString;

    NbSyncState(final int id) {
        this.id = id;
        this.idString = Integer.toString(id);
    }

    /**
     * 整数値から変換
     */
    public static NbSyncState fromInt(int id) {
        for (NbSyncState state : values()) {
            if (state.id == id) {
                return state;
            }
        }
        //throw new IllegalArgumentException("Invalid id:" + id);
        log.warning("Invalid id: " + id);
        return NOSTATE;
    }

    /**
     * オブジェクトから変換。
     * Integerの場合はそのまま。それ以外の場合は文字列に変換してから parseInt する。
     * @param obj オブジェクト
     * @return 同期状態
     */
    public static NbSyncState fromObject(Object obj) {
        if (obj instanceof Integer) {
            return fromInt((int)obj);
        } else {
            return fromInt(Integer.parseInt(obj.toString()));
        }
    }

    /**
     * オブジェクトから変換(デフォルト値つき)。
     * Integerの場合はそのまま。それ以外の場合は文字列に変換してから parseInt する。
     * null の場合は defValue が返る。
     * @param obj オブジェクト
     * @return 同期状態
     */
    public static NbSyncState fromObject(Object obj, NbSyncState defValue) {
        if (obj == null) return defValue;
        return fromObject(obj);
    }

    public boolean isConflicted() {
        switch (this) {
            case CONFLICTED:
            case CONFLICTED_FULL:
            case CONFLICTED_DELETE:
                return true;
            default:
                return false;
        }
    }

    public boolean isDirtyNotFull() {
        switch (this) {
            case DIRTY:
            case CONFLICTED:
                return true;
            default:
                return false;
        }
    }

    public boolean isDirtyFull() {
        switch (this) {
            case DIRTY_FULL:
            case CONFLICTED_FULL:
                return true;
            default:
                return false;
        }
    }

    /**
     * 同期状態からDirtyか否かを判定する。
     * CONFLICTは元はDIRTYなデータであるため判定に加える。
     */
    public static boolean isDirty(NbSyncState state) {
        return state.isDirty();
    }

    public boolean isDirty() {
        switch (this) {
            case DIRTY:
            case DIRTY_FULL:
            case CONFLICTED:
            case CONFLICTED_FULL:
                return true;
            default:
                return false;
        }
    }

    /**
     * 同期状態からDeleteか否かを判定する。
     * CONFLICTED_DELETEは元はDELETEデータであるため判定に加える。
     */
    public boolean isDeleted() {
        switch (this) {
            case DELETE:
            case CONFLICTED_DELETE:
                return true;
            default:
                return false;
        }
    }

    /**
     * 同期状態からDirty or Deleteか否かを判定する。
     * CONFLICTは元はDIRTYなデータであるたため判定に加える。
     */
    public boolean isDirtyOrDelete() {
        switch (this) {
            case DIRTY:
            case DIRTY_FULL:
            case DELETE:
            case CONFLICTED:
            case CONFLICTED_FULL:
            case CONFLICTED_DELETE:
                return true;
            default:
                return false;
        }
    }
}
