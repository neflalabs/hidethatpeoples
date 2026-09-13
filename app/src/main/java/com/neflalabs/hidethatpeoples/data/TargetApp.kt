package com.neflalabs.hidethatpeoples.data

data class TargetApp(
    val packageName: String,
    val displayName: String,
    val iconRes: String = "",
    val defaultEnabled: Boolean = true
) {
    companion object {
        val DEFAULT_TARGETS = listOf(
            TargetApp("com.whatsapp", "WhatsApp"),
            TargetApp("com.whatsapp.w4b", "WhatsApp Business"),
            TargetApp("org.telegram.messenger", "Telegram"),
            TargetApp("com.google.android.apps.messaging", "Google Messages"),
            TargetApp("org.thoughtcrime.securesms", "Signal"),
            TargetApp("com.facebook.orca", "Facebook Messenger"),
            TargetApp("com.instagram.android", "Instagram"),
            TargetApp("com.google.android.gm", "Gmail")
        )
    }
}
