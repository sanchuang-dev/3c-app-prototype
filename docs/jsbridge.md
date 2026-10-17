# 3C Prototype JSBridge v1

The WebView injects `window.ThreeCBridge` using AndroidX WebKit WebMessageListener on supported Android System WebView versions.

## Origin policy

Only main-frame documents hosted at `https://lab.3cgroup.cn` may access this bridge. Subdomains, external sites, iframes and manually selected debug URLs from other hosts **do not** receive native privileges. HTTPS navigation still works independently of the bridge.

**No** `addJavascriptInterface`, unrestricted native method dispatcher, device identifier access, shell execution or arbitrary URL opening.

## Message protocol

The host sends a JSON string with an `id`, a `method`, and optional `params`. The native side replies using the event's `replyProxy` as a JSON string. Response: `{id,ok,result}` or `{id,ok:false,error}`.

Available methods:
- `getCapabilities`: `{version:1, methods:["getCapabilities","getAppInfo","toast"]}`
- `getAppInfo`: `{platform:"android",androidSdk,appVersion,bridgeVersion:1}` (no persistent identifiers)
- `toast`: `{message:"hello"}` limited to 160 characters

Example (in a trusted first-party page):

```js
function nativeCall(method, params = {}) {
  return new Promise((resolve, reject) => {
    const bridge = window.ThreeCBridge;
    if (!bridge) return reject(new Error("Bridge unavailable"));
    const id = Math.random().toString(36).slice(2);
    const onMessage = event => {
      let data;
      try { data = JSON.parse(event.data); } catch { return; }
      if (data.id !== id) return;
      bridge.removeEventListener("message", onMessage);
      data.ok ? resolve(data.result) : reject(new Error(data.error));
    };
    bridge.addEventListener("message", onMessage);
    bridge.postMessage(JSON.stringify({id, method, params}));
  });
}

nativeCall("getCapabilities").then(console.log);
nativeCall("toast", { message: "Hello from 3C!" });
```

Future privileged methods must have explicit method-specific allowlisting, user consent/permissions where needed, and origin review. This is deliberately not a universal native-permission bridge.

## Acceptance checks

1. First-party HTTPS page can call each method and gets a correlated reply.
2. External HTTPS page and cross-origin iframe cannot call bridge.
3. If WebMessageListener is not supported, WebView still renders normally.
4. Malformed JSON, unknown methods and empty toast content return a controlled error.
5. Verify on physical Android device; a green CI alone is insufficient.
