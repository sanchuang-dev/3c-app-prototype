// GeckoView built-in extension: expose the existing ThreeCBridge API only on
// top-level pages from the trusted first-party origin. Native code rechecks
// sender.url, frame and session before handling every request.
(() => {
  if (window !== window.top || window.location.origin !== "https://lab.3cgroup.cn") return;
  const page = window.wrappedJSObject;
  if (!page) return;

  const listeners = new Map();
  const bridge = cloneInto({}, page);

  exportFunction(function postMessage(raw) {
    let request;
    try {
      request = JSON.parse(raw);
    } catch (_) {
      return;
    }
    browser.runtime.sendNativeMessage("threec", request)
      .then(response => {
        const event = cloneInto({ data: JSON.stringify(response) }, page);
        for (const listener of listeners.keys()) {
          try { listener(event); } catch (_) { /* Listener errors are page-owned. */ }
        }
      })
      .catch(() => {
        const event = cloneInto({
          data: JSON.stringify({ id: request.id, ok: false, error: "NATIVE_UNAVAILABLE" })
        }, page);
        for (const listener of listeners.keys()) {
          try { listener(event); } catch (_) {}
        }
      });
  }, bridge, { defineAs: "postMessage" });

  exportFunction(function addEventListener(type, handler) {
    if (type === "message" && typeof handler === "function") listeners.set(handler, true);
  }, bridge, { defineAs: "addEventListener" });

  exportFunction(function removeEventListener(type, handler) {
    if (type === "message") listeners.delete(handler);
  }, bridge, { defineAs: "removeEventListener" });

  page.ThreeCBridge = bridge;
})();
