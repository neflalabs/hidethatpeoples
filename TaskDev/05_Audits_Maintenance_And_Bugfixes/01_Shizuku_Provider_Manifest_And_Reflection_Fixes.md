# 01 — Shizuku Provider Manifest And Reflection Fixes

**Metadata Tracking:**
* **Tanggal Dibuat:** `2026-09-08 19:30:00 WIB`
* **Pembaruan Terakhir:** `2026-09-08 19:48:00 WIB`
* **Status:** `Completed`
* **Target Versi / CalVer:** `v2026.09.08`
* **Kategori:** `Audits & Maintenance`
* **Komponen Terkait:** `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/nefla/hidethatpeoples/shizuku/ShizukuManager.kt`, `app/src/main/java/com/nefla/hidethatpeoples/data/TargetApp.kt`, `app/src/main/java/com/nefla/hidethatpeoples/data/AppPreferences.kt`
* **Tags:** `[#bugfix, #shizuku_provider, #reflection, #gmail, #rca]`

---

## 🎯 1. Ringkasan Eksekutif & Stop-the-Line Triage

### 1.1 Ringkasan Temuan
Selama tahap kompilasi awal dan uji coba runtime langsung pada perangkat fisik (POCO X3 NFC / surya, Android 16), ditemukan 3 kendala teknis krusial:
1. **Compile Failure (Kotlin Error)**: Method `Shizuku.newProcess` tidak dapat diakses langsung karena diubah menjadi `private` pada Shizuku API library v13+.
2. **Runtime IPC Failure (Provider Null)**: Layanan Shizuku di HP tidak dapat mengirim binder ke aplikasi, menyebabkan status di layar tetap *"Not Running"*. Logcat mencatat: `Service : provider is null com.nefla.hidethatpeoples.shizuku 0`.
3. **Target Extension (Gmail Integration)**: Kebutuhan penambahan target pembersihan kontak untuk aplikasi Gmail (`com.google.android.gm`).

### 1.2 Stop-the-Line Checklist
- [x] Reproduksi kegagalan kompilasi dan runtime dikonfirmasi via logcat dan compiler output.
- [x] Akar masalah (*Root Cause Analysis*) dianalisis hingga ke level bytecode dan manifest merger.
- [x] Perbaikan diimplementasikan secara berurutan dan diverifikasi langsung pada device target.

---

## 🔍 2. Temuan Audit, RCA & Prove-It Fixes

### 2.1 🚨 Isu 1: Shizuku `newProcess` Accessibility Compilation Error
* **File Target**: `app/src/main/java/com/nefla/hidethatpeoples/shizuku/ShizukuManager.kt`
* **Gejala / Error**:
  ```text
  e: .../ShizukuManager.kt:53:35 Cannot access 'static fun newProcess(p0: Array<(out) String!>, p1: Array<(out) String!>?, p2: String?): ShizukuRemoteProcess!': it is private in 'rikka/shizuku/Shizuku'.
  ```
* **Akar Masalah (RCA)**:
  Pada Shizuku API versi `13.1.5`, tim pengembang Shizuku secara sengaja mengubah visibilitas `newProcess()` menjadi private untuk mendorong penggunaan AIDL `UserService`. Namun, method tersebut tetap ada di dalam bytecode `classes.jar` dan kelas `ShizukuRemoteProcess` tetap mengimplementasikan `java.lang.Process`.
* **Cuplikan Perbaikan Kode**:
  ```kotlin
  // ❌ Pemanggilan langsung (Gagal kompilasi):
  val process = Shizuku.newProcess(arrayOf("cmd", "shortcut", "clear-shortcuts", packageName), null, null)

  // ✅ Solusi perbaikan via Reflection:
  val newProcessMethod: Method = Shizuku::class.java.getDeclaredMethod(
      "newProcess",
      Array<String>::class.java,
      Array<String>::class.java,
      String::class.java
  )
  newProcessMethod.isAccessible = true
  val process = newProcessMethod.invoke(
      null,
      arrayOf("cmd", "shortcut", "clear-shortcuts", packageName),
      null,
      null
  ) as Process
  ```

