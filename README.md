# HideThatPeoples 🙈

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

**HideThatPeoples** (Launcher: *HidePeoples*) is a modern, lightweight Android privacy utility and system management tool built with Jetpack Compose, Material 3, and a **Built-in Standalone Wireless ADB Engine** (with optional Root support).

It delivers two powerful capabilities in one unified, thumb-friendly app:
1. **Direct Share Cleaner**: Instantly wipes sensitive direct share contact recommendations (people suggestions) from the Android Share Sheet.
2. **Debloat Assistant**: Freezes, unfreezes, uninstalls (user 0), and restores preloaded OEM bloatware, tracking packages, telemetry, and background services without needing a PC or external bridge app.

---

## 💡 How It Works

### 1. Direct Share Privacy
Android dynamically registers recent chat contacts as shortcuts via `ShortcutManager`. When you open the Share Sheet, the system queries these dynamic shortcuts and places recent contacts right on top of the menu (Direct Share).

Because standard Android sandboxing prevents regular applications from clearing other apps' shortcuts, **HideThatPeoples** interacts directly with Android's local debugging daemon (`adbd`) over loopback TLS or via root shell (`su`) to invoke:

```bash
cmd shortcut clear-shortcuts <target_package>
```

### 2. Debloat & App Freezing Engine
Using elevated ADB / Root privileges, the app executes native package management operations safely on the current user profile (User 0):
- **Freeze / Disable**: `pm disable-user --user 0 <package>`
- **Unfreeze / Enable**: `pm enable <package>`
- **Uninstall (User 0)**: `pm uninstall -k --user 0 <package>`
- **Reinstall / Restore**: `cmd package install-existing <package>`

All changes are non-destructive to system partitions and fully reversible.

---

## ✨ Key Features

* **Built-in Wireless ADB & Root**:
  - Standalone TLS connection and pairing engine.
  - **Notification Direct-Reply Pairing**: Type the pairing code directly into Android's notification shade — no split-screen or multi-window needed.
  - **mDNS Zero-Config Discovery**: Automatically discovers active pairing and connection ports.
  - Seamless fallback to `su` on rooted devices.
* **1-Tap Direct Share Cleaner**: Instant hero control to wipe direct share shortcuts on demand.
* **Debloat Assistant**:
  - Curated presets across popular vendors and third-party bloat (Meta/Facebook, Netflix, Spotify, TikTok, Microsoft, Xiaomi/MIUI ads & telemetry, Samsung Bixby, Google Suite).
  - Categorized packages: User Apps, System Bloat, Carrier/OEM, and Complete System Apps.
  - Live search and filter by package name or label.
  - Real-time status indicators (*Enabled*, *Disabled/Frozen*, *Uninstalled*).
  - Contextual action sheets with safety warnings and one-click operations.
* **Floating Bottom Navigation**: Floating pills overlay (`Hide` & `Debloat`) with fluid full-screen edge-to-edge scrolling.
* **Quick Settings Tile ("Hide Peoples")**: 1-tap quick cleaning directly from your notification panel before screen sharing, presenting, or sharing in public (with automatic background reconnect).
* **Background Auto-Clean**: Periodically schedules background sweeps via Android WorkManager with customizable intervals (15m to 24h).
* **Target App Customization**: Filter and select from installed chat and social apps (WhatsApp, Telegram, Signal, Discord, Instagram, Messages, etc.).
* **Zero Telemetry & 100% Offline**: Zero external network requests, zero third-party tracking, and 100% open-source transparency.

---

## 📱 Requirements

* **Non-Root (Recommended)**: Android 11.0+ (API 30+) with Wi-Fi / Wireless Debugging enabled in Developer Options.
* **Rooted**: Android 8.0+ (API 26+) with Magisk, KernelSU, or APatch.

---

## 🔒 Permission Transparency

| Permission | Purpose |
|---|---|
| `INTERNET` | Connects strictly to `127.0.0.1` (loopback) to communicate with local on-device `adbd`. |
| `ACCESS_WIFI_STATE` & `CHANGE_WIFI_MULTICAST_STATE` | Discovers active Wireless Debugging ports via mDNS (`NsdManager`). |
| `QUERY_ALL_PACKAGES` | Lists installed messaging apps & system packages to allow shortcut clearing and debloating. |
| `POST_NOTIFICATIONS` | Displays the notification pairing prompt to enter pairing codes without leaving Settings. |
| `RECEIVE_BOOT_COMPLETED` | Restores the periodic background Auto-Clean schedule after device reboots. |

---

## 🚀 Building From Source

### Prerequisites
* JDK 17
* Android SDK 35 / 36

### Package Details
- **Application ID**: `com.neflalabs.hidethatpeoples`
- **Launcher Display Name**: `HidePeoples`

### Build Debug APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/HideThatPeoples-release.apk`

### Run Unit Tests
```bash
./gradlew test
```

---

## 📄 License
Released under the [MIT License](LICENSE).  
Developed with care by **[neflalabs](https://github.com/neflalabs/hidethatpeoples)**.
