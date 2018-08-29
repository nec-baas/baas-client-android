/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.push.internal;

import com.nec.baas.core.*;
import com.nec.baas.core.internal.*;
import com.nec.baas.json.*;

import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Pure Java 用 Preferences
 */
public class NbGenericPreferences implements NbPreferences {
    private Preferences mPrefs;

    public NbGenericPreferences(String name) {
        Preferences root = Preferences.userNodeForPackage(this.getClass());
        NbServiceImpl service = (NbServiceImpl) NbService.getInstance();
        Preferences appId = root.node(service.getAppId());
        mPrefs = appId.node(name);
   }

    @Override
    public String getString(String key, String defValue) {
        return mPrefs.get(key, defValue);
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValue) {
        String value = mPrefs.get(key, null);
        if (value == null) {
            return defValue;
        }

        Set<String> returnValue = new HashSet<>();
        try {
            NbJSONObject json = NbJSONParser.parse(value);
            NbJSONArray jsonArray = json.getJSONArray(key);
            for (int i = 0; i < jsonArray.size(); i++) {
                returnValue.add((String)jsonArray.get(i));
            }
        } catch (Exception e) {
            returnValue = defValue;
        }
        return returnValue;
    }

    @Override
    public NbPreferences putString(String key, String value) {
        if (value != null) {
            mPrefs.put(key, value);
        } else {
            mPrefs.remove(key);
        }
        return this;
    }

    @Override
    public NbPreferences putStringSet(String key, Set<String> setValue) {
        if (setValue != null) {
            NbJSONArray jsonArray = new NbJSONArray();
            NbJSONObject jsonObject = new NbJSONObject();
            for (String value: setValue) {
                jsonArray.add(value);
            }
            jsonObject.put(key, jsonArray);
            putString(key, jsonObject.toString());
        } else {
            mPrefs.remove(key);
        }
        return this;
    }

    @Override
    public NbPreferences clear() {
        try {
            mPrefs.clear();
        } catch (BackingStoreException e) {
            // for debug
        }
        return this;
    }

    @Override
    public NbPreferences apply() {
        // do nothing
        return this;
    }
}
