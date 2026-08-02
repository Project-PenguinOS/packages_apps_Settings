/*
 * SPDX-FileCopyrightText: The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display.darkmode

import android.content.Context
import android.provider.Settings.Secure.SYSTEM_BLACK_THEME
import com.android.settings.R
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.KeyValueStoreDelegate
import com.android.settingslib.datastore.SettingsSecureStore
import com.android.settingslib.datastore.SettingsStore
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.SwitchPreference
import com.android.settingslib.metadata.UI_ONLY_PREFERENCE

class BlackThemeTogglePreference :
    SwitchPreference(
        KEY,
        R.string.system_black_theme_title,
        R.string.system_black_theme_title,
        R.string.system_black_theme_summary,
    ) {

    override val key: String
        get() = KEY

    override fun tags(context: Context) = arrayOf(UI_ONLY_PREFERENCE)

    override fun storage(context: Context): KeyValueStore = BlackThemeStorage(context)

    override fun getReadPermissions(context: Context) = SettingsSecureStore.getReadPermissions()

    override fun getWritePermissions(context: Context) = SettingsSecureStore.getWritePermissions()

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override fun getWritePermit(
        context: Context,
        value: Boolean?,
        callingPid: Int,
        callingUid: Int,
    ) = ReadWritePermit.ALLOW

    override val sensitivityLevel
        get() = SensitivityLevel.NO_SENSITIVITY

    @Suppress("UNCHECKED_CAST")
    private class BlackThemeStorage(
        private val context: Context,
        private val settingsStore: SettingsStore = SettingsSecureStore.get(context),
    ) : KeyValueStoreDelegate {

        override val keyValueStoreDelegate
            get() = settingsStore

        override fun <T : Any> getDefaultValue(key: String, valueType: Class<T>) =
            DEFAULT_VALUE as T

        override fun <T : Any> setValue(key: String, valueType: Class<T>, value: T?) {
            settingsStore.setValue(key, valueType, value)
        }
    }

    companion object {
        const val KEY = SYSTEM_BLACK_THEME
        const val DEFAULT_VALUE = false
    }
}
