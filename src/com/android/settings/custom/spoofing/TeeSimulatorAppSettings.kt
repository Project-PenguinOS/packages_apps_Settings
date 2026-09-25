/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.android.settings.custom.spoofing

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.spa.framework.theme.SettingsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class TargetMode(val symbol: String) {
    AUTO(""),
    LEAF_HACK("?"),
    CERT_GEN("!"),
}

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean = false,
    var targetMode: TargetMode = TargetMode.AUTO,
    var isInTarget: Boolean = false,
)

private val EXCLUDED_SUFFIXES = listOf(
    ".auto_generated", ".appsearch", ".backup", ".carrier",
    ".cellbroadcast", ".cts", ".federated", ".ims", ".overlay",
    ".qti", ".qualcomm", ".resources", ".systemui.clocks",
    ".systemui.plugin", ".theme", ".iconpack",
)

class TeeSimulatorAppSettings : SettingsPreferenceFragment() {

    companion object {
        const val TARGET_KEY = "tee_simulator_packages"
        const val FALLBACK_TARGET_KEY = "spoof_trickystore_target"
        const val STORE_DIR = "/data/system/keystore_compat"
        const val CONFIG_FILE = "config.conf"

        val DEFAULT_TARGETS = setOf(
            "android",
            "com.android.vending",
            "com.google.android.gsf",
            "com.google.android.gms",
            "com.google.android.contactkeys",
            "com.google.android.ims",
            "com.google.android.safetycore",
            "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.nbu.paisa.user",
        )
        val DEFAULT_TARGET_MODES = mapOf(
            "com.revolut.revolut" to TargetMode.CERT_GEN,
            "io.github.qwq233.keyattestation" to TargetMode.LEAF_HACK,
            "io.github.vvb2060.keyattestation" to TargetMode.LEAF_HACK,
            "io.github.vvb2060.mahoshojo" to TargetMode.LEAF_HACK,
            "icu.nullptr.nativetest" to TargetMode.LEAF_HACK,
            "com.reveny.nativecheck" to TargetMode.LEAF_HACK,
            "com.zhenxi.hunter" to TargetMode.LEAF_HACK,
            "com.android.nativetest" to TargetMode.LEAF_HACK,
            "io.liankong.riskdetector" to TargetMode.LEAF_HACK,
            "luna.safe.luna" to TargetMode.LEAF_HACK,
            "com.eltavine.duckdetector" to TargetMode.LEAF_HACK,
            "com.rem01gaming.disclosure" to TargetMode.LEAF_HACK,
            "wu.keyChain.test" to TargetMode.LEAF_HACK,
            "com.kikyps.crackme" to TargetMode.LEAF_HACK,
            "com.chunqiunativecheck" to TargetMode.LEAF_HACK,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().title = getString(R.string.tee_simulator_manage_targets)
    }

    override fun getMetricsCategory() = MetricsProto.MetricsEvent.VIEW_UNKNOWN

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Compose owns the entire view, no preferences XML needed
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            SettingsTheme {
                TeeSimulatorAppSettingsContent(
                    context = requireContext(),
                )
            }
        }
    }
}

