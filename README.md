# 3C Prototype · Android

三创通用 Android 原型播放器，启动加载 https://lab.3cgroup.cn/ 。H5 演示内容由 sanchuang-dev/base-prototype 独立维护。

V0.1: Kotlin WebView、HTTPS 加载、返回、加载失败重试、长按切换调试地址；默认不开放 JSBridge。

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
