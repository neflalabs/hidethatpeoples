# 02 — Batch Execution Watchdog And Shizuku Deprecation

**Metadata Tracking:**
* **Tanggal Dibuat:** `2026-09-09 11:10:53 WIB`
* **Pembaruan Terakhir:** `2026-09-09 11:15:00 WIB`
* **Status:** `Completed`
* **Target Versi / CalVer:** `v2026.09.09`
* **Kategori:** `Audits Maintenance And Bugfixes`
* **Komponen Terkait:** `LocalAdbPrivilegeProvider.kt`, `HomeScreen.kt`, `AutoCleanWorker.kt`, `PrivilegeManager.kt`, `AppPreferences.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`
* **Tags:** `[#adb, #batching, #bugfix, #watchdog, #shizuku-deprecation, #performance]`

---

## 🎯 1. Ringkasan Eksekutif & Stop-the-Line Triage

### 1.1 Ringkasan Temuan
Sesi audit dan pemeliharaan ini dilakukan untuk merespons serangkaian masalah fungsional kritis pada fitur Direct Share Cleaner di perangkat **POCO X3 NFC (Android 16 / SDK 36)**:
1. **"Loading Melulu" saat Pembersihan:** Tombol `Clear Contacts Now` berputar tanpa henti (*infinite spinner*). Analisis logcat membuktikan bahwa proses pembersihan terhenti (*hang*) hingga 47 detik pada aplikasi ke-11 akibat pembukaan puluhan *stream* TLS ADB beruntun secara sekuensial.
2. **UI State Lock (Deadlock State):** Ketiadaan blok `try ... finally` pada coroutine pembersihan di `HomeScreen.kt` menyebabkan state `isClearing` terkunci di status `true` selamanya jika terjadi interupsi atau socket timeout.
3. **Timestamp "Last Cleared" Tidak Terbarui:** Pembersihan yang dieksekusi dari *Quick Settings Tile* maupun *AutoCleanWorker* tidak merefleksikan perubahan waktu pada layar utama karena pembacaan preferensi bersifat statis (*one-shot*).
4. **Residu Komponen Shizuku:** Inkonsistensi UX akibat adanya sisa tombol/chip Shizuku yang sudah tidak didukung lagi, sementara arsitektur aplikasi telah sepenuhnya beralih ke *Built-in Local Wireless ADB Engine*.

### 1.2 Stop-the-Line Checklist
- [x] Reproduksi bug berhasil dikonfirmasi secara konsisten melalui live logcat perangkat POCO.
- [x] Failing point terisolasi pada pemanggilan `AdbStream.openInputStream()` dan `BufferedReader.readLine()` tanpa timeout.
- [x] Pengujian performa batch shell script berhasil memangkas durasi eksekusi dari >45 detik menjadi **~1,1 detik**.
- [x] Build dan instalasi berhasil diverifikasi secara langsung pada target device hardware.

---

## 🔍 2. Temuan Audit, RCA & Prove-It Fixes

### 2.1 🚨 Bug 1: ADB Stream Sequential TLS Stall ("Loading Melulu")
* **File Target**: `LocalAdbPrivilegeProvider.kt`
* **Akar Masalah (RCA)**:
  Implementasi lama dari `clearMultipleShortcuts()` melakukan perulangan `for (pkg in packages)` dan memanggil `clearShortcuts(pkg)` satu per satu. Setiap panggilan membuka ADB channel baru (`AbsAdbConnectionManager.openStream()`) melalui koneksi TLS TCP lokal. Pada perangkat dengan 39 target direct share apps, pembukaan 39 TLS stream secara cepat menyebabkan `adbd` socket window mengalami *backpressure* / stalling pada remote EOF, sehingga pembacaan `reader.readLine()` memblokir thread IO tanpa batas waktu (*infinite blocking*).
