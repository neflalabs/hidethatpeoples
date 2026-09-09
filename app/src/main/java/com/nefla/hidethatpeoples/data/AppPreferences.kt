package com.nefla.hidethatpeoples.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hide_that_peoples_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENABLED_PACKAGES = "enabled_packages"
        private const val KEY_AUTO_CLEAN_ENABLED = "auto_clean_enabled"
        private const val KEY_AUTO_CLEAN_INTERVAL = "auto_clean_interval_minutes"
        private const val KEY_LAST_CLEARED_TIMESTAMP = "last_cleared_timestamp"
    }

    var enabledPackages: Set<String>
        get() {
            val saved = prefs.getStringSet(KEY_ENABLED_PACKAGES, null)
            if (saved != null) return saved
            return TargetApp.DEFAULT_TARGETS.map { it.packageName }.toSet()
        }
        set(value) {
            prefs.edit().putStringSet(KEY_ENABLED_PACKAGES, value).apply()
        }

    fun isConfigured(): Boolean = prefs.contains(KEY_ENABLED_PACKAGES)

    fun initializeWithDefaultsIfFirstRun(defaultPackages: Set<String>) {
        if (!prefs.contains(KEY_ENABLED_PACKAGES)) {
            enabledPackages = defaultPackages
        }
    }

    fun isPackageEnabled(packageName: String): Boolean {
        return enabledPackages.contains(packageName)
    }

    fun setPackageEnabled(packageName: String, enabled: Boolean) {
        val current = enabledPackages.toMutableSet()
        if (enabled) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        enabledPackages = current
    }

    fun setAllPackagesEnabled(packages: Collection<String>, enabled: Boolean) {
        val current = enabledPackages.toMutableSet()
        if (enabled) {
            current.addAll(packages)
        } else {
            current.removeAll(packages.toSet())
        }
        enabledPackages = current
    }

    var isAutoCleanEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CLEAN_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CLEAN_ENABLED, value).apply()

    var autoCleanIntervalMinutes: Long
        get() = prefs.getLong(KEY_AUTO_CLEAN_INTERVAL, 30L)
        set(value) = prefs.edit().putLong(KEY_AUTO_CLEAN_INTERVAL, value).apply()

    val autoCleanIntervalMinutesFlow: kotlinx.coroutines.flow.Flow<Long> = kotlinx.coroutines.flow.callbackFlow {
        trySend(autoCleanIntervalMinutes)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_AUTO_CLEAN_INTERVAL) {
                trySend(autoCleanIntervalMinutes)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var preferredPrivilegeType: String
        get() = prefs.getString("preferred_privilege_type", "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString("preferred_privilege_type", value).apply()

    var lastClearedTimestamp: Long
        get() = prefs.getLong(KEY_LAST_CLEARED_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CLEARED_TIMESTAMP, value).apply()

    val lastClearedTimestampFlow: kotlinx.coroutines.flow.Flow<Long> = kotlinx.coroutines.flow.callbackFlow {
        trySend(lastClearedTimestamp)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LAST_CLEARED_TIMESTAMP) {
                trySend(lastClearedTimestamp)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isAutoCleanEnabledFlow: kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.callbackFlow {
        trySend(isAutoCleanEnabled)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_AUTO_CLEAN_ENABLED) {
                trySend(isAutoCleanEnabled)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val enabledPackagesFlow: kotlinx.coroutines.flow.Flow<Set<String>> = kotlinx.coroutines.flow.callbackFlow {
        trySend(enabledPackages)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ENABLED_PACKAGES) {
                trySend(enabledPackages)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var isAdbPaired: Boolean
        get() = prefs.getBoolean("is_adb_paired", false)
        set(value) = prefs.edit().putBoolean("is_adb_paired", value).apply()

    var lastAdbConnectPort: Int
        get() = prefs.getInt("last_adb_connect_port", -1)
        set(value) = prefs.edit().putInt("last_adb_connect_port", value).apply()
}
