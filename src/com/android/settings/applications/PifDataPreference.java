/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.applications;

import android.content.Context;
import android.util.AttributeSet;

/**
 * Backward compatibility alias for PifDataPreference.
 */
public class PifDataPreference extends com.android.settings.custom.spoofing.PifDataPreference {

    public PifDataPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