* **Cuplikan Perbaikan Kode**:
  ```kotlin
  // ❌ Kode bermasalah (Membuka 30+ stream sekuensial terpisah):
  override suspend fun clearMultipleShortcuts(packages: Collection<String>): Map<String, Boolean> {
      for (pkg in packages) {
          val res = clearShortcuts(pkg) // Membuka & menutup stream per package
          results[pkg] = res.isSuccess
      }
  }

  // ✅ Solusi perbaikan (Batch loop dalam 1 shell ADB stream + Watchdog):
  override suspend fun clearMultipleShortcuts(packages: Collection<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
      val results = mutableMapOf<String, Boolean>()
      val chunks = packages.chunked(25)
      for (chunk in chunks) {
          try {
              val pkgListStr = chunk.joinToString(" ")
              val command = "shell:for p in $pkgListStr; do res=\$(cmd shortcut clear-shortcuts --user 0 \"\$p\" 2>&1); echo \"\$p:\$res\"; done"
              val stream = openStream(command)
              val watchdog = scope.launch {
                  delay(10000L)
                  try { stream.close() } catch (_: Throwable) {}
              }
              try {
                  val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
                  var line: String?
                  var receivedCount = 0
                  while (reader.readLine().also { line = it } != null) {
                      val currentLine = line?.trim() ?: continue
                      if (currentLine.contains(":")) {
                          val parts = currentLine.split(":", limit = 2)
                          results[parts[0].trim()] = parts[1].contains("Success", ignoreCase = true)
                          receivedCount++
                      }
                      if (receivedCount >= chunk.size) break
                  }
              } finally {
                  watchdog.cancel()
                  try { stream.close() } catch (_: Throwable) {}
              }
          } catch (e: Throwable) { ... }
      }
      return@withContext results
  }
  ```

---

### 2.2 🚨 Bug 2: UI State Lock & Missing Try-Finally di HomeScreen
* **File Target**: `HomeScreen.kt`
* **Akar Masalah (RCA)**:
  State `isClearing` diaktifkan (`true`) saat user menekan tombol "Clear Contacts Now". Jika terjadi *unhandled exception*, socket error, atau timeout, baris `isClearing = false` dilewati karena tidak berada di dalam blok `finally`. Akibatnya, tombol `Clear Contacts Now` terkunci dalam kondisi spinner loading secara permanen.
* **Cuplikan Perbaikan Kode**:
  ```kotlin
  // ❌ Kode bermasalah:
  scope.launch {
      isClearing = true
      val results = privilegeManager.clearMultipleShortcuts(enabledPackages)
      ...
      isClearing = false
  }

  // ✅ Solusi perbaikan:
  scope.launch {
      try {
          isClearing = true
          val results = withTimeoutOrNull(15000L) {
              privilegeManager.clearMultipleShortcuts(enabledPackages)
          } ?: emptyMap()
          val count = results.values.count { it }
          val now = System.currentTimeMillis()
          prefs.lastClearedTimestamp = now
          currentTicker = now
          snackbarHostState.showSnackbar("Successfully cleared shortcuts for $count apps!")
      } catch (e: Throwable) {
          snackbarHostState.showSnackbar("Error clearing shortcuts: ${e.message}")
      } finally {
          isClearing = false
      }
  }
  ```

---

### 2.3 🚨 Bug 3: Timestamp & Preferences Reaktivitas Hilang
* **File Target**: `AppPreferences.kt`, `HomeScreen.kt`
* **Akar Masalah (RCA)**:
  `HomeScreen` membaca `prefs.lastClearedTimestamp` dan `prefs.enabledPackages` menggunakan mutable state lokal yang diinisialisasi hanya satu kali (`remember { mutableStateOf(...) }`). Ketika *Quick Settings Tile* atau *AutoCleanWorker* memperbarui timestamp di background, UI tidak menerima notifikasi pembaruan nilai.
