/*
 * Copyright (C) 2026 The Neoteric OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.custom.smartpixel;

import android.app.settings.SettingsEnums;
import android.os.Bundle;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class SmartPixelsSettings extends SettingsPreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.smart_pixels);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }
}