---

### 2.2 🚨 Isu 2: Shizuku Binder Provider Null di Runtime
* **File Target**: `app/src/main/AndroidManifest.xml`
* **Gejala / Error**:
  Aplikasi menampilkan banner merah *"Shizuku Not Running"*, meskipun `shizuku_server` sudah aktif dengan PID 7493 di HP. Logcat Android mencatat:
  ```text
  09-08 19:39:53.456  7493  7502 E Service : provider is null com.nefla.hidethatpeoples.shizuku 0
  ```
* **Akar Masalah (RCA)**:
  Shizuku bekerja melalui mekanisme ContentProvider untuk mengirimkan Binder interface dari daemon ke aplikasi klien. Library AAR `dev.rikka.shizuku:provider` membutuhkan deklarasi eksplisit `rikka.shizuku.ShizukuProvider` dengan atribut authority `${applicationId}.shizuku`. Karena belum dideklarasikan di manifest aplikasi, `shizuku_server` gagal menemukan titik masuk komunikasi IPC.
* **Cuplikan Perbaikan Kode**:
  ```xml
  <!-- ❌ Sebelum: Provider tidak ada di AndroidManifest.xml -->

  <!-- ✅ Sesudah: Ditambahkan ke dalam tag <application> di AndroidManifest.xml -->
  <provider
      android:name="rikka.shizuku.ShizukuProvider"
      android:authorities="${applicationId}.shizuku"
      android:multiprocess="false"
      android:enabled="true"
      android:exported="true"
      android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
  ```
* **Verifikasi Runtime (Logcat Sukses)**:
  ```text
  09-08 19:41:11.293  8280  8298 D ShizukuProvider: binder received
  09-08 19:41:11.296  8280  8294 D ShizukuProvider: binder received
  ```

---

### 2.3 🚨 Isu 3: Dukungan Pembersihan Kontak Gmail (`com.google.android.gm`)
* **File Target**: `app/src/main/AndroidManifest.xml`, `data/TargetApp.kt`, `data/AppPreferences.kt`
* **Kebutuhan**:
  Aplikasi Gmail juga sering menyodorkan saran kontak email/chat pada Direct Share sheet dan perlu dimasukkan ke dalam daftar target cleaner.
* **Solusi & Mitigasi Preference Caching**:
  1. Menambahkan deklarasi `<package android:name="com.google.android.gm" />` pada `<queries>` di `AndroidManifest.xml`.
  2. Menambahkan `TargetApp("com.google.android.gm", "Gmail")` pada `DEFAULT_TARGETS`.
  3. Memodifikasi getter `AppPreferences.enabledPackages` agar target default baru yang belum pernah dimatikan secara eksplisit oleh user otomatis terintegrasi ke dalam set yang aktif tanpa harus menghapus data aplikasi (*clear data*).

---

## 📋 3. Matriks Hasil Implementasi & Verifikasi Tes

| No | Modul / File | Isu yang Diperbaiki | Status Perbaikan | Hasil Test |
|---|---|---|---|---|
| 1 | `ShizukuManager.kt` | Method accessibility compilation error | **Tuntas** | ✅ Compile Pass |
| 2 | `AndroidManifest.xml` | `ShizukuProvider` null authority di runtime | **Tuntas** | ✅ Binder Received |
| 3 | `TargetApp.kt` & `AppPreferences.kt` | Dukungan target Gmail & auto-merge default | **Tuntas** | ✅ Pass & Deployed |

---

## ⏱️ 4. Riwayat Revisi & Audit Trail
| Tanggal & Waktu | Versi / Commit | Penulis | Ringkasan Perubahan |
|---|---|---|---|
| `2026-09-08 19:32 WIB` | `v2026.09.08` | Antigravity | Investigasi RCA dan perbaikan reflection Shizuku |
| `2026-09-08 19:41 WIB` | `v2026.09.08` | Antigravity | Implementasi deklarasi ShizukuProvider di manifest |
| `2026-09-08 19:43 WIB` | `v2026.09.08` | Antigravity | Penambahan target Gmail dan deployment build terbaru |