* **Cuplikan Perbaikan Kode**:
  ```kotlin
  // ✅ Menambahkan Reactive CallbackFlows di AppPreferences:
  val lastClearedTimestampFlow: Flow<Long> = callbackFlow {
      trySend(lastClearedTimestamp)
      val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
          if (key == KEY_LAST_CLEARED_TIMESTAMP) trySend(lastClearedTimestamp)
      }
      prefs.registerOnSharedPreferenceChangeListener(listener)
      awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
  }

  // ✅ Integrasi di HomeScreen:
  val lastClearedTimestamp by prefs.lastClearedTimestampFlow.collectAsStateWithLifecycle(initialValue = prefs.lastClearedTimestamp)
  val enabledPackages by prefs.enabledPackagesFlow.collectAsStateWithLifecycle(initialValue = prefs.enabledPackages)
  ```

---

### 2.4 🧹 Refaktor: Eliminasi Total Komponen Shizuku
* **File Target**: `app/build.gradle.kts`, `AndroidManifest.xml`, `PrivilegeType.kt`, `ClearShortcutsTileService.kt`
* **File Dihapus**:
  - `ShizukuPrivilegeProvider.kt`
  - `ShizukuManager.kt`
* **Tindakan**:
  1. Menghapus dependensi `dev.rikka.shizuku:api` dan `dev.rikka.shizuku:provider` dari `build.gradle.kts`.
  2. Menghapus deklarasi `<queries><package android:name="moe.shizuku.privileged.api" /></queries>` dan `rikka.shizuku.ShizukuProvider` dari `AndroidManifest.xml`.
  3. Menghapus opsi enum `SHIZUKU` dari `PrivilegeType.kt`.
  4. Menyederhanakan `PrivilegeManager.kt` untuk mendelegasikan 100% operasi ke `LocalAdbPrivilegeProvider`.

---

## 📋 3. Matriks Hasil Implementasi & Verifikasi Tes

| No | Modul / File | Isu yang Diperbaiki | Status Perbaikan | Hasil Verifikasi |
|---|---|---|:---:|:---|
| 1 | `LocalAdbPrivilegeProvider.kt` | Batching shell command + coroutine watchdog timer | **Tuntas** | ✅ Pembersihan 39 apps tuntas dalam 1.1s |
| 2 | `HomeScreen.kt` | Safeguard `try-finally`, UI timeout, reactive flows | **Tuntas** | ✅ Spinner tidak pernah stuck |
| 3 | `AppPreferences.kt` | Reaktivitas preferensi via `callbackFlow` | **Tuntas** | ✅ Timestamp langsung terupdate |
| 4 | `AutoCleanWorker.kt` | Background ADB reconnect + immediate one-time run | **Tuntas** | ✅ 39/39 sukses pada schedule |
| 5 | `AndroidManifest.xml` & `build.gradle.kts` | Pembersihan total sisa Shizuku | **Tuntas** | ✅ Build APK bersih tanpa Shizuku |

### 📊 Bukti Verifikasi Runtime (Logcat POCO X3 NFC):
```text
09-09 10:44:10.796 27070 27150 D LocalAdbProvider: Batch cleared shortcut for com.instagram.android: Success
09-09 10:44:10.865 27070 27150 D LocalAdbProvider: Batch cleared shortcut for com.mikrotik.android.tikapp: Success
09-09 10:44:10.925 27070 27150 D LocalAdbProvider: Batch cleared shortcut for com.google.android.apps.messaging: Success
09-09 10:44:10.976 27070 27150 D LocalAdbProvider: Batch cleared shortcut for org.lineageos.aperture: Success
...
09-09 10:44:11.911 27070 27150 D LocalAdbProvider: Batch cleared shortcut for com.discord: Success
09-09 10:44:11.940 27070 27150 D LocalAdbProvider: Batch cleared shortcut for com.google.android.calendar: Success
09-09 10:44:11.940 27070 27150 D AutoCleanWorker: AutoCleanWorker completed with 39 successes out of 39
```

---

## ⏱️ 4. Riwayat Revisi & Audit Trail
| Tanggal & Waktu | Versi / Commit | Penulis | Ringkasan Perubahan |
|---|---|---|---|
| `2026-09-09 11:15:00 WIB` | `v2026.09.09` | AI / Developer | Dokumentasi tuntas: RCA batch execution watchdog, perbaikan reaktivitas timestamp, dan eliminasi Shizuku |
