/*
 * Copyright (C) 2019-2026 Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.lockscreen;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.List;

@SearchIndexable
public class LockScreenWeather extends SettingsPreferenceFragment {

    public static final String TAG = "LockScreenWeather";
    private static final String KEY_WEATHER_ENABLED = "lockscreen_weather_enabled";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings_lock_screen_weather);

        Preference weatherEnabled = findPreference(KEY_WEATHER_ENABLED);
        if (weatherEnabled != null) {
            weatherEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                Settings.Secure.putInt(
                        getContentResolver(),
                        Settings.Secure.LOCK_SCREEN_WEATHER_ENABLED,
                        enabled ? 1 : 0);
                return true;
            });
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CUSTOM;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.settings_lock_screen_weather) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    return keys;
                }
            };
}
