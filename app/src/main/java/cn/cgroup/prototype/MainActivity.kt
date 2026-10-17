package cn.cgroup.prototype

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.webkit.WebViewCompat
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val HOME = "https://lab.3cgroup.cn/"
        private const val PREFS = "prototype_settings"
        private const val KEY = "debug_url"
    }
    private lateinit var browser: WebView
    private lateinit var updater: AppUpdater
    private var activeUrl = HOME
    private var errorVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeUrl = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY, HOME) ?: HOME
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme == "https") return false
                    if (request.isForMainFrame && uri.scheme in listOf("tel", "mailto")) {
                        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        catch (_: Exception) {
                            Toast.makeText(this@MainActivity, "无法打开此链接", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return true
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && !errorVisible) {
                        errorVisible = true
                        view.post {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("页面加载失败").setMessage("请检查网络连接后重试。")
                                .setPositiveButton("重试") { _, _ -> errorVisible = false; view.reload() }
                                .setNegativeButton("返回首页") { _, _ -> errorVisible = false; view.loadUrl(HOME) }
                                .show()
                        }
                    }
                }
            }
            setOnLongClickListener { showNativeMenu(); true }
        }
        PrototypeBridge.install(browser)
        updater = AppUpdater(this)
        CookieManager.getInstance().setAcceptCookie(true)
        setContentView(browser)
        if (savedInstanceState == null) browser.loadUrl(activeUrl)
        else browser.restoreState(savedInstanceState)
        updater.check(silent = true)
    }

    private fun showNativeMenu() {
        val actions = arrayOf("检查应用更新", "查看浏览器内核", "切换原型地址")
        AlertDialog.Builder(this).setTitle("3C Prototype · 管理")
            .setItems(actions) { _, selected ->
                when (selected) {
                    0 -> updater.check(silent = false)
                    1 -> showWebViewInfo()
                    2 -> showDebugAddress()
                }
            }.show()
    }

    private fun showWebViewInfo() {
        val provider = WebViewCompat.getCurrentWebViewPackage(this)
        val info = if (provider == null) {
            "没有找到可用的系统 WebView。请在设备系统设置中安装或更新 WebView 组件。"
        } else {
            "当前内核：" + provider.packageName + "\n版本：" + provider.versionName +
                "\n\n此 App 使用系统 WebView，不包含独立 Chromium 内核。较旧内核可能无法支持现代 H5。"
        }
        AlertDialog.Builder(this).setTitle("浏览器兼容性").setMessage(info)
            .setPositiveButton("知道了", null)
            .setNeutralButton("打开内核设置") { _, _ ->
                val name = provider?.packageName
                if (name != null) {
                    try {
                        startActivity(android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + name)))
                    } catch (_: Exception) {
                        Toast.makeText(this, "无法打开系统设置", Toast.LENGTH_SHORT).show()
                    }
                }
            }.show()
    }

    private fun showDebugAddress() {
        val input = EditText(this).apply { setSingleLine(true); setText(activeUrl); selectAll() }
        AlertDialog.Builder(this)
            .setTitle("原型地址（调试）").setView(input)
            .setPositiveButton("加载") { _, _ ->
                val candidate = input.text.toString().trim()
                val uri = Uri.parse(candidate)
                if (uri.scheme == "https" && !uri.host.isNullOrBlank()) {
                    activeUrl = candidate
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, candidate).apply()
                    errorVisible = false
                    browser.loadUrl(candidate)
                } else Toast.makeText(this, "仅支持 HTTPS 地址", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("恢复默认首页") { _, _ ->
                activeUrl = HOME
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY).apply()
                errorVisible = false
                browser.loadUrl(HOME)
            }
            .setNegativeButton("取消", null).show()
    }

    @Deprecated("Android legacy back callback")
    override fun onBackPressed() {
        if (browser.canGoBack()) browser.goBack() else super.onBackPressed()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }
    @Deprecated("Legacy activity result for APK install permission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updater.onActivityResult(requestCode)
    }

    override fun onDestroy() {
        updater.destroy()
        browser.destroy()
        super.onDestroy()
    }
}
