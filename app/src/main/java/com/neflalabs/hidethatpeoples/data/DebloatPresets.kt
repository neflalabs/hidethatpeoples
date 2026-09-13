package com.neflalabs.hidethatpeoples.data

object DebloatPresets {

    data class PresetDefinition(
        val packageName: String,
        val appName: String,
        val description: String,
        val category: DebloatCategory = DebloatCategory.PRESET_POPULAR,
        val safety: DebloatSafety = DebloatSafety.SAFE
    )

    val WELL_KNOWN_PRESETS = listOf(
        // Facebook & Meta Bloat (Installed on millions of Android phones by default)
        PresetDefinition(
            "com.facebook.katana",
            "Facebook",
            "Main Facebook social application",
            DebloatCategory.PRESET_POPULAR,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.facebook.services",
            "Meta Services",
            "Background background sync & telemetry for Meta",
            DebloatCategory.PRESET_POPULAR,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.facebook.system",
            "Meta App System",
            "System installer agent for Meta apps",
            DebloatCategory.PRESET_POPULAR,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.facebook.appmanager",
            "Meta App Manager",
            "Background auto-updater for Meta apps",
            DebloatCategory.PRESET_POPULAR,
            DebloatSafety.SAFE
        ),

        // Preloaded Sponsor Apps
        PresetDefinition(
            "com.netflix.mediaclient",
            "Netflix",
            "Preinstalled streaming app",
            DebloatCategory.PRESET_POPULAR,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.netflix.partner.activation",
            "Netflix Activation",
            "Preinstalled Netflix partner provisioning service",
            DebloatCategory.PRESET_POPULAR,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.spotify.music",
            "Spotify",
            "Preloaded music streaming app",
            DebloatCategory.PRESET_POPULAR,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.linkedin.android",
            "LinkedIn",
            "Preloaded professional network app",
            DebloatCategory.PRESET_POPULAR,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.tiktok.android",
            "TikTok",
            "Preloaded short video application",
            DebloatCategory.PRESET_POPULAR,
            DebloatSafety.SAFE
        ),

        // Microsoft Preinstalled
        PresetDefinition(
            "com.microsoft.office.onedrive",
            "Microsoft OneDrive",
            "Preinstalled Microsoft cloud sync",
            DebloatCategory.PRESET_POPULAR,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.microsoft.office.officehubrow",
            "Microsoft 365 / Office",
            "Preinstalled Office suite hub",
            DebloatCategory.PRESET_POPULAR,
            DebloatSafety.SAFE
        ),

        // OEM & Vendor Telemetry / Analytics / Adware
        PresetDefinition(
            "com.miui.analytics",
            "Xiaomi Analytics",
            "MIUI/HyperOS usage tracker & telemetry",
            DebloatCategory.OEM_CARRIER,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.miui.msa.global",
            "MIUI System Ads (MSA)",
            "Xiaomi system advertising daemon",
            DebloatCategory.OEM_CARRIER,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.miui.bugreport",
            "Mi Bug Report",
            "Telemetry and diagnostic logs sender",
            DebloatCategory.OEM_CARRIER,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.samsung.android.bixby.agent",
            "Bixby Voice Agent",
            "Samsung Bixby voice background service",
            DebloatCategory.OEM_CARRIER,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.samsung.android.game.gamehome",
            "Samsung Gaming Hub",
            "Samsung preinstalled gaming launcher with promotions",
            DebloatCategory.OEM_CARRIER,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.sec.android.app.sbrowser",
            "Samsung Internet",
            "Samsung default web browser",
            DebloatCategory.OEM_CARRIER,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.heytap.mcs",
            "OPPO/Realme Push Service",
            "HeyTap cloud messaging & promotional notification",
            DebloatCategory.OEM_CARRIER,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.transsion.ad.sdk",
            "Transsion Ad SDK",
            "Infinix / Tecno preinstalled advertising SDK",
            DebloatCategory.OEM_CARRIER,
            DebloatSafety.SAFE
        ),

        // Google Optional Bloat
        PresetDefinition(
            "com.google.android.apps.tachyon",
            "Google Meet / Duo",
            "Preinstalled video calling service",
            DebloatCategory.GOOGLE,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.google.android.videos",
            "Google TV / Movies",
            "Google film purchasing & rental service",
            DebloatCategory.GOOGLE,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.google.android.apps.youtube.music",
            "YouTube Music",
            "Preinstalled Google music player",
            DebloatCategory.GOOGLE,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.google.android.apps.podcasts",
            "Google Podcasts",
            "Legacy Google podcasts client",
            DebloatCategory.GOOGLE,
            DebloatSafety.SAFE
        ),
        PresetDefinition(
            "com.google.android.feedback",
            "Google Market Feedback Agent",
            "System feedback & crash reporting agent",
            DebloatCategory.GOOGLE,
            DebloatSafety.CAUTION
        )
    )
}
