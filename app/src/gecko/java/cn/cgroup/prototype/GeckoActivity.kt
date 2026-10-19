package cn.cgroup.prototype

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension

/**
 * Standalone browser engine build. Unlike the system flavor, it does not create Android WebView.
 * This flavor's own package ID and APK asset prevent accidental cross-engine updates.
 */
class GeckoActivity : Activity() {
    companion object {
        private const val HOME = "https://lab.3cgroup.cn/"
        private const val PREFS = "prototype_settings"
        private const val URL_KEY = "debug_url"
        private const val EXTENSION_PATH = "resource://android/assets/threecbridge/"
        private const val EXTENSION_ID = "threecbridge@3cgroup.cn"
        private const val NATIVE_APP_ID = "threec"
    }
    private lateinit var updater: AppUpdater
    private var session: GeckoSession? = null
    private var runtime: GeckoRuntime? = null
    private var canGoBack = false
    private var currentUrl = HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updater = AppUpdater(this)
        currentUrl = getSharedPreferences(PREFS, MODE_PRIVATE).getString(URL_KEY, HOME) ?: HOME
        try {
            val engine = GeckoRuntime.create(this)
            runtime = engine
            val activeSession = GeckoSession()
            session = activeSession
            val view = GeckoView(this)
            activeSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                    this@GeckoActivity.canGoBack = canGoBack
                }
                override fun onLocationChange(
                    session: GeckoSession,
                    url: String?,
                    perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                    hasUserGesture: Boolean
                ) {
                    if (url != null) currentUrl = url
                }
            }
            activeSession.open(engine)
            view.setSession(activeSession)
            view.setOnLongClickListener { showNativeMenu(); true }
            setContentView(view)
            engine.webExtensionController.ensureBuiltIn(EXTENSION_PATH, EXTENSION_ID).accept(
                { extension ->
                    if (extension == null) {
                        Toast.makeText(this, "原生通信扩展不可用，网页仍可浏览", Toast.LENGTH_LONG).show()
                    } else {
                        activeSession.webExtensionController.setMessageDelegate(
                            extension, createBridgeDelegate(activeSession), NATIVE_APP_ID
                        )
                    }
                    activeSession.loadUri(currentUrl)
                },
                { error ->
                    Toast.makeText(this, "原生通信不可用，网页仍可浏览", Toast.LENGTH_LONG).show()
                    activeSession.loadUri(currentUrl)
                }
            )
        } catch (error: Exception) {
            AlertDialog.Builder(this).setTitle("GeckoView 启动失败")
                .setMessage("设备不支持或缺少资源。可以检查应用更新，或改用轻量版。\n\n" +
                    (error.message ?: "未知错误"))
                .setPositiveButton("检查更新") { _, _ -> updater.check(silent = false) }
                .setNegativeButton("关闭", null).show()
        }
        updater.check(silent = true)
    }

    private fun createBridgeDelegate(activeSession: GeckoSession) =
        object : WebExtension.MessageDelegate {
            override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any> {
                val payload = if (message is JSONObject) message else JSONObject()
                val id = payload.opt("id")
                val result = JSONObject()
                if (id != null && id != JSONObject.NULL) result.put("id", id)
                val uri = Uri.parse(sender.url)
                val trusted = nativeApp == NATIVE_APP_ID &&
                    sender.session === activeSession &&
                    sender.environmentType == WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT &&
                    sender.isTopLevel &&
                    uri.scheme == "https" && uri.host == "lab.3cgroup.cn" &&
                    (uri.port == -1 || uri.port == 443)
                if (!trusted) {
                    result.put("ok", false).put("error", "FORBIDDEN_ORIGIN")
                    return GeckoResult.fromValue(result)
                }
                try {
                    when (payload.optString("method")) {
                        "getCapabilities" -> result.put("ok", true).put(
                            "result", JSONObject().put("version", 1).put(
                                "methods", JSONArray(listOf("getCapabilities", "getAppInfo", "toast"))
                            )
                        )
                        "getAppInfo" -> result.put("ok", true).put(
                            "result", JSONObject()
                                .put("platform", "android")
                                .put("androidSdk", Build.VERSION.SDK_INT)
                                .put("bridgeVersion", 1)
                                .put("appVersion", packageManager.getPackageInfo(packageName, 0).versionName)
                        )
                        "toast" -> {
                            val text = payload.optJSONObject("params")?.optString("message").orEmpty().take(160)
                            require(text.isNotBlank())
                            runOnUiThread {
                                Toast.makeText(this@GeckoActivity, text, Toast.LENGTH_SHORT).show()
                            }
                            result.put("ok", true).put("result", JSONObject())
                        }
                        else -> result.put("ok", false).put("error", "UNKNOWN_METHOD")
                    }
                } catch (_: Exception) {
                    result.put("ok", false).put("error", "INVALID_ARGUMENT")
                }
                return GeckoResult.fromValue(result)
            }
        }

    private fun showNativeMenu() {
        AlertDialog.Builder(this).setTitle("3C Prototype Gecko · 管理")
            .setItems(arrayOf("检查应用更新", "内核版本", "切换原型地址", "恢复默认首页")) { _, option ->
                when (option) {
                    0 -> updater.check(silent = false)
                    1 -> AlertDialog.Builder(this).setTitle("独立浏览器内核")
                        .setMessage("内置 GeckoView 158。此版本不依赖 Android System WebView。\nAndroid 8.0 或以上。")
                        .setPositiveButton("知道了", null).show()
                    2 -> showUrlPicker()
                    3 -> load(HOME)
                }
            }.show()
    }

    private fun showUrlPicker() {
        val input = EditText(this).apply { setSingleLine(true); setText(currentUrl); selectAll() }
        AlertDialog.Builder(this).setTitle("原型地址（仅 HTTPS）")
            .setView(input).setPositiveButton("加载") { _, _ ->
                val url = input.text.toString().trim()
                val uri = Uri.parse(url)
                if (uri.scheme == "https" && !uri.host.isNullOrBlank()) {
                    load(url)
                } else Toast.makeText(this, "只允许 HTTPS", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("取消", null).show()
    }

    private fun load(url: String) {
        currentUrl = url
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(URL_KEY, url).apply()
        session?.loadUri(url)
    }

    @Deprecated("Legacy back callback")
    override fun onBackPressed() {
        if (canGoBack) session?.goBack() else super.onBackPressed()
    }

    @Deprecated("Legacy result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updater.onActivityResult(requestCode)
    }

    override fun onDestroy() {
        updater.destroy()
        session?.close()
        super.onDestroy()
    }
}
