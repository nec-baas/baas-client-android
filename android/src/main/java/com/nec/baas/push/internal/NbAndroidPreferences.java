/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push.internal;

import android.content.Context;
import android.content.SharedPreferences;
import lombok.Getter;

import java.util.Set;

/**
 * プリファレンス: SharedPreference 使用
 */
public class NbAndroidPreferences implements NbPreferences {
    @Getter
    private String name;

    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mEditor;

    public NbAndroidPreferences(Context context, String name) {
        this.name = name;
        mPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        mEditor = null;
    }

    @Override
    public String getString(String key, String defValue) {
        return mPrefs.getString(key, defValue);
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValue) {
        return mPrefs.getStringSet(key, defValue);
    }

    @Override
    public NbPreferences putString(String key, String value) {
        getEditor().putString(key, value);
        return this;
    }

    @Override
    public NbPreferences putStringSet(String key, Set<String> value) {
        getEditor().putStringSet(key, value);
        return this;
    }

    @Override
    public NbPreferences clear() {
        getEditor().clear();
        return this;
    }

    private SharedPreferences.Editor getEditor() {
        if (mEditor == null) {
            mEditor = mPrefs.edit();
        }
        return mEditor;
    }

    @Override
    public NbPreferences apply() {
        if (mEditor != null) {
            mEditor.apply();
            mEditor = null;
        }
        return this;
    }
}
