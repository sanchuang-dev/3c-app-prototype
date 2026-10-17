# 3C Android Prototype repository rules

Follow current sanchuang-dev/.github/AGENTS.md (main). This repository owns the Android WebView shell and its build/release workflow, not the H5 demonstration pages. Home URL is https://lab.3cgroup.cn/. Keep Kotlin implementation minimal; allow only the deliberately versioned AndroidX WebKit JSBridge described in docs/jsbridge.md. Never expose native capabilities to arbitrary origins.

PR/main compile the release variant **without distributing unsigned or debug APKs**. Tagged versions (v*) must generate a consistently signed APK, verify its signature, and publish it in GitHub Releases. Stable signing secrets are mandatory to support future in-place upgrades. CI compile success is not device acceptance. Use small verifiable changes; Issue is work item source of truth.
