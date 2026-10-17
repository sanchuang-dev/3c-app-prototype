package cn.cgroup.prototype

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Fetches the latest published GitHub release, then delegates APK installation to Android. */
class AppUpdater(private val activity: Activity) {
    companion object {
        private const val API = "https://api.github.com/repos/sanchuang-dev/3c-app-prototype/releases/latest"
        private const val INSTALL_PERMISSION_REQUEST = 3101
        private val TAG = Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+$")
    }

    private data class Release(val version: String, val download: String, val size: Long, val digest: String?)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val manager = activity.getSystemService(Activity.DOWNLOAD_SERVICE) as DownloadManager
    private var checking = false
    private var activeDownload = -1L
    private var pendingInstall: File? = null
    private var destroyed = false

    fun check(silent: Boolean) {
        if (destroyed || checking || activeDownload != -1L) return
        checking = true
        if (!silent) Toast.makeText(activity, "正在检查更新…", Toast.LENGTH_SHORT).show()
        worker.execute {
            val result = runCatching { readLatest() }
            main.post {
                checking = false
                if (destroyed || activity.isFinishing) return@post
                result.onSuccess { release ->
                    val current = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0.0.0"
                    val latest = release.version.removePrefix("v")
                    if (compareVersions(latest, current) > 0) {
                        AlertDialog.Builder(activity).setTitle("发现新版本 " + release.version)
                            .setMessage("当前版本：" + current + "\n最新版本：" + latest +
                                "\n安装包：" + (release.size / 1024) + " KB\n\n下载后由系统确认安装。")
                            .setPositiveButton("下载并安装") { _, _ -> download(release) }
                            .setNegativeButton("稍后", null).show()
                    } else if (!silent) {
                        message("已是最新版本", "当前版本：" + current)
                    }
                }.onFailure { error ->
                    if (!silent) message("检查失败", error.message ?: "无法访问 GitHub")
                }
            }
        }
    }

    private fun compareVersions(remote: String, local: String): Int {
        val a = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val b = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0..2) {
            val diff = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    private fun readLatest(): Release {
        val connection = URL(API).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "3C-Prototype-Android")
        connection.useCaches = false
        try {
            require(connection.responseCode == 200) { "GitHub HTTP " + connection.responseCode }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            require(!json.optBoolean("draft") && !json.optBoolean("prerelease")) { "非正式版本" }
            val tag = json.getString("tag_name")
            require(TAG.matches(tag)) { "版本号格式不合法" }
            val name = "3C-Prototype-" + tag + ".apk"
            val assets = json.getJSONArray("assets")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == name } ?: error("Release 未包含安装包")
            val url = URL(asset.getString("browser_download_url"))
            require(url.protocol == "https" && url.host == "github.com" &&
                url.path == "/sanchuang-dev/3c-app-prototype/releases/download/" + tag + "/" + name) {
                "安装包地址不受信任"
            }
            val size = asset.optLong("size", -1)
            require(size in 1..(250L * 1024 * 1024)) { "安装包大小异常" }
            val digest = asset.optString("digest").takeIf {
                it.matches(Regex("^sha256:[a-fA-F0-9]{64}$"))
            }
            return Release(tag, url.toString(), size, digest)
        } finally {
            connection.disconnect()
        }
    }

    private fun download(release: Release) {
        if (activeDownload != -1L) return
        val directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (directory == null) {
            message("下载不可用", "设备存储空间不可用")
            return
        }
        val file = File(directory, "3C-Prototype-update.apk")
        file.delete()
        val request = DownloadManager.Request(Uri.parse(release.download))
            .setTitle("3C Prototype " + release.version)
            .setDescription("下载正式更新包")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, file.name)
        try {
            activeDownload = manager.enqueue(request)
        } catch (error: Exception) {
            activeDownload = -1
            message("下载失败", error.message ?: "无法启动系统下载服务")
            return
        }

        val label = TextView(activity).apply { text = "正在连接下载服务器…" }
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        val padding = (24 * activity.resources.displayMetrics.density).toInt()
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(label)
            addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(activity).setTitle("下载更新 " + release.version)
            .setView(layout)
            .setNegativeButton("取消") { _, _ -> cancelDownload() }
            .setCancelable(false).create()
        dialog.show()

        val downloadId = activeDownload
        val poll = object : Runnable {
            override fun run() {
                if (destroyed || activeDownload != downloadId || !dialog.isShowing) return
                manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        dialog.dismiss()
                        cancelDownload()
                        message("下载失败", "找不到下载任务")
                        return
                    }
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            activeDownload = -1
                            dialog.dismiss()
                            verifyAndInstall(file, release)
                            return
                        }
                        DownloadManager.STATUS_FAILED -> {
                            activeDownload = -1
                            dialog.dismiss()
                            message("下载失败", "系统下载服务报告失败，请稍后重试")
                            return
                        }
                        else -> {
                            val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            progress.isIndeterminate = false
                            progress.progress = ((bytes * 100) / release.size).toInt().coerceIn(0, 100)
                            label.text = "已下载 " + (bytes / 1024) + " / " + (release.size / 1024) + " KB"
                        }
                    }
                }
                main.postDelayed(this, 400)
            }
        }
        main.post(poll)
    }

    private fun cancelDownload() {
        if (activeDownload != -1L) {
            manager.remove(activeDownload)
            activeDownload = -1L
        }
    }

    private fun verifyAndInstall(file: File, release: Release) {
        worker.execute {
            val valid = runCatching {
                require(file.length() == release.size) { "安装包大小校验失败" }
                release.digest?.let { expected ->
                    val md = MessageDigest.getInstance("SHA-256")
                    file.inputStream().buffered().use { input ->
                        val bytes = ByteArray(32768)
                        while (true) {
                            val length = input.read(bytes)
                            if (length < 0) break
                            md.update(bytes, 0, length)
                        }
                    }
                    val actual = md.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    require(actual.equals(expected.removePrefix("sha256:"), ignoreCase = true)) { "安装包 SHA-256 校验失败" }
                }
                require(activity.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.packageName ==
                    activity.packageName) { "安装包不是 3C Prototype" }
                true
            }
            main.post {
                if (destroyed || activity.isFinishing) return@post
                valid.onSuccess { install(file) }.onFailure {
                    file.delete()
                    message("校验失败", it.message ?: "安装包不完整")
                }
            }
        }
    }

    private fun install(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()) {
            pendingInstall = file
            AlertDialog.Builder(activity).setTitle("允许应用更新")
                .setMessage("请允许 3C Prototype 安装来自自身下载的 APK，然后由系统确认安装。")
                .setPositiveButton("打开设置") { _, _ ->
                    activity.startActivityForResult(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.packageName)),
                        INSTALL_PERMISSION_REQUEST
                    )
                }
                .setNegativeButton("取消", null).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            message("无法安装", "未找到系统 APK 安装程序")
        } catch (error: Exception) {
            message("无法安装", error.message ?: "无法打开安装程序")
        }
    }

    fun onActivityResult(requestCode: Int) {
        if (requestCode != INSTALL_PERMISSION_REQUEST) return
        val file = pendingInstall ?: return
        pendingInstall = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            activity.packageManager.canRequestPackageInstalls()) {
            install(file)
        } else {
            message("尚未授权", "可再次从更新入口安装")
        }
    }

    private fun message(title: String, body: String) {
        AlertDialog.Builder(activity).setTitle(title).setMessage(body)
            .setPositiveButton("知道了", null).show()
    }

    fun destroy() {
        destroyed = true
        cancelDownload()
        worker.shutdownNow()
    }
}
