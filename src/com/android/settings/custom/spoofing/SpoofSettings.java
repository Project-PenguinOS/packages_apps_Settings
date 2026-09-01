/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.settings.custom.spoofing;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.Arrays;
import java.util.List;

@SearchIndexable
public class SpoofSettings extends DashboardFragment {
    private static final String TAG = "SpoofSettings";
    private static final String KEY_GPHOTOS_SPOOF = "gphotos_spoof";
    private static final String KEY_PI_TENSOR_SPOOF = "pi_tensor_spoof";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.spoofing_settings;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SwitchPreferenceCompat gphotosSpoof = findPreference(KEY_GPHOTOS_SPOOF);
        if (gphotosSpoof != null) {
            boolean isChecked = Settings.Secure.getInt(
                    requireContext().getContentResolver(),
                    Settings.Secure.GPHOTOS_SPOOF, 1) == 1;
            gphotosSpoof.setChecked(isChecked);
            gphotosSpoof.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                Settings.Secure.putInt(
                        requireContext().getContentResolver(),
                        Settings.Secure.GPHOTOS_SPOOF, enabled ? 1 : 0);
                killPackage(PACKAGE_GPHOTOS);
                return true;
            });
        }

        SwitchPreferenceCompat tensorSpoof = findPreference(KEY_PI_TENSOR_SPOOF);
        if (tensorSpoof != null) {
            boolean isChecked = Settings.Secure.getInt(
                    requireContext().getContentResolver(),
                    Settings.Secure.PI_TENSOR_SPOOF, 0) == 1;
            tensorSpoof.setChecked(isChecked);
            tensorSpoof.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                Settings.Secure.putInt(
                        requireContext().getContentResolver(),
                        Settings.Secure.PI_TENSOR_SPOOF, enabled ? 1 : 0);
                killGmsPackages();
                return true;
            });
        }
    }

    private void killPackage(String packageName) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    requireContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            try {
                am.getClass().getMethod("forceStopPackage", String.class).invoke(am, packageName);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to kill package: " + packageName, e);
        }
    }

    private void killGmsPackages() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    requireContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            String[] packages = {
                    "com.google.android.gms",
                    "com.android.vending",
                    "com.google.android.gsf",
                    "com.google.android.apps.walletnfcrel",
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.as",
                    "com.google.android.aicore",
                    "com.google.android.apps.aiwallpapers",
                    "com.google.android.apps.photos"
            };
            for (String pkg : packages) {
                try {
                    am.getClass().getMethod("forceStopPackage", String.class).invoke(am, pkg);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to kill GMS packages", e);
        }
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.spoofing_settings;
                    return Arrays.asList(sir);
                }
            };
}
