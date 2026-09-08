# 01 — System Architecture And Shizuku Direct Share Cleaner

**Metadata Tracking:**
* **Tanggal Dibuat:** `2026-09-08 19:16:00 WIB`
* **Pembaruan Terakhir:** `2026-09-08 19:48:00 WIB`
* **Status:** `Completed`
* **Target Versi / CalVer:** `v2026.09.08`
* **Kategori:** `Foundation & Architecture`
* **Komponen Terkait:** `app/src/main/java/com/nefla/hidethatpeoples/`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
* **Tags:** `[#shizuku, #direct_share, #jetpack_compose, #material3, #quick_tile, #workmanager]`

---

## 🎯 1. Latar Belakang & Success Criteria

### 1.1 Latar Belakang Masalah
Pada sistem operasi Android modern (Android 10 hingga Android 16), setiap kali pengguna berinteraksi atau mengirim pesan melalui aplikasi perpesanan (WhatsApp, Telegram, Google Messages, Signal, Gmail, dll.), aplikasi tersebut mendaftarkan kontak terkait sebagai *Dynamic Sharing Shortcuts* melalui API `ShortcutManager`.

Akibatnya, saat pengguna menekan tombol **Share** di aplikasi apa pun, Android Share Sheet menampilkan baris **Direct Share Targets** (daftar foto dan nama kontak) persis di baris paling atas menu. Hal ini memunculkan keluhan privasi dan kecanggungan (*awkwardness*), terutama ketika berbagi layar (*screen share*), presentasi, atau berada di tempat umum.

Di HP Samsung One UI, terdapat toggle resmi (*"Show contacts when sharing content"* / Good Lock Home Up) karena Samsung memegang kendali atas framework ROM One UI dan menandatangani aplikasinya dengan *Platform Signature*. Namun, di Stock Android/AOSP/Pixel/Xiaomi:
* Google telah mengunci komponen `ChooserActivity` di Android 12+ sehingga aplikasi pihak ketiga dilarang menggantikan menu share sistem (kasus gugurnya aplikasi `Sharedr`).
* Android Sandbox melarang aplikasi pihak ketiga biasa menghapus data/shortcut milik aplikasi lain tanpa hak istimewa sistem.

### 1.2 Success Criteria Terukur (Acceptance Criteria)
- [x] Membangun aplikasi Android non-root berbasis **Shizuku API** untuk mengeksekusi `cmd shortcut clear-shortcuts <target_pkg>` dengan hak akses UID Shell (2000).
- [x] Menyediakan UI modern dengan **Jetpack Compose + Material 3** yang menampilkan status koneksi Shizuku secara real-time.
- [x] Menyediakan aksi pembersihan 1-tap manual (**"Clear Direct Share Contacts Now"**).
- [x] Menyediakan **Quick Settings Tile ("Hide Peoples")** di status bar untuk pembersihan instan tanpa harus membuka aplikasi.
- [x] Menyediakan otomatisasi background terjadwal via **Android WorkManager**.
- [x] Mendukung aplikasi perpesanan populer: WhatsApp, WhatsApp Business, Telegram, Google Messages, Signal, Facebook Messenger, Instagram, dan Gmail.
- [x] Menjamin 100% Non-Root (Aman untuk m-Banking dan Play Integrity).

---

## 🏗️ 2. Arsitektur, Alur Sistem & Keputusan ADR-Lite

### 2.1 Diagram Topologi & Alur Eksekusi (ASCII)

