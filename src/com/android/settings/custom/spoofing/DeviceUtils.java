/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.custom.spoofing;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

public class DeviceUtils {

    public static boolean isActivityEnabled(Context context, String componentString) {
        try {
            ComponentName component = ComponentName.unflattenFromString(componentString);
            if (component == null) return false;
            ActivityInfo info = context.getPackageManager()
                    .getActivityInfo(component, 0);
            return info.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void setComponentEnabled(Context context, String componentString,
            boolean enabled) {
        PackageManager pm = context.getPackageManager();
        ComponentName component = ComponentName.unflattenFromString(componentString);
        if (component == null) return;

        pm.setComponentEnabledSetting(
                component,
                enabled
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
    }
}
