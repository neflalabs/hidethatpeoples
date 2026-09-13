package com.neflalabs.hidethatpeoples.data

data class DebloatApp(
    val packageName: String,
    val appName: String,
    val category: DebloatCategory,
    val isInstalled: Boolean = true,
    val isEnabled: Boolean = true,
    val description: String = "",
    val safetyLevel: DebloatSafety = DebloatSafety.SAFE
)

enum class DebloatCategory(val displayName: String) {
    PRESET_POPULAR("Popular Presets"),
    OEM_CARRIER("OEM & Telemetry"),
    GOOGLE("Google Suite"),
    ALL_SYSTEM("All System Apps")
}

enum class DebloatSafety(val displayName: String) {
    SAFE("Safe"),
    CAUTION("Caution")
}
