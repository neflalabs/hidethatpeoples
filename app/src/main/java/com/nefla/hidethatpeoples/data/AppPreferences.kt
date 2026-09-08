package com.nefla.hidethatpeoples.data

import android.content.Context
import android.content.SharedPreferences

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
            val defaults = TargetApp.DEFAULT_TARGETS.filter { it.defaultEnabled }.map { it.packageName }.toSet()
            return prefs.getStringSet(KEY_ENABLED_PACKAGES, defaults) ?: defaults
        }
        set(value) {
            prefs.edit().putStringSet(KEY_ENABLED_PACKAGES, value).apply()
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

    var isAutoCleanEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CLEAN_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CLEAN_ENABLED, value).apply()

    var autoCleanIntervalMinutes: Long
        get() = prefs.getLong(KEY_AUTO_CLEAN_INTERVAL, 30L)
        set(value) = prefs.edit().putLong(KEY_AUTO_CLEAN_INTERVAL, value).apply()

    var lastClearedTimestamp: Long
        get() = prefs.getLong(KEY_LAST_CLEARED_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CLEARED_TIMESTAMP, value).apply()
}
