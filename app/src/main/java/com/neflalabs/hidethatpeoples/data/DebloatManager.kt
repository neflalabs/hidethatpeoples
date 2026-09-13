package com.neflalabs.hidethatpeoples.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DebloatManager(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    suspend fun loadDebloatApps(): List<DebloatApp> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DebloatApp>()
        val presetMap = DebloatPresets.WELL_KNOWN_PRESETS.associateBy { it.packageName }
        val processedPackages = mutableSetOf<String>()

        // 1. Process Well-Known Presets first
        for (preset in DebloatPresets.WELL_KNOWN_PRESETS) {
            try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(preset.packageName, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(preset.packageName, 0)
                }
                val label = pm.getApplicationLabel(appInfo).toString()
                result.add(
                    DebloatApp(
                        packageName = preset.packageName,
                        appName = if (label.isNotBlank() && label != preset.packageName) label else preset.appName,
                        category = preset.category,
                        isInstalled = true,
                        isEnabled = appInfo.enabled,
                        description = preset.description,
                        safetyLevel = preset.safety
                    )
                )
                processedPackages.add(preset.packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed on this device, do not clutter UI unless desired
            }
        }

        // 2. Scan other System / Preinstalled Applications
        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }

        for (app in installedApps) {
            val pkg = app.packageName
            if (pkg == context.packageName || processedPackages.contains(pkg)) {
                continue
            }

            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            if (isSystem) {
                val label = pm.getApplicationLabel(app).toString()
                val isGoogle = pkg.startsWith("com.google.android.")
                val isOem = pkg.startsWith("com.miui.") ||
                        pkg.startsWith("com.samsung.") ||
                        pkg.startsWith("com.sec.") ||
                        pkg.startsWith("com.coloros.") ||
                        pkg.startsWith("com.heytap.") ||
                        pkg.startsWith("com.transsion.") ||
                        pkg.startsWith("com.huawei.")

                val category = when {
                    isOem -> DebloatCategory.OEM_CARRIER
                    isGoogle -> DebloatCategory.GOOGLE
                    else -> DebloatCategory.ALL_SYSTEM
                }

                result.add(
                    DebloatApp(
                        packageName = pkg,
                        appName = if (label.isNotBlank()) label else pkg,
                        category = category,
                        isInstalled = true,
                        isEnabled = app.enabled,
                        description = if (isSystem) "System Application" else "Installed Application",
                        safetyLevel = if (isCriticalSystemApp(pkg)) DebloatSafety.CAUTION else DebloatSafety.SAFE
                    )
                )
            }
        }

        result.sortedWith(
            compareBy<DebloatApp> {
                when (it.category) {
                    DebloatCategory.PRESET_POPULAR -> 0
                    DebloatCategory.OEM_CARRIER -> 1
                    DebloatCategory.GOOGLE -> 2
                    DebloatCategory.ALL_SYSTEM -> 3
                }
            }.thenBy { it.appName.lowercase() }
        )
    }

    fun getAppIcon(packageName: String): Drawable? {
        return try {
            pm.getApplicationIcon(packageName)
        } catch (_: Throwable) {
            null
        }
    }

    private fun isCriticalSystemApp(packageName: String): Boolean {
        val criticalPrefixes = listOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.providers.telephony",
            "com.android.providers.settings",
            "com.google.android.gms",
            "com.android.vending",
            "com.android.keychain",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        )
        return criticalPrefixes.any { packageName == it || packageName.startsWith("$it.") }
    }
}
