package cn.cgroup.prototype

import android.os.Build
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * Safe, versioned API for trusted first-party H5 pages.
 * AndroidX injects the object only into the explicitly listed HTTPS origins.
 */
object PrototypeBridge {
    private val allowedOrigins = setOf("https://lab.3cgroup.cn")
    private const val objectName = "ThreeCBridge"

    fun install(webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return // WebView remains usable; bridge capability is unavailable.
        }
        WebViewCompat.addWebMessageListener(webView, objectName, allowedOrigins) {
            _, message, sourceOrigin, isMainFrame, replyProxy ->
            // Require top-level first-party document, not an embedded iframe.
            if (!isMainFrame || sourceOrigin.toString().trimEnd('/') !in allowedOrigins) return@addWebMessageListener
            val request = try { JSONObject(message.data ?: "") } catch (_: Exception) { JSONObject() }
            val id = request.opt("id")
            val response = JSONObject()
            if (id != null && id != JSONObject.NULL) response.put("id", id)
            try {
                when (request.optString("method")) {
                    "getCapabilities" -> {
                        response.put("ok", true)
                        response.put("result", JSONObject().put("version", 1).put(
                            "methods", org.json.JSONArray(listOf("getCapabilities", "getAppInfo", "toast"))
                        ))
                    }
                    "getAppInfo" -> {
                        response.put("ok", true)
                        response.put("result", JSONObject()
                            .put("platform", "android")
                            .put("androidSdk", Build.VERSION.SDK_INT)
                            .put("bridgeVersion", 1)
                            .put("appVersion", webView.context.packageManager
                                .getPackageInfo(webView.context.packageName, 0).versionName))
                    }
                    "toast" -> {
                        val text = request.optJSONObject("params")?.optString("message").orEmpty().take(160)
                        if (text.isBlank()) throw IllegalArgumentException("message required")
                        webView.post { Toast.makeText(webView.context, text, Toast.LENGTH_SHORT).show() }
                        response.put("ok", true).put("result", JSONObject())
                    }
                    else -> response.put("ok", false).put("error", "UNKNOWN_METHOD")
                }
            } catch (_: Exception) {
                response.put("ok", false).put("error", "INVALID_ARGUMENT")
            }
            replyProxy.postMessage(response.toString())
        }
    }
}
