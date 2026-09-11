/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.custom.spoofing

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import java.io.File
import java.io.FileOutputStream

class TeeSimulatorSettings : SettingsPreferenceFragment() {

    private var teeEnabled = false
    private var teeMode = MODE_AUTO
    private var teeKeyboxPath = ""
    private var teeShowSystemApps = false
    private val teeTargetPackages = mutableSetOf<String>()

    private val keyboxPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK || result.data == null) return@registerForActivityResult
            val uri = result.data?.data ?: return@registerForActivityResult
            onKeyboxSelected(uri)
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.tee_simulator_settings)

        loadConfigFromStore()
        bindPreferences()
    }

    override fun onResume() {
        super.onResume()
        loadConfigFromStore()
        bindPreferences()
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CUSTOM

    private fun bindPreferences() {
        val enablePref = findPreference<SwitchPreferenceCompat>(KEY_ENABLED)
        enablePref?.isPersistent = false
        enablePref?.isChecked = teeEnabled
        enablePref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            teeEnabled = newValue as Boolean
            persistConfigToStore()
            refreshKeyboxSummary()
            refreshTargetsSummary()
            killGms()
            true
        }

        val keyboxPref = findPreference<Preference>(KEY_KEYBOX_PATH)
        keyboxPref?.isPersistent = false
        keyboxPref?.setOnPreferenceClickListener {
            launchKeyboxPicker()
            true
        }

        val deleteKeyboxPref = findPreference<Preference>(KEY_DELETE_KEYBOX)
        deleteKeyboxPref?.isPersistent = false
        deleteKeyboxPref?.setOnPreferenceClickListener {
            showDeleteKeyboxDialog()
            true
        }

        val manageTargetsPref = findPreference<Preference>(KEY_MANAGE_TARGETS)
        manageTargetsPref?.isPersistent = false

        refreshKeyboxSummary()
        refreshTargetsSummary()
    }

    private fun loadConfigFromStore() {
        val cr = requireContext().contentResolver
        teeEnabled = Settings.Secure.getInt(cr, SETTING_ENABLED, 0) == 1
        teeMode = normalizeMode(Settings.Secure.getString(cr, SETTING_MODE) ?: MODE_AUTO)
        val savedKeybox = Settings.Secure.getString(cr, SETTING_KEYBOX) ?: ""
        if (savedKeybox.isNotEmpty()) {
            teeKeyboxPath = File(STORE_DIR, KEYBOX_FILE).absolutePath
        } else {
            teeKeyboxPath = ""
        }
        teeShowSystemApps = Settings.Secure.getInt(cr, SETTING_SHOW_SYSTEM_APPS, 0) == 1

        var savedPkgs = Settings.Secure.getString(cr, SETTING_PACKAGES)
        if (savedPkgs.isNullOrEmpty()) {
            savedPkgs = Settings.Secure.getString(cr, "spoof_trickystore_target")
        }
        teeTargetPackages.clear()
        if (!savedPkgs.isNullOrEmpty()) {
            teeTargetPackages.addAll(
                savedPkgs.lines().map { it.trim() }.filter { it.isNotEmpty() }
            )
        }

        val configFile = File(STORE_DIR, CONFIG_FILE)
        if (configFile.exists() && configFile.canRead()) {
            try {
                for (line in configFile.readText().lines()) {
                    val sep = line.indexOf('=')
                    if (sep <= 0) continue
                    val key = line.substring(0, sep).trim()
                    val value = line.substring(sep + 1).trim()
                    when (key) {
                        "enabled" -> teeEnabled = value == "1" || value.equals("true", ignoreCase = true)
                        "mode" -> teeMode = normalizeMode(value)
                        "keybox_path" -> if (value.isNotEmpty()) teeKeyboxPath = value
                        "show_system_apps" -> teeShowSystemApps = value == "1" || value.equals("true", ignoreCase = true)
                        "target_packages" -> {
                            if (value.isNotEmpty() && teeTargetPackages.isEmpty()) {
                                val packages = value.split(Regex("[,|]")).map { it.trim() }.filter { it.isNotEmpty() }
                                teeTargetPackages.addAll(packages)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (savedKeybox.isNotEmpty() && !File(STORE_DIR, KEYBOX_FILE).exists()) {
            Thread {
                saveFileToStore(KEYBOX_FILE, savedKeybox.toByteArray(Charsets.UTF_8))
                persistConfigToStore()
            }.start()
        }
    }

    private fun ensureStoreDir(): Boolean {
        val dir = File(STORE_DIR)
        if (dir.exists() && dir.canWrite()) return true
        if (dir.mkdirs()) {
            dir.setReadable(true, false)
            dir.setWritable(true, true)
            dir.setExecutable(true, false)
            return true
        }
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c",
                "mkdir -p $STORE_DIR && chmod 775 $STORE_DIR && chown 1000:1000 $STORE_DIR && restorecon -R $STORE_DIR"))
            p.waitFor()
            if (dir.exists()) return true
        } catch (_: Exception) {}
        return dir.exists()
    }

    private fun saveFileToStore(filename: String, bytes: ByteArray): Boolean {
        ensureStoreDir()
        val target = File(STORE_DIR, filename)
        try {
            FileOutputStream(target).use { it.write(bytes) }
            target.setReadable(true, false)
            target.setWritable(true, true)
            return true
        } catch (_: Exception) {
            try {
                val temp = File(requireContext().cacheDir, filename)
                FileOutputStream(temp).use { it.write(bytes) }
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c",
                    "cp ${temp.absolutePath} ${target.absolutePath} && chmod 664 ${target.absolutePath} && chown 1000:1000 ${target.absolutePath} && restorecon ${target.absolutePath}"))
                p.waitFor()
                temp.delete()
                return target.exists()
            } catch (_: Exception) {}
        }
        return false
    }

    private fun persistConfigToStore() {
        val cr = requireContext().contentResolver
        Settings.Secure.putInt(cr, SETTING_ENABLED, if (teeEnabled) 1 else 0)
        Settings.Secure.putString(cr, SETTING_MODE, teeMode)
        Settings.Secure.putInt(cr, SETTING_SHOW_SYSTEM_APPS, if (teeShowSystemApps) 1 else 0)
        Settings.Secure.putString(cr, SETTING_PACKAGES, teeTargetPackages.joinToString("\n"))

        Thread {
            try {
                val lines = listOf(
                    "enabled=${if (teeEnabled) "1" else "0"}",
                    "mode=$teeMode",
                    "keybox_path=$teeKeyboxPath",
                    "show_system_apps=${if (teeShowSystemApps) "1" else "0"}",
                    "target_packages=${teeTargetPackages.joinToString(",")}"
                )
                saveFileToStore(CONFIG_FILE, (lines.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun normalizeMode(mode: String): String {
        return when (mode) {
            MODE_PATCH, MODE_AUTO, MODE_GENERATE -> mode
            else -> MODE_AUTO
        }
    }

    private fun refreshKeyboxSummary() {
        val pref = findPreference<Preference>(KEY_KEYBOX_PATH) ?: return
        val deletePref = findPreference<Preference>(KEY_DELETE_KEYBOX)
        val hasKeybox = teeKeyboxPath.isNotEmpty() && (File(teeKeyboxPath).exists() || 
            !Settings.Secure.getString(requireContext().contentResolver, SETTING_KEYBOX).isNullOrEmpty())
        if (!hasKeybox) {
            pref.summary = getString(R.string.tee_simulator_no_keybox)
            deletePref?.isEnabled = false
        } else {
            pref.summary = getString(R.string.tee_simulator_keybox_installed)
            deletePref?.isEnabled = true
        }
    }

    private fun refreshTargetsSummary() {
        val managePref = findPreference<Preference>(KEY_MANAGE_TARGETS) ?: return
        val cr = requireContext().contentResolver

        var rawTargets = Settings.Secure.getString(cr, SETTING_PACKAGES)
        if (rawTargets.isNullOrEmpty()) {
            rawTargets = Settings.Secure.getString(cr, "spoof_trickystore_target")
        }

        val count = rawTargets?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.size ?: 0
        if (count == 0) {
            managePref.summary = getString(R.string.tee_simulator_no_targets)
        } else {
            managePref.summary = getString(R.string.tee_simulator_target_apps_count, count)
        }
    }

    private fun launchKeyboxPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/xml", "application/xml", "*/*"))
        }
        keyboxPickerLauncher.launch(intent)
    }

    private fun onKeyboxSelected(uri: Uri) {
        val context = requireContext()
        try {
            val content = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: ""

            if (content.isEmpty()) {
                toast(getString(R.string.tee_simulator_keybox_failed))
                return
            }

            Settings.Secure.putString(context.contentResolver, SETTING_KEYBOX, content)
            val target = File(STORE_DIR, KEYBOX_FILE)
            teeKeyboxPath = target.absolutePath

            saveFileToStore(KEYBOX_FILE, content.toByteArray(Charsets.UTF_8))
            persistConfigToStore()
            refreshKeyboxSummary()
            killGms()

            toast(getString(R.string.tee_simulator_keybox_imported))
        } catch (e: Exception) {
            e.printStackTrace()
            toast(getString(R.string.tee_simulator_keybox_failed))
        }
    }

    private fun showDeleteKeyboxDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.tee_simulator_delete_keybox_title)
            .setMessage(R.string.tee_simulator_delete_keybox_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                try {
                    val cr = requireContext().contentResolver
                    Settings.Secure.putString(cr, SETTING_KEYBOX, "")
                    val file = File(STORE_DIR, KEYBOX_FILE)
                    if (file.exists()) file.delete()
                    teeKeyboxPath = ""
                    persistConfigToStore()
                    refreshKeyboxSummary()
                    killGms()
                    toast(getString(R.string.tee_simulator_keybox_deleted))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun killGms() {
        try {
            val am = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.forceStopPackage(VENDING_PACKAGE)
            am.forceStopPackage(DROIDGUARD_PACKAGE)
            am.forceStopPackage(GMS_PACKAGE)
        } catch (_: Exception) {}
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val STORE_DIR = "/data/system/keystore_compat"
        const val CONFIG_FILE = "config.conf"
        const val KEYBOX_FILE = "keybox.xml"

        private const val MODE_AUTO = "auto"
        private const val MODE_PATCH = "patch"
        private const val MODE_GENERATE = "generate"

        private const val KEY_ENABLED = "tee_simulator_enabled"
        private const val KEY_KEYBOX_PATH = "tee_simulator_keybox_path"
        private const val KEY_DELETE_KEYBOX = "tee_simulator_delete_keybox"
        private const val KEY_MANAGE_TARGETS = "tee_simulator_manage_targets"

        private const val VENDING_PACKAGE = "com.android.vending"
        private const val DROIDGUARD_PACKAGE = "com.google.android.gms.unstable"
        private const val GMS_PACKAGE = "com.google.android.gms"

        private const val SETTING_ENABLED = "tee_simulator_enabled"
        private const val SETTING_MODE = "tee_simulator_mode"
        private const val SETTING_KEYBOX = "tee_simulator_keybox"
        private const val SETTING_PACKAGES = "tee_simulator_packages"
        private const val SETTING_SHOW_SYSTEM_APPS = "tee_simulator_show_system_apps"
    }
}
