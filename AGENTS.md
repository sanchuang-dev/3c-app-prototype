# 3C Android Prototype repository rules

Follow current sanchuang-dev/.github/AGENTS.md (main). This repository owns the Android WebView shell and its build/release workflow, not the H5 demonstration pages. Home URL is https://proto.3cgroup.cn/. The Chromium variant uses the self-contained Cefrium engine, not Android System WebView or AndroidX WebKit. Only expose the deliberately versioned, origin-scoped JSBridge described in docs/jsbridge.md; preserve explicit privilege boundaries. Never expose native capabilities to arbitrary origins.

PR/main compile the release variant **without distributing unsigned or debug APKs**. Tagged versions (v*) must generate a consistently signed APK, verify its signature, and publish it in GitHub Releases. Stable signing secrets are mandatory to support future in-place upgrades. CI compile success is not device acceptance. Use small verifiable changes; Issue is work item source of truth.
