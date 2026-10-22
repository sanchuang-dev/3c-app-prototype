plugins {
    id("com.android.application")
    id("com.cefrium") version "0.10.2"
}
android {
    namespace = "cn.cgroup.prototype"
    compileSdk = 37
    defaultConfig {
        applicationId = "cn.cgroup.prototype.chromium"
        ndk { abiFilters += "arm64-v8a" }
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.0"
    }
    val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    val keystorePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
    val keyAliasEnv = System.getenv("RELEASE_KEY_ALIAS")
    val keyPasswordEnv = System.getenv("RELEASE_KEY_PASSWORD")
    val signingAvailable = listOf(keystorePath, keystorePassword, keyAliasEnv, keyPasswordEnv).all { !it.isNullOrBlank() }
    signingConfigs {
        if (signingAvailable) create("releaseCi") {
            storeFile = file(keystorePath!!)
            storePassword = keystorePassword
            keyAlias = keyAliasEnv
            keyPassword = keyPasswordEnv
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingAvailable) signingConfig = signingConfigs.getByName("releaseCi")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

configurations.all { exclude(group = "com.google.guava", module = "listenablefuture") }

dependencies {
    implementation("com.cefrium:cefrium-sdk:0.10.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.browser:browser:1.8.0")
}
