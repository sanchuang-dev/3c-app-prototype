package cn.cgroup.prototype

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
            setOnLongClickListener { showDebugAddress(); true }
        }
        PrototypeBridge.install(browser)
        CookieManager.getInstance().setAcceptCookie(true)
        setContentView(browser)
        if (savedInstanceState == null) browser.loadUrl(activeUrl)
        else browser.restoreState(savedInstanceState)
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
    override fun onDestroy() {
        browser.destroy()
        super.onDestroy()
    }
}
