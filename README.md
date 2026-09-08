# HideThatPeoples 🙈

A modern Android utility built with Jetpack Compose, Material 3, and Shizuku to instantly hide/clear direct share contact suggestions (WhatsApp, Telegram, Google Messages, Signal, etc.) from the Android Share Sheet.

---

## 💡 How It Works

Android dynamically registers recent contacts as shortcuts via `ShortcutManager`. When you open the Share Sheet, the system queries these dynamic shortcuts and places recent chats right on top of the menu (Direct Share).

Because Android restricts standard apps from clearing other apps' shortcuts, **HideThatPeoples** leverages **Shizuku (ADB privileges via Wireless Debugging)** to safely invoke the system command:
```bash
cmd shortcut clear-shortcuts <target_package>
```

- **100% Non-Root**: Uses standard Android Wireless Debugging.
- **Bank-App Safe**: Does not trigger SafetyNet / Play Integrity root flags.
- **Fast & Lightweight**: Clean Material 3 interface with dark mode and dynamic colors.

---

## ✨ Features

1. **Dashboard & Status**: Shows real-time Shizuku service status (Connected, Needs Permission, Offline) with quick grant buttons.
2. **1-Tap Direct Share Cleaner**: Instant button to sweep all active direct share contacts.
3. **App Customization**: Choose which messaging apps to clear (WhatsApp, WA Business, Telegram, Google Messages, Signal, Messenger, Instagram).
4. **Quick Settings Tile ("Hide Peoples")**: Add a tile to your Android pull-down notification panel for 1-tap quick cleaning right before sharing screens, presenting, or sharing in public.
5. **Background Auto-Clean**: Periodically schedules background sweeps via Android WorkManager so unwanted contacts don't linger.

---

## 🛠️ Requirements

- Android 8.0+ (API 26+)
- [Shizuku](https://shizuku.rikka.app/) installed and running (via Wireless Debugging or ADB).

---

## 🚀 Building & Installing

### Build Debug APK
```bash
./gradlew assembleDebug
```
Output APK:
`app/build/outputs/apk/debug/app-debug.apk`

### Install to connected device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```
