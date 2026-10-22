# 3C Prototype Chromium JSBridge v1

The C01 Chromium application uses **Cefrium 0.10.2**, not AndroidX WebKit. It exposes Cefrium's own `window.cefriumQuery` on supported pages. The old Android System WebView implementation and its `window.ThreeCBridge` object are *not* included in the Chromium APK; H5 callers requiring the former object must explicitly adapt. The JSON method contract remains `getCapabilities`, `getAppInfo`, `toast`.

## Privilege boundary

Only HTTPS documents with **exact** origin `https://proto.3cgroup.cn` may receive native responses. Cefrium SDK 0.10.2 provides the calling frame URL to `setQueryHandler(QueryHandler)`; the native handler explicitly checks HTTPS scheme, exact host, port, and absence of userinfo before processing a method. Arbitrary debug URLs and cross-origin iframes are denied. This API provides the caller origin, **not a proven main-frame flag**: same-origin iframes are not yet independently excluded. Keep methods strictly nonprivileged (static app metadata, short toast). Do not add filesystem, identifiers, shell, arbitrary intent, or privileged methods without an independently verifiable frame isolation guarantee.

## Calling convention

On allowed pages, call:

```js
function nativeCall(method, params = {}) {
  return new Promise((resolve, reject) => {
    if (typeof window.cefriumQuery !== 'function') return reject(new Error('Chromium bridge unavailable'));
    const id = Math.random().toString(36).slice(2);
    window.cefriumQuery({
      request: JSON.stringify({id, method, params}),
      onSuccess: raw => {
        try {
          const data = JSON.parse(raw);
          data.ok ? resolve(data.result) : reject(new Error(data.error || 'Native call failed'));
        } catch (e) { reject(e); }
      },
      onFailure: (code, message) => reject(new Error(message || `Native call failed (${code})`))
    });
  });
}
nativeCall('getCapabilities').then(console.log);
nativeCall('toast', {message: 'Hello from 3C!'});
```

The native handler checks the method allowlist, validates message lengths, and returns a correlated JSON `{id,ok,result}` on success. Invalid requests are rejected with a controlled error.

## Acceptance boundaries

- CI must compile the release APK with no AndroidX/WebView Kotlin classes present.
- Inspect that `https://proto.3cgroup.cn` can reach allowed methods; cross-origin sites cannot.
- Verify same-origin iframe isolation before introducing privileged methods; origin-only is not frame-equivalent.
- Verify on a physical Android device before claiming the integration is accepted.
