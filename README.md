# 3C Prototype · Android

三创通用 Android 原型播放器，启动加载 https://lab.3cgroup.cn/ 。H5 演示内容由 sanchuang-dev/base-prototype 独立维护。

V0.1: Kotlin WebView、HTTPS 加载、返回、加载失败重试、长按切换调试地址；内置受来源限制的 JSBridge v1（见 [调用协议](docs/jsbridge.md)）。

## Build & install

- PR / main: CI 编译 Release 变体，仅校验，不发布未签名 APK，也不提供 Debug APK。
- 推送 `v*` Tag: CI 使用固定签名密钥构建已签名 Release APK，验证签名，并发布到 GitHub Releases。
- 用户从 Releases 下载 `3C-Prototype-v*.apk` 并在 Android 手机上直接安装。Android 可能需要允许此来源安装 APK。
- 请将以下四项 Actions Secrets 配在当前仓库（Settings → Secrets and variables → Actions）：
  - `ANDROID_KEYSTORE_BASE64`：Android 上传/应用签名 keystore 文件内容的 base64 编码（单行）
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`
- **必须长期安全保管原始 keystore**；若丢失，后续 APK 无法在已安装版本上直接覆盖升级。绝不把 keystore 或密码放进公开源码。
- 无签名 Secrets 时，标签发布会明确失败；不使用一次性临时签名伪装成正式版。

JDK 17、Android SDK 35、Gradle 8.10.2。手动本地编译命令 `gradle :app:assembleRelease`（未配置签名时产出未签名 APK，不能安装）。

CI 通过不等于真机验收：还需检查首次加载、内部导航、返回、弱网重试、切换 URL、恢复默认首页和覆盖升级。

## 应用内更新（v0.1.1 起）

- 启动后后台检查 GitHub `/repos/sanchuang-dev/3c-app-prototype/releases/latest`，仅在发现比本机 `versionName` 更新的正式版本时提示。
- 长按网页打开原生管理菜单：**检查应用更新 / 查看浏览器内核 / 切换原型地址**。
- 找到新版本后自动读取 Release 的 `3C-Prototype-vX.Y.Z.apk` 资产地址，而不是把旧版本下载 URL 写死。
- 下载使用 Android DownloadManager，原生界面显示进度、可取消；检查 APK 大小、GitHub 发布的 SHA-256（若提供）和 Android 包名，随后调用系统安装器；用户必须授权并确认安装。
- 同一个应用必须复用稳定签名；发布后可覆盖安装。下载发生在原生容器中，不依赖 H5 正常加载。
- 调试时可在管理菜单看到 Android System WebView 提供者名称和版本，并跳转至系统内核应用设置；**本版没有内置 Chrome/Chromium**，系统内核过旧时应通过设备系统渠道更新。若要完全内置独立内核，应另立 GeckoView 迁移项目，重做 JSBridge 并评估体积与兼容性。

验收：GitHub CI Release 编译、真实 v0.1.1 签名包、从 v0.1.0 覆盖安装、正常下载与用户取消、断网/下载失败、安装权限处理、Android 7/8/13+ 真机路径。CI 不替代这些真机检查。

## 双引擎安装包（兼容版）

从 v0.1.1 开始，一个 GitHub Release 会同时包含两个**正式签名**的 APK：

| Asset | 包名 | 内核 | 最低 Android |
| --- | --- | --- | --- |
| `3C-Prototype-vX.Y.Z.apk` | `cn.cgroup.prototype` | 系统 WebView | 7.0 / API 24 |
| `3C-Prototype-Gecko-vX.Y.Z.apk` | `cn.cgroup.prototype.gecko` | 随包附带 GeckoView 158.0 | 8.0 / API 26 |

两款可以在同一设备**并排安装**，互不覆盖、互不共享登录状态或缓存。后续升级必须选同一内核渠道的 APK。更新检查都使用 GitHub `releases/latest`，按当前包内 `apk_asset_prefix` 匹配正确的安装文件，不会互相升级到另一种内核。

GeckoView 是 Mozilla 的自包含浏览器引擎，并非 Google Chrome/Chromium；它不依赖系统 Android WebView，但也不代表支持所有旧设备。装载独立引擎的 APK 比系统版大得多，对 CPU ABI/内存/Android 版本有要求。GeckoView 使用内置 WebExtension 对接与轻量版相同的 `ThreeCBridge` JSON 协议，并在原生层重新校验调用来源、顶层 Frame、Session。

签名：使用同一仓库的既有 Android Secrets，CI 必须同时验证两份 APK 的签名，再同时上传至 GitHub Release。禁止失败时自动降级成没有内置内核的“兼容包”。

验证：`assembleSystemRelease` 与 `assembleGeckoRelease` 均通过；签名包真实存在、各自 APK 包名正确；实机安装（两版共存）、首页打开、链接跳转及返回、JSBridge 三个方法、调试 URL 的权限隔离、最新版本检查、下载和安装权限都要单独验收。仅编译成功不算兼容性验收。
