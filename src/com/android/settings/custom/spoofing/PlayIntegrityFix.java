/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.custom.spoofing;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@SearchIndexable
public class PlayIntegrityFix extends DashboardFragment {
    private static final String TAG = "PlayIntegrityFix";

    private static final String PIF_DATA_KEY = "pif_data_setting";
    private static final String PIF_PROPS_KEY = "pif_props";
    private static final String PIF_UPDATE_KEY = "pif_update";
    private static final String PIF_UPDATE_RANDOM_KEY = "pif_update_random";
    private static final String PIF_MASTER_SWITCH_KEY = "persist.sys.pihooks.disable.gms_props";
    private static final String KEY_AUTO_UPDATE_PIF = "auto_update_pif";

    private ActivityResultLauncher<Intent> mPifFilePickerLauncher;
    private PifDataPreference mPifDataPreference;
    private SwitchPreferenceCompat mPifMasterSwitch;
    private SwitchPreferenceCompat mAutoUpdateSwitch;
    private Preference mPifProps;
    private Preference mPifUpdate;
    private Preference mPifUpdateRandom;

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
        return R.xml.play_integrity_fix;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        mPifFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        Preference pref = findPreference(PIF_DATA_KEY);
                        if (pref instanceof PifDataPreference) {
                            ((PifDataPreference) pref).handleFileSelected(uri);
                        }
                    }
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPifDataPreference = findPreference(PIF_DATA_KEY);
        mPifMasterSwitch = findPreference(PIF_MASTER_SWITCH_KEY);
        mAutoUpdateSwitch = findPreference(KEY_AUTO_UPDATE_PIF);
        mPifProps = findPreference(PIF_PROPS_KEY);
        mPifUpdate = findPreference(PIF_UPDATE_KEY);
        mPifUpdateRandom = findPreference(PIF_UPDATE_RANDOM_KEY);

        if (mPifDataPreference != null) {
            mPifDataPreference.setFilePickerLauncher(mPifFilePickerLauncher);
        }

        if (mPifMasterSwitch != null) {
            boolean isChecked = SystemProperties.getBoolean(PIF_MASTER_SWITCH_KEY, false);
            mPifMasterSwitch.setChecked(isChecked);
            updatePifPreferencesState(!isChecked);

            mPifMasterSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean disabled = (Boolean) newValue;
                SystemProperties.set(PIF_MASTER_SWITCH_KEY, disabled ? "true" : "false");
                updatePifPreferencesState(!disabled);
                if (disabled) {
                    clearPifProps();
                }
                return true;
            });
        }

        if (mAutoUpdateSwitch != null) {
            boolean autoUpdateEnabled = Settings.Secure.getInt(
                    requireContext().getContentResolver(),
                    Settings.Secure.AUTO_UPDATE_PIF, 0) == 1;
            mAutoUpdateSwitch.setChecked(autoUpdateEnabled);
            mAutoUpdateSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                Settings.Secure.putInt(
                        requireContext().getContentResolver(),
                        Settings.Secure.AUTO_UPDATE_PIF, enabled ? 1 : 0);
                return true;
            });
        }

        if (mPifProps != null) {
            mPifProps.setOnPreferenceClickListener(preference -> {
                showPifProps();
                return true;
            });
        }

        if (mPifUpdate != null) {
            mPifUpdate.setOnPreferenceClickListener(preference -> {
                new UpdatePifTask().execute();
                return true;
            });
        }

        if (mPifUpdateRandom != null) {
            mPifUpdateRandom.setOnPreferenceClickListener(preference -> {
                new UpdateRandomPifTask().execute();
                return true;
            });
        }
    }

    private void updatePifPreferencesState(boolean enabled) {
        if (mPifDataPreference != null) mPifDataPreference.setEnabled(enabled);
        if (mAutoUpdateSwitch != null) mAutoUpdateSwitch.setEnabled(enabled);
        if (mPifProps != null) mPifProps.setEnabled(enabled);
        if (mPifUpdate != null) mPifUpdate.setEnabled(enabled);
        if (mPifUpdateRandom != null) mPifUpdateRandom.setEnabled(enabled);
    }

    private void clearPifProps() {
        Settings.Secure.putString(requireContext().getContentResolver(), Settings.Secure.PIF_DATA, null);
        Settings.Secure.putString(requireContext().getContentResolver(), Settings.Secure.PIF_DATA_TIMESTAMP, null);
        Settings.Secure.putString(requireContext().getContentResolver(), Settings.Secure.FETCHED_PIF, null);
        killGmsPackages();
        Toast.makeText(requireContext(), R.string.pif_toast_file_cleared, Toast.LENGTH_SHORT).show();
    }

    private void showPifProps() {
        String fetchedPif = Settings.Secure.getString(requireContext().getContentResolver(),
                Settings.Secure.FETCHED_PIF);
        String pifData = Settings.Secure.getString(requireContext().getContentResolver(),
                Settings.Secure.PIF_DATA);

        StringBuilder sb = new StringBuilder();
        sb.append("Auto-updated / Fetched PIF:\n");
        if (!TextUtils.isEmpty(fetchedPif)) {
            try {
                JSONObject json = new JSONObject(fetchedPif);
                sb.append(json.toString(4));
            } catch (JSONException e) {
                sb.append(fetchedPif);
            }
        } else {
            sb.append("Not set");
        }

        sb.append("\n\nManually imported PIF:\n");
        if (!TextUtils.isEmpty(pifData)) {
            try {
                JSONObject json = new JSONObject(pifData);
                sb.append(json.toString(4));
            } catch (JSONException e) {
                sb.append(pifData);
            }
        } else {
            sb.append("Not set");
        }

        if (TextUtils.isEmpty(fetchedPif) && TextUtils.isEmpty(pifData)) {
            sb.append("\n\nDefault ROM PIF (built-in overlay):\n");
            try {
                String[] defaultProps = requireContext().getResources().getStringArray(
                        com.android.internal.R.array.config_certifiedBuildProperties);
                for (String prop : defaultProps) {
                    sb.append("  ").append(prop).append('\n');
                }
            } catch (Exception ignored) {
                sb.append("Not configured");
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.pif_props_title)
                .setMessage(sb.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private class UpdatePifTask extends AsyncTask<Void, Void, String> {
        private ProgressDialog mProgressDialog;

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(requireContext());
            mProgressDialog.setTitle(getString(R.string.pif_random_fetching_title));
            mProgressDialog.setMessage(getString(R.string.pif_random_fetching_message));
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                String updateUrl = requireContext().getResources().getString(
                        com.android.internal.R.string.config_pifUpdateUrl);
                if (TextUtils.isEmpty(updateUrl)) {
                    return null;
                }
                URL url = new URL(updateUrl);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(10000);
                urlConnection.setReadTimeout(10000);
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(urlConnection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return response.toString();
                } finally {
                    urlConnection.disconnect();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to download PIF from URL", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }

            if (!TextUtils.isEmpty(result)) {
                Settings.Secure.putString(requireContext().getContentResolver(),
                        Settings.Secure.PIF_DATA, null);
                Settings.Secure.putString(requireContext().getContentResolver(),
                        Settings.Secure.PIF_DATA_TIMESTAMP, null);
                Settings.Secure.putString(requireContext().getContentResolver(),
                        Settings.Secure.FETCHED_PIF, result);
                Toast.makeText(requireContext(), "PIF updated successfully", Toast.LENGTH_SHORT)
                        .show();
                killGmsPackages();
            } else {
                Toast.makeText(requireContext(), "Failed to update PIF", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class UpdateRandomPifTask extends AsyncTask<Void, Void, Map<String, String>> {
        private ProgressDialog mProgressDialog;

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(requireContext());
            mProgressDialog.setTitle(getString(R.string.pif_random_fetching_title));
            mProgressDialog.setMessage(getString(R.string.pif_random_fetching_message));
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected Map<String, String> doInBackground(Void... voids) {
            try {
                String currentDevice = SystemProperties.get("persist.sys.pihooks_DEVICE", "");
                if (TextUtils.isEmpty(currentDevice)) {
                    return SpoofingUtils.getRandomFingerprint();
                }
                return SpoofingUtils.getRandomFingerprint(currentDevice);
            } catch (Exception e) {
                Log.e(TAG, "Error fetching random PIF properties", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Map<String, String> newValues) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }

            if (newValues == null || newValues.isEmpty()) {
                Toast.makeText(requireContext(), R.string.pif_update_failed, Toast.LENGTH_SHORT)
                        .show();
                return;
            }

            final JSONObject json = new JSONObject();
            String spoofedModel = newValues.get("MODEL");
            try {
                for (Map.Entry<String, String> entry : newValues.entrySet()) {
                    final String key = entry.getKey();
                    final String value = entry.getValue();
                    if (value == null) continue;
                    json.put(key, value);
                }
                Settings.Secure.putString(requireContext().getContentResolver(),
                        Settings.Secure.PIF_DATA, null);
                Settings.Secure.putString(requireContext().getContentResolver(),
                        Settings.Secure.PIF_DATA_TIMESTAMP, null);
                Settings.Secure.putString(requireContext().getContentResolver(),
                        Settings.Secure.FETCHED_PIF, json.toString());
            } catch (JSONException e) {
                Log.e(TAG, "Failed serializing random PIF properties", e);
                Toast.makeText(requireContext(), R.string.pif_update_failed, Toast.LENGTH_SHORT)
                        .show();
                return;
            }

            Toast.makeText(requireContext(),
                    getString(R.string.pif_update_success,
                            spoofedModel != null ? spoofedModel : "unknown"),
                    Toast.LENGTH_LONG).show();

            killGmsPackages();
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
                    sir.xmlResId = R.xml.play_integrity_fix;
                    return Arrays.asList(sir);
                }
            };
}
