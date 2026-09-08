package com.nefla.hidethatpeoples.shizuku

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

object ShizukuManager {
    private const val TAG = "ShizukuManager"

    enum class ShizukuState {
        NOT_RUNNING,
        PERMISSION_REQUIRED,
        READY
    }

    fun getShizukuState(): ShizukuState {
        return try {
            if (!Shizuku.pingBinder()) {
                ShizukuState.NOT_RUNNING
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuState.READY
            } else {
                ShizukuState.PERMISSION_REQUIRED
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error checking Shizuku state", e)
            ShizukuState.NOT_RUNNING
        }
    }

    fun isReady(): Boolean = getShizukuState() == ShizukuState.READY

    fun requestPermission(requestCode: Int = 1001) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    suspend fun clearShortcuts(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isReady()) {
            return@withContext Result.failure(IllegalStateException("Shizuku is not ready or permission denied"))
        }

        try {
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

            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val errorOutput = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                errorOutput.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.d(TAG, "Successfully cleared shortcuts for $packageName: $output")
                Result.success(output.toString().trim())
            } else {
                val errMsg = "Failed to clear shortcuts for $packageName (code $exitCode): $errorOutput"
                Log.e(TAG, errMsg)
                Result.failure(RuntimeException(errMsg))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception clearing shortcuts for $packageName", e)
            Result.failure(e)
        }
    }

    suspend fun clearMultipleShortcuts(packages: Collection<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()
        for (pkg in packages) {
            val res = clearShortcuts(pkg)
            results[pkg] = res.isSuccess
        }
        results
    }
}