@Composable
private fun TeeSimulatorAppSettingsContent(
    context: Context,
) {
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val allApps = remember { mutableStateListOf<AppEntry>() }

    fun loadTargetMap(): Map<String, TargetMode> {
        val result = mutableMapOf<String, TargetMode>()
        var content = Settings.Secure.getString(
            context.contentResolver, TeeSimulatorAppSettings.TARGET_KEY
        )
        if (content.isNullOrEmpty()) {
            content = Settings.Secure.getString(
                context.contentResolver, TeeSimulatorAppSettings.FALLBACK_TARGET_KEY
            )
        }
        if (content == null) {
            val file = File(TeeSimulatorAppSettings.STORE_DIR, TeeSimulatorAppSettings.CONFIG_FILE)
            if (file.exists() && file.canRead()) {
                try {
                    for (line in file.readText().lines()) {
                        if (line.startsWith("target_packages=")) {
                            content = line.substring("target_packages=".length).replace(',', '\n')
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        if (content == null) return result

        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                when {
                    trimmed.endsWith("?") ->
                        result[trimmed.dropLast(1)] = TargetMode.LEAF_HACK
                    trimmed.endsWith("!") ->
                        result[trimmed.dropLast(1)] = TargetMode.CERT_GEN
                    else ->
                        result[trimmed] = TargetMode.AUTO
                }
            }
        }
        return result
    }

    fun syncToDisk(targetLines: List<String>) {
        try {
            val storeDir = File(TeeSimulatorAppSettings.STORE_DIR)
            val configFile = File(storeDir, TeeSimulatorAppSettings.CONFIG_FILE)
            var enabled = "1"
            var mode = "auto"
            var keyboxPath = File(storeDir, "keybox.xml").absolutePath
            var showSys = "0"

            if (configFile.exists() && configFile.canRead()) {
                try {
                    for (line in configFile.readText().lines()) {
                        val sep = line.indexOf('=')
                        if (sep <= 0) continue
                        val k = line.substring(0, sep).trim()
                        val v = line.substring(sep + 1).trim()
                        when (k) {
                            "enabled" -> enabled = v
                            "mode" -> mode = v
                            "keybox_path" -> if (v.isNotEmpty()) keyboxPath = v
                            "show_system_apps" -> showSys = v
                        }
                    }
                } catch (_: Exception) {}
            }

            val fileContent = listOf(
                "enabled=$enabled",
                "mode=$mode",
                "keybox_path=$keyboxPath",
                "show_system_apps=$showSys",
                "target_packages=${targetLines.joinToString(",")}"
            ).joinToString("\n") + "\n"

            val temp = File(storeDir, "${TeeSimulatorAppSettings.CONFIG_FILE}.tmp")
            try {
                FileOutputStream(temp).use { it.write(fileContent.toByteArray(Charsets.UTF_8)) }
                temp.setReadable(true, false)
                temp.setWritable(true, true)
                if (!temp.renameTo(configFile)) {
                    FileOutputStream(configFile).use { it.write(fileContent.toByteArray(Charsets.UTF_8)) }
                    configFile.setReadable(true, false)
                    configFile.setWritable(true, true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (temp.exists()) {
                    temp.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun killGms() {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.forceStopPackage("com.android.vending")
            am.forceStopPackage("com.google.android.gms.unstable")
            am.forceStopPackage("com.google.android.gms")
        } catch (_: Exception) {}
    }

    fun saveTargets() {
        val snapshot = allApps.toList()
        val lines = snapshot
            .filter { it.isInTarget }
            .map { it.packageName + it.targetMode.symbol }

        Settings.Secure.putString(
            context.contentResolver,
            TeeSimulatorAppSettings.TARGET_KEY,
            lines.joinToString("\n")
        )
        syncToDisk(lines)
        killGms()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: ""
                    if (text.isNotBlank()) {
                        val importedMap = mutableMapOf<String, TargetMode>()
                        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
                            when {
                                line.endsWith("?") -> importedMap[line.dropLast(1)] = TargetMode.LEAF_HACK
                                line.endsWith("!") -> importedMap[line.dropLast(1)] = TargetMode.CERT_GEN
                                else -> importedMap[line] = TargetMode.AUTO
                            }
                        }
                        withContext(Dispatchers.Main) {
                            allApps.indices.forEach { i ->
                                val pkg = allApps[i].packageName
                                if (pkg in importedMap) {
                                    allApps[i] = allApps[i].copy(
                                        isInTarget = true,
                                        targetMode = importedMap[pkg] ?: TargetMode.AUTO
                                    )
                                }
                            }
                            saveTargets()
                            Toast.makeText(context, R.string.tee_simulator_targets_imported, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val snapshot = allApps.toList()
                    val content = snapshot
                        .filter { it.isInTarget }
                        .joinToString("\n") { it.packageName + it.targetMode.symbol } + "\n"
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.tee_simulator_targets_exported, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun resetToDefaults() {
        allApps.indices.forEach { i ->
            val pkg = allApps[i].packageName
            val isDefault = pkg in TeeSimulatorAppSettings.DEFAULT_TARGETS ||
                pkg in TeeSimulatorAppSettings.DEFAULT_TARGET_MODES
            val mode = TeeSimulatorAppSettings.DEFAULT_TARGET_MODES[pkg] ?: TargetMode.AUTO
            allApps[i] = allApps[i].copy(
                isInTarget = isDefault,
                targetMode = mode,
            )
        }
        scope.launch(Dispatchers.IO) { saveTargets() }
        Toast.makeText(context, R.string.tee_simulator_reset_defaults, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(showSystemApps) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val existing = Settings.Secure.getString(
                context.contentResolver, TeeSimulatorAppSettings.TARGET_KEY
            )
            if (existing.isNullOrEmpty()) {
                val seed = TeeSimulatorAppSettings.DEFAULT_TARGETS.map { it } +
                    TeeSimulatorAppSettings.DEFAULT_TARGET_MODES.map { (pkg, mode) ->
                        pkg + mode.symbol
                    }
                Settings.Secure.putString(
                    context.contentResolver,
                    TeeSimulatorAppSettings.TARGET_KEY,
                    seed.joinToString("\n")
                )
                syncToDisk(seed)
            }

            val targetMap = loadTargetMap()
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val parsed = installed
                .filter { app ->
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    val isTrulySystem = isSystem && !isUpdatedSystem

                    if (!showSystemApps && isTrulySystem && app.packageName !in targetMap) {
                        return@filter false
                    }
                    if (EXCLUDED_SUFFIXES.any { app.packageName.endsWith(it) } && app.packageName !in targetMap) {
                        return@filter false
                    }
                    true
                }
                .map { app ->
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val mode = targetMap[app.packageName] ?: TargetMode.AUTO
                    val inTarget = app.packageName in targetMap
                    AppEntry(
                        packageName = app.packageName,
                        label = app.loadLabel(pm).toString(),
                        icon = app.loadIcon(pm),
                        isSystem = isSystem,
                        targetMode = mode,
                        isInTarget = inTarget,
                    )
                }
                .sortedWith(
                    compareByDescending<AppEntry> { it.isInTarget }
                        .thenBy { it.label.lowercase() }
                )

            withContext(Dispatchers.Main) {
                allApps.clear()
                allApps.addAll(parsed)
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, allApps.toList()) {
        if (searchQuery.isBlank()) allApps.toList()
        else allApps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val selectedCount = remember(allApps.toList()) {
        allApps.count { it.isInTarget }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tee_simulator_manage_targets),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (selectedCount == 0)
                                stringResource(R.string.tee_simulator_no_targets)
                            else
                                stringResource(R.string.tee_simulator_selected_count, selectedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { showSettingsMenu = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tee_simulator_import_targets)) },
                                onClick = {
                                    showSettingsMenu = false
                                    importLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tee_simulator_export_targets)) },
                                onClick = {
                                    showSettingsMenu = false
                                    exportLauncher.launch("target.txt")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tee_simulator_reset_defaults)) },
                                onClick = {
                                    showSettingsMenu = false
                                    resetToDefaults()
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AppPickerSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = showSystemApps,
                    onClick = { showSystemApps = !showSystemApps },
                    label = { Text(stringResource(R.string.show_system_apps)) },
                    leadingIcon = if (showSystemApps) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                )

                TextButton(
                    onClick = {
                        filteredApps.forEach { f ->
                            val i = allApps.indexOfFirst { it.packageName == f.packageName }
                            if (i >= 0 && !allApps[i].isInTarget) {
                                allApps[i] = allApps[i].copy(isInTarget = true)
                            }
                        }
                        scope.launch(Dispatchers.IO) { saveTargets() }
                    },
                ) {
                    Text(stringResource(R.string.select_all))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppPickerItem(
                            packageName = app.packageName,
                            label = app.label,
                            icon = app.icon,
                            isSystem = app.isSystem,
                            checked = app.isInTarget,
                            onToggle = { nowEnabled ->
                                val i = allApps.indexOfFirst { it.packageName == app.packageName }
                                if (i >= 0) {
                                    allApps[i] = allApps[i].copy(isInTarget = nowEnabled)
                                    scope.launch(Dispatchers.IO) { saveTargets() }
                                }
                            },
                            extraContent = if (app.isInTarget) {
                                {
                                    AnimatedVisibility(
                                        visible = app.isInTarget,
                                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                                  expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                                  shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
                                    ) {
                                        Column {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                                TargetMode.entries.forEachIndexed { index, mode ->
                                                    SegmentedButton(
                                                        selected = app.targetMode == mode,
                                                        onClick = {
                                                            val i = allApps.indexOfFirst { it.packageName == app.packageName }
                                                            if (i >= 0) {
                                                                allApps[i] = allApps[i].copy(targetMode = mode)
                                                                scope.launch(Dispatchers.IO) { saveTargets() }
                                                            }
                                                        },
                                                        shape = SegmentedButtonDefaults.itemShape(
                                                            index = index,
                                                            count = TargetMode.entries.size,
                                                        ),
                                                        label = {
                                                            Text(
                                                                text = when (mode) {
                                                                    TargetMode.AUTO -> stringResource(R.string.tee_simulator_mode_auto)
                                                                    TargetMode.LEAF_HACK -> stringResource(R.string.tee_simulator_mode_leaf)
                                                                    TargetMode.CERT_GEN -> stringResource(R.string.tee_simulator_mode_cert)
                                                                },
                                                                style = MaterialTheme.typography.labelSmall,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else null,
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}
