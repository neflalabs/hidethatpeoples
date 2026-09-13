# Proguard / R8 rules for HideThatPeoples

# Standalone ADB and Cryptography (TLS / JNI / Reflection)
-keep class io.github.muntashirakon.adb.** { *; }
-dontwarn io.github.muntashirakon.adb.**

-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# Coroutines & WorkManager
-keep class androidx.work.** { *; }
-keep class com.neflalabs.hidethatpeoples.worker.AutoCleanWorker { *; }

# Target App Models & Preferences
-keep class com.neflalabs.hidethatpeoples.data.TargetApp { *; }
-keep class com.neflalabs.hidethatpeoples.data.InstalledTargetApp { *; }
