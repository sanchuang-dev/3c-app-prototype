# 3C Prototype · Android

三创通用 Android 原型播放器。启动加载 https://lab.3cgroup.cn/ ，网页由 sanchuang-dev/base-prototype 单独维护。

V0.1: Kotlin WebView、HTTPS 加载、返回、失败重试、长按页面切换调试地址，默认不开放 JSBridge。

## Build

JDK 17, Android SDK 35, Gradle 8.10.2: `gradle :app:assembleDebug`。

PR/main 自动构建 Debug APK，可从 Actions Artifacts 下载。推送 v* 标签会尝试生成签名 Release，需先配置 ANDROID_KEYSTORE_BASE64、ANDROID_KEYSTORE_PASSWORD、ANDROID_KEY_ALIAS、ANDROID_KEY_PASSWORD 四项 GitHub Actions Secrets；缺少密钥时必须失败，不发布未签名 APK。

注意：CI 构建成功不等于真机验收。真机需验证首次打开、页面跳转、返回、弱网重试、调试 URL、恢复首页。
