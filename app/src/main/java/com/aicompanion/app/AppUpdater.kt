package com.aicompanion.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.aicompanion.core.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val isForceUpdate: Boolean = false,
    val fileSize: Long = 0
)

sealed class UpdateState {
    object Checking : UpdateState()
    object NoUpdate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class Ready(val file: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class AppUpdater(private val context: Context) {

    private val updateCheckUrl = "https://api.example.com/app/update.json" // Configure

    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null

    fun checkForUpdates(currentVersionCode: Int): Flow<UpdateState> = flow {
        emit(UpdateState.Checking)

        try {
            val json = withContext(Dispatchers.IO) {
                URL(updateCheckUrl).readText()
            }
            val obj = JSONObject(json)
            val latestCode = obj.getInt("versionCode")
            val latestName = obj.getString("versionName")
            val url = obj.getString("downloadUrl")
            val changelog = obj.optString("changelog", "")
            val forceUpdate = obj.optBoolean("forceUpdate", false)
            val fileSize = obj.optLong("fileSize", 0)

            if (latestCode > currentVersionCode) {
                emit(
                    UpdateState.Available(
                        UpdateInfo(
                            versionCode = latestCode,
                            versionName = latestName,
                            downloadUrl = url,
                            changelog = changelog,
                            isForceUpdate = forceUpdate,
                            fileSize = fileSize
                        )
                    )
                )
            } else {
                emit(UpdateState.NoUpdate)
            }
        } catch (e: Exception) {
            emit(UpdateState.Error("检查更新失败: ${e.message}"))
        }
    }

    fun downloadUpdate(info: UpdateInfo): Flow<UpdateState> = flow {
        emit(UpdateState.Downloading(0f))

        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val apkFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "ai_companion_${info.versionName}.apk"
            )
            apkFile.delete()

            val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
                .setTitle("AI陪伴 更新下载")
                .setDescription("正在下载 v${info.versionName}")
                .setDestinationUri(Uri.fromFile(apkFile))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)

            downloadId = downloadManager.enqueue(request)

            // Monitor download progress
            val downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val totalBytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    val progress = if (totalBytes > 0) {
                        bytesDownloaded.toFloat() / totalBytes.toFloat()
                    } else 0f
                    emit(UpdateState.Downloading(progress))

                    if (bytesDownloaded == totalBytes && totalBytes > 0) {
                        emit(UpdateState.Ready(apkFile))
                        break
                    }
                }
                cursor.close()
                kotlinx.coroutines.delay(500)
            }
        } catch (e: Exception) {
            emit(UpdateState.Error("下载失败: ${e.message}"))
        }
    }

    fun installApk(file: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun cancelDownload() {
        if (downloadId != -1L) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(downloadId)
        }
    }
}
