package com.neflalabs.hidethatpeoples.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledTargetApp(
    val packageName: String,
    val appName: String,
    val isPotentialDirectShare: Boolean,
    val categoryLabel: String
)

class TargetAppManager(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    suspend fun getInstalledTargets(): List<InstalledTargetApp> = withContext(Dispatchers.IO) {
        val foundPackages = mutableMapOf<String, InstalledTargetApp>()

        // 1. Query apps that handle SEND (*/*, text/plain)
        val sendTypes = listOf("text/plain", "*/*")
        for (type in sendTypes) {
            val sendIntent = Intent(Intent.ACTION_SEND).setType(type)
            val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(sendIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(sendIntent, 0)
            }
            for (resolveInfo in activities) {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg != context.packageName && !foundPackages.containsKey(pkg)) {
                    val label = resolveInfo.loadLabel(pm).toString()
                    val isPotential = isPotentialDirectShareApp(pkg, resolveInfo.activityInfo.applicationInfo)
                    foundPackages[pkg] = InstalledTargetApp(
                        packageName = pkg,
                        appName = label,
                        isPotentialDirectShare = isPotential,
                        categoryLabel = if (isPotential) "Chat & Social" else "Share Target"
                    )
                }
            }
        }

        // 2. Check common/known chat and messaging apps that might be installed
        for (known in KNOWN_CHAT_PACKAGES) {
            if (!foundPackages.containsKey(known)) {
                try {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(known, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(known, 0)
                    }
                    val label = pm.getApplicationLabel(appInfo).toString()
                    foundPackages[known] = InstalledTargetApp(
                        packageName = known,
                        appName = label,
                        isPotentialDirectShare = true,
                        categoryLabel = "Chat & Social"
                    )
                } catch (_: PackageManager.NameNotFoundException) {
                    // Not installed on device, ignore
                }
            }
        }

        // 3. Query launcher apps ONLY if they match potential chat/social
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }
        for (resolveInfo in launcherActivities) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg != context.packageName && !foundPackages.containsKey(pkg)) {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val isPotential = isPotentialDirectShareApp(pkg, appInfo)
                if (isPotential) {
                    val label = resolveInfo.loadLabel(pm).toString()
                    foundPackages[pkg] = InstalledTargetApp(
                        packageName = pkg,
                        appName = label,
                        isPotentialDirectShare = true,
                        categoryLabel = "Chat & Social"
                    )
                }
            }
        }

        // Sort: Potential Direct Share apps first, then alphabetical by App Name
        foundPackages.values.sortedWith(
            compareByDescending<InstalledTargetApp> { it.isPotentialDirectShare }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun getAppIcon(packageName: String): Drawable? {
        return try {
            pm.getApplicationIcon(packageName)
        } catch (_: Throwable) {
            null
        }
    }

    private fun isPotentialDirectShareApp(packageName: String, appInfo: ApplicationInfo?): Boolean {
        if (EXCLUDED_FROM_CHAT.contains(packageName)) {
            return false
        }
        val lower = packageName.lowercase()
        for (kw in CHAT_KEYWORDS) {
            if (lower.contains(kw)) return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo != null) {
            if (appInfo.category == ApplicationInfo.CATEGORY_SOCIAL) {
                return true
            }
        }
        return false
    }

    companion object {
        private val EXCLUDED_FROM_CHAT = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.google.android.calculator",
            "org.lineageos.aperture",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.nbu.files"
        )

        private val CHAT_KEYWORDS = listOf(
            "whatsapp", "telegram", "signal", "discord", "messenger", "instagram",
            "threads", "barcelona", "wechat", "line.android", "naver.line", "viber",
            "skype", "snapchat", "slack", "teams", "messaging", "android.gm",
            "outlook", "grok", "twitter", "challegram", "plusmessenger",
            "kakao", "tiktok"
        )

        private val KNOWN_CHAT_PACKAGES = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram",
            "org.thoughtcrime.securesms",
            "com.discord",
            "com.instagram.android",
            "com.instagram.barcelona",
            "com.instagram.basel",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.facebook.stella",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.twitter.android",
            "ai.x.grok",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically",
            "jp.naver.line.android",
            "com.tencent.mm",
            "com.viber.voip",
            "com.skype.raider",
            "com.snapchat.android",
            "com.Slack",
            "com.microsoft.teams"
        )
    }
}