```text
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           HideThatPeoples Application                           │
│                                                                                 │
│   ┌─────────────────────┐   ┌───────────────────────────┐   ┌───────────────┐   │
│   │ Compose Dashboard   │   │ Quick Settings Tile       │   │ WorkManager   │   │
│   │ (MainActivity.kt)   │   │ (ClearShortcutsTile.kt)   │   │ (AutoClean.kt)│   │
│   └──────────┬──────────┘   └─────────────┬─────────────┘   └───────┬───────┘   │
│              │                            │                         │           │
│              └──────────────────────┬─────┴─────────────────────────┘           │
│                                     ▼                                           │
│                       ┌───────────────────────────┐                             │
│                       │    ShizukuManager.kt      │                             │
│                       │ (Reflection Binder Bridge)│                             │
│                       └─────────────┬─────────────┘                             │
└─────────────────────────────────────┼───────────────────────────────────────────┘
                                      │ IPC Binder (UID 10473 -> UID 2000)
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    Shizuku Service Daemon (shizuku_server)                      │
│                                (UID Shell 2000)                                 │
│                                     │                                           │
│                 Executes: `cmd shortcut clear-shortcuts <pkg>`                  │
└─────────────────────────────────────┼───────────────────────────────────────────┘
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    Android OS ShortcutManagerService (Framework)                │
│                                                                                 │
│   • com.whatsapp                  ───> [Dynamic Shortcuts Cleared]              │
│   • org.telegram.messenger        ───> [Dynamic Shortcuts Cleared]              │
│   • com.google.android.apps.msg   ───> [Dynamic Shortcuts Cleared]              │
│   • com.google.android.gm (Gmail) ───> [Dynamic Shortcuts Cleared]              │
│                                                                                 │
│   Hasil di Android ChooserActivity: Baris Direct Share kontak KOSONG BERSIH.    │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Keputusan Arsitektur & Alternatif yang Dipertimbangkan (ADR-Lite)
* **Solusi Terpilih**: **Shizuku API (UID 2000 Shell via Wireless Debugging)**.
* **Alternatif Lain yang Dipertimbangkan**:
  1. *Opsi A: Screen Overlay / Accessibility Service* — **Ditolak karena:** Android 12+ memberlakukan Anti-Tapjacking (`setHideOverlayWindows(true)` & `FLAG_WINDOW_IS_OBSCURED`) yang menyebabkan overlay otomatis disembunyikan atau touch event ke tombol di bawahnya terblokir total.
  2. *Opsi B: Root / LSPosed Module Hooking* — **Ditolak karena:** Membutuhkan Unlock Bootloader & Root (Magisk/KernelSU), merusak integritas m-Banking dan garansi perangkat.
  3. *Opsi C: Intent Filter Replacement (Sharedr Pattern)* — **Ditolak karena:** Google secara sengaja memblokir penggantian `Intent.createChooser()` sejak Android 12 (Google IssueTracker #188611132).
* **Konsekuensi & Mitigasi**:
  * (+) 100% Non-Root, tidak memicu SafetyNet/Play Integrity.
  * (+) Bekerja universal di Xiaomi, Pixel, Oppo, Vivo, Motorola, dll.
  * (-) Pengguna harus mengaktifkan Shizuku sekali per reboot (disediakan status banner, tombol integrasi, dan runner otomatis via ADB).

---

## 🛡️ 3. Three-Tier Boundaries Matrix

| Tingkatan | Batasan & Aturan Eksekusi |
|---|---|
| **Always Do** | • Selalu validasi ketersediaan binder Shizuku sebelum memanggil eksekusi proses.<br/>• Selalu tangani proses IO stream (stdout/stderr) saat memanggil process shell agar tidak terjadi dead-lock.<br/>• Gunakan Coroutine `Dispatchers.IO` untuk eksekusi process.<br/>• Berikan feedback visual (Toast / Snackbar / Tile Subtitle) setiap kali eksekusi selesai. |
| **Ask First** | • Penambahan target package baru di luar aplikasi perpesanan.<br/>• Perubahan interval minimum WorkManager (rekomendasi Android >= 15 menit). |
| **Never Do** | • Dilarang meminta akses Root (`su`) jika pengguna menginginkan solusi non-root.<br/>• Dilarang menggunakan loop blocking tanpa jeda yang menguras baterai di latar belakang.<br/>• Dilarang memodifikasi data internal aplikasi lain di luar API `cmd shortcut`. |

---

## 🔍 4. Rincian Desain & Spesifikasi Teknis

### 4.1 Modul & Komponen Kunci
1. **`shizuku/ShizukuManager.kt`**:
   * Abstraksi status Shizuku (`NOT_RUNNING`, `PERMISSION_REQUIRED`, `READY`).
   * Listener binder sticky (`OnBinderReceivedListener`, `OnBinderDeadListener`).
   * Eksekusi aman `cmd shortcut clear-shortcuts <pkg>`.
2. **`tile/ClearShortcutsTileService.kt`**:
   * Quick Settings Tile bawaan sistem dengan ikon `ic_hide_contacts.xml`.
   * Update visual dinamis: status inactive -> active (Clearing...) -> subtitle ("Cleared!").
3. **`worker/AutoCleanWorker.kt`**:
   * Periodic WorkManager worker dengan interval dinamis (default 30 menit).
4. **`data/AppPreferences.kt` & `TargetApp.kt`**:
   * Pengelolaan konfigurasi aplikasi yang dicentang dan status auto-clean.
5. **`ui/HomeScreen.kt`**:
   * Dashboard interaktif Jetpack Compose, status card, quick clear button, target checklist, dan info dialog.

---

## 🍰 5. Vertical Slices & Execution History

### Slice 1: Setup Gradle, Compose & Shizuku Dependencies (Size: S) — ✅ Tuntas
* Inisialisasi Android Studio project di `/home/nefla/AndroidStudioProjects/hidethatpeoples`.
* Konfigurasi AGP `9.3.2`, Kotlin `2.2.10`, Compose BOM `2024.09.00`, dan Shizuku API `13.1.5`.

### Slice 2: Shizuku Service Bridge & Target Cleaner Engine (Size: M) — ✅ Tuntas
* Implementasi `ShizukuManager.kt` dengan coroutines dan error handling.
* Konfigurasi `TargetApp.kt` dan `AppPreferences.kt`.

### Slice 3: Quick Settings Tile & WorkManager Daemon (Size: M) — ✅ Tuntas
* Implementasi `ClearShortcutsTileService.kt` dan registrasi di `AndroidManifest.xml`.
* Integrasi `AutoCleanWorker.kt` dengan `PeriodicWorkRequestBuilder`.

### Slice 4: Material 3 UI & Edge-to-Edge Experience (Size: M) — ✅ Tuntas
* Implementasi `HomeScreen.kt`, `MainActivity.kt`, theme dark/light, dan adaptive vector icons.

### Slice 5: Perbaikan ShizukuProvider & Target Gmail Integration (Size: S) — ✅ Tuntas
* Resolusi `ShizukuProvider` di manifest dan penambahan target `com.google.android.gm`.

---

## 📋 6. Checkpoints & Matriks Verifikasi

| No | Komponen / Pengujian | Kriteria Keberhasilan | Status |
|---|---|---|---|
| 1 | Gradle Build | `./gradlew assembleDebug` sukses tanpa error | ✅ Pass (13s) |
| 2 | APK Installation | `adb install -r app-debug.apk` sukses | ✅ Pass (Streamed) |
| 3 | Shizuku Binder Connection | `ShizukuProvider: binder received` di logcat | ✅ Pass |
| 4 | Shizuku Server Execution | `shizuku_server` berjalan di device (UID 2000) | ✅ Pass (PID 7493) |
| 5 | Target Gmail Inclusion | `com.google.android.gm` muncul di UI & queries | ✅ Pass |

---

## ⏱️ 7. Riwayat Revisi & Audit Trail
| Tanggal & Waktu | Versi / Commit | Penulis | Ringkasan Perubahan |
|---|---|---|---|
| `2026-09-08 19:27 WIB` | `v2026.09.08` | Antigravity | Inisiasi struktur proyek, gradle wrapper, dan dependencies |
| `2026-09-08 19:32 WIB` | `v2026.09.08` | Antigravity | Implementasi fitur inti, UI Compose, Quick Tile, dan WorkManager |
| `2026-09-08 19:43 WIB` | `v2026.09.08` | Antigravity | Penambahan dukungan target aplikasi Gmail dan deployment ke device |
