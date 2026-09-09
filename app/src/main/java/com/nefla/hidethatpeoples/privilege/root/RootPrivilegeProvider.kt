package com.nefla.hidethatpeoples.privilege.root

import android.util.Log
import com.nefla.hidethatpeoples.privilege.PrivilegeProvider
import com.nefla.hidethatpeoples.privilege.PrivilegeState
import com.nefla.hidethatpeoples.privilege.PrivilegeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class RootPrivilegeProvider : PrivilegeProvider {

    companion object {
        private const val TAG = "RootProvider"
        private val SU_PATHS = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        )
    }

    override val type: PrivilegeType = PrivilegeType.ROOT

    private val _state = MutableStateFlow<PrivilegeState>(PrivilegeState.Disconnected)
    override val state: StateFlow<PrivilegeState> = _state.asStateFlow()

    suspend fun checkRootAvailability(): Boolean = withContext(Dispatchers.IO) {
        val hasSuBinary = SU_PATHS.any { File(it).exists() } || canExecuteSu()
        if (!hasSuBinary) {
            _state.value = PrivilegeState.Disconnected
            return@withContext false
        }

        return@withContext try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            process.waitFor()
            val isRoot = output.contains("uid=0") || process.exitValue() == 0
            if (isRoot) {
                _state.value = PrivilegeState.Ready(PrivilegeType.ROOT, "Root Shell (su) Granted")
                true
            } else {
                _state.value = PrivilegeState.Disconnected
                false
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Root check failed: ${e.message}")
            _state.value = PrivilegeState.Disconnected
            false
        }
    }

    private fun canExecuteSu(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            output != null && output.isNotBlank()
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun clearShortcuts(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cmd = "cmd shortcut clear-shortcuts --user 0 \"$packageName\""
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 || output.contains("Success", ignoreCase = true)) {
                Result.success(output.ifEmpty { "Success" })
            } else {
                Result.failure(Exception("Root clear failed (exit $exitCode): $output"))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun clearMultipleShortcuts(packages: Collection<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        if (packages.isEmpty()) return@withContext emptyMap()
        val results = mutableMapOf<String, Boolean>()
        val chunks = packages.chunked(25)

        for (chunk in chunks) {
            try {
                val pkgListStr = chunk.joinToString(" ")
                val script = "for p in $pkgListStr; do res=\$(cmd shortcut clear-shortcuts --user 0 \"\$p\" 2>&1); echo \"\$p:\$res\"; done"
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.contains(":")) {
                        val parts = currentLine.split(":", limit = 2)
                        val pkg = parts[0].trim()
                        val res = parts[1].trim()
                        results[pkg] = res.contains("Success", ignoreCase = true) || !res.contains("Error", ignoreCase = true)
                    }
                }
                process.waitFor()
            } catch (e: Throwable) {
                Log.e(TAG, "Error executing root batch clear", e)
            }
        }
        return@withContext results
    }
}
