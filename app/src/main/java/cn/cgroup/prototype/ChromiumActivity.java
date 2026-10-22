package cn.cgroup.prototype;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.net.Uri;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.cefrium.CefriumBrowser;
import org.json.JSONObject;

/** Standalone Chromium engine. The Android System WebView is never instantiated. */
public final class ChromiumActivity extends Activity {
    private static final String HOME = "https://proto.3cgroup.cn/";
    private CefriumBrowser browser;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        browser = CefriumBrowser.createWithSurface(this);
        browser.setQueryHandler((id, request, origin, callback) -> {
            if (!isTrustedOrigin(origin)) {
                callback.failure(403, "origin not allowed");
                return true;
            }
            try {
                JSONObject input = new JSONObject(request);
                String method = input.optString("method");
                JSONObject result = new JSONObject();
                switch (method) {
                    case "getCapabilities":
                        result.put("version", 1);
                        result.put("methods", new org.json.JSONArray("[\"getCapabilities\",\"getAppInfo\",\"toast\"]"));
                        break;
                    case "getAppInfo":
                        result.put("platform", "android");
                        result.put("androidSdk", android.os.Build.VERSION.SDK_INT);
                        result.put("appVersion", "0.2.0");
                        result.put("bridgeVersion", 1);
                        break;
                    case "toast":
                        String message = input.optJSONObject("params") == null ? "" : input.optJSONObject("params").optString("message", "");
                        if (message.isEmpty() || message.length() > 160) throw new IllegalArgumentException("invalid message");
                        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
                        break;
                    default: throw new IllegalArgumentException("unknown method");
                }
                JSONObject reply = new JSONObject();
                reply.put("id", input.optString("id"));
                reply.put("ok", true);
                reply.put("result", result);
                callback.success(reply.toString());
            } catch (Exception e) {
                callback.failure(400, "invalid request");
            }
            return true;
        });
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(browser.getSurfaceContainer(), new LinearLayout.LayoutParams(-1, 0, 1));
        root.setOnLongClickListener(v -> { showAddressDialog(); return true; });
        setContentView(root);
        browser.loadUrl(HOME);
    }
    /** Cefrium 0.10.2 exposes the caller frame URL, but no QueryOriginPolicy API. */
    private static boolean isTrustedOrigin(String origin) {
        if (origin == null) return false;
        try {
            java.net.URI uri = new java.net.URI(origin);
            return "https".equalsIgnoreCase(uri.getScheme())
                && "proto.3cgroup.cn".equalsIgnoreCase(uri.getHost())
                && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (java.net.URISyntaxException ignored) {
            return false;
        }
    }

    private void showAddressDialog() {
        EditText input = new EditText(this);
        input.setText(HOME);
        new AlertDialog.Builder(this).setTitle("HTTPS 原型地址").setView(input)
            .setPositiveButton("加载", (d, w) -> {
                Uri uri = Uri.parse(input.getText().toString().trim());
                if ("https".equals(uri.getScheme()) && uri.getHost() != null) browser.loadUrl(uri.toString());
                else Toast.makeText(this, "仅支持 HTTPS", Toast.LENGTH_SHORT).show();
            }).setNeutralButton("首页", (d, w) -> browser.loadUrl(HOME))
            .setNegativeButton("取消", null).show();
    }
    @Override public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack(); else super.onBackPressed();
    }
    @Override public void onDestroy() {
        if (browser != null) browser.close();
        super.onDestroy();
    }
}
