# HideThatPeoples 🙈

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

An on-device privacy utility built with Jetpack Compose, Material 3, and a **Built-in Standalone Wireless ADB Engine** (with optional Root support) to instantly remove direct share contact recommendations (people suggestions) from the Android Share Sheet.

---

## 💡 How It Works

Android dynamically registers recent chat contacts as shortcuts via `ShortcutManager`. When you open the Share Sheet, the system queries these dynamic shortcuts and places recent chats right on top of the menu (Direct Share).

Because standard Android sandboxing prevents regular applications from clearing other apps' shortcuts, **HideThatPeoples** interacts directly with Android's local debugging daemon (`adbd`) over loopback TLS or via root shell (`su`) to invoke:

```bash
cmd shortcut clear-shortcuts <target_package>
```

- **Zero Bridge Apps**: Standalone built-in ADB client — no PC and no Shizuku required after initial on-device pairing.
- **Root & Non-Root Support**: Works without root via standard Android Wireless Debugging, with instant fallback to `su` on rooted devices.
- **Privacy First**: 100% on-device operation with zero telemetry, zero analytics, and zero external network requests.
- **Ultra-Fast Batch Engine**: Sweeps 40+ messaging apps in ~1 second via single-stream batch execution.

---

## ✨ Key Features

* **Built-in Wireless ADB & Root**:
  - Standalone TLS connection and pairing engine.
  - **Notification Direct-Reply Pairing**: Type the pairing code directly into Android's notification shade — no split-screen needed.
  - mDNS Zero-Config Discovery: Automatically discovers active pairing and connection ports.
* **1-Tap Direct Share Cleaner**: Instant dashboard button to clear direct share shortcuts.
* **Quick Settings Tile ("Hide Peoples")**: 1-tap quick cleaning directly from your notification panel before screen sharing, presenting, or sharing in public (with automatic background reconnect).
* **Pull-to-Refresh**: Swipe down on the main screen to refresh target apps and connection status.
* **Target App Customization**: Filter and select from installed chat and social apps (WhatsApp, Telegram, Signal, Discord, Instagram, Messages, etc.).
* **Background Auto-Clean**: Periodically schedules background sweeps via Android WorkManager with customizable intervals (15m to 24h).

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
| `QUERY_ALL_PACKAGES` | Lists installed messaging & social applications to allow shortcut clearing. |
| `POST_NOTIFICATIONS` | Displays the notification pairing prompt to enter pairing codes without leaving Settings. |
| `RECEIVE_BOOT_COMPLETED` | Restores the periodic background Auto-Clean schedule after device reboots. |

---

## 🚀 Building From Source

### Prerequisites
* JDK 17
* Android SDK 35 / 36

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
Created with care by **neflalabs**.
