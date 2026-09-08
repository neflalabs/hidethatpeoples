# OPERATIONAL RUNBOOK — HIDETHATPEOPLES

**Metadata:**
* **Tanggal Dibuat:** `2026-09-08 19:48:00 WIB`
* **Pembaruan Terakhir:** `2026-09-08 19:50:00 WIB`
* **Kategori:** `Quick Reference / Operational Runbook`
* **Tags:** `[#runbook, #cheatsheet, #adb, #shizuku]`

---

## 🎯 1. Ringkasan & Tujuan Dokumen
Dokumen ini berisi cheatsheet perintah operasional, prosedur build, deployment ADB, dan trik aktivasi Shizuku langsung melalui command line.

---

## 📋 2. Cheatsheet Perintah Kunci

### 2.1 Build & Deployment
```bash
# Kompilasi debug APK
./gradlew assembleDebug

# Install APK ke perangkat Android yang terhubung
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Buka aplikasi langsung di layar ponsel
adb shell am start -n com.nefla.hidethatpeoples/.MainActivity
```

### 2.2 Menjalankan Shizuku Service via ADB (Tanpa Pairing Wi-Fi Manual)
Jika ponsel terhubung ke PC/laptop via USB atau Wireless ADB, daemon Shizuku dapat dinyalakan seketika dengan mengeksekusi library starter bawaannya:
```bash
# Menyalakan Shizuku Server langsung dari native library yang terpasang
adb shell "/data/app/~~*/*shizuku*/*/lib/arm64/libshizuku.so"

# Verifikasi proses Shizuku yang sedang berjalan
adb shell ps -ef | grep shizuku
# Output yang diharapkan:
# shell <PID> 1 ... shizuku_server
```

### 2.3 Perintah Pembersihan Shortcut Manual (Testing & Verifikasi)
```bash
# WhatsApp
adb shell cmd shortcut clear-shortcuts com.whatsapp

# WhatsApp Business
adb shell cmd shortcut clear-shortcuts com.whatsapp.w4b

# Telegram
adb shell cmd shortcut clear-shortcuts org.telegram.messenger

# Google Messages
adb shell cmd shortcut clear-shortcuts com.google.android.apps.messaging

# Gmail
adb shell cmd shortcut clear-shortcuts com.google.android.gm
```

### 2.4 Memantau Logcat Aplikasi
```bash
# Pantau log Shizuku dan status aplikasi secara real-time
adb logcat -s ShizukuManager ShizukuProvider rikka.shizuku.Shizuku
```
