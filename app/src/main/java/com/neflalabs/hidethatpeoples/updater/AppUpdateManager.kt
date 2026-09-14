package com.neflalabs.hidethatpeoples.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.neflalabs.hidethatpeoples.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val htmlUrl: String,
    val isNewer: Boolean
)

sealed interface UpdateCheckState {
    object Idle : UpdateCheckState
    object Checking : UpdateCheckState
    data class UpdateAvailable(val info: ReleaseInfo) : UpdateCheckState
    object UpToDate : UpdateCheckState
    data class Downloading(val downloadId: Long) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val GITHUB_REPO_API = "https://api.github.com/repos/neflalabs/hidethatpeoples/releases/latest"

    private var activeDownloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null

    suspend fun checkLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_REPO_API)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "HideThatPeoples-AndroidApp")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP Error: $responseCode"))
            }

            val jsonString = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(jsonString)

            val tagName = json.optString("tag_name", "").trim()
            val releaseNotes = json.optString("body", "").trim()
            val htmlUrl = json.optString("html_url", "https://github.com/neflalabs/hidethatpeoples/releases")

            // Parse APK asset
            var apkDownloadUrl = ""
            val assets = json.optJSONArray("assets") ?: JSONArray()
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val browserDownloadUrl = asset.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = browserDownloadUrl
                    break
                }
            }

            // Fallback if no apk asset in release
            if (apkDownloadUrl.isEmpty()) {
                apkDownloadUrl = htmlUrl
            }

            val remoteVersion = tagName.removePrefix("v").trim()
            val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()
            val isNewer = isVersionNewer(remoteVersion, currentVersion)

            Result.success(
                ReleaseInfo(
                    tagName = tagName,
                    versionName = remoteVersion,
                    releaseNotes = releaseNotes,
                    downloadUrl = apkDownloadUrl,
                    htmlUrl = htmlUrl,
                    isNewer = isNewer
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking latest release", e)
            Result.failure(e)
        }
    }

    fun isVersionNewer(remote: String, current: String): Boolean {
        if (remote.isBlank() || current.isBlank()) return false
        if (remote == current) return false

        val remoteParts = remote.split("-", "_")[0].split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split("-", "_")[0].split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun downloadAndInstall(
        context: Context,
        releaseInfo: ReleaseInfo,
        onDownloadStarted: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        val downloadUrl = releaseInfo.downloadUrl
        if (downloadUrl.isEmpty() || !downloadUrl.endsWith(".apk", ignoreCase = true)) {
            // If direct APK not found, open release page in browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.htmlUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                onError("Failed to open browser: ${e.localizedMessage}")
            }
            return
        }

        try {
            val fileName = "HideThatPeoples_${releaseInfo.versionName}.apk"
            val destinationFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("HideThatPeoples v${releaseInfo.versionName}")
                setDescription("Downloading latest update...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            activeDownloadId = downloadId
            onDownloadStarted(downloadId)

            // Register receiver for completion
            downloadReceiver?.let {
                try {
                    context.applicationContext.unregisterReceiver(it)
                } catch (_: Exception) {}
            }

            downloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(recvCtx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id == activeDownloadId) {
                        try {
                            recvCtx?.applicationContext?.unregisterReceiver(this)
                        } catch (_: Exception) {}
                        downloadReceiver = null
                        installApk(context, destinationFile)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(
                    downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.applicationContext.registerReceiver(
                    downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download initiation failed", e)
            onError("Failed to start download: ${e.localizedMessage}")
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
            return
        }

        // Check Unknown Source Installation permission for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(manageIntent)
                // Also trigger install intent so user can tap it once granted
            }
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
        }
    }
}
