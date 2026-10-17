plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "cn.cgroup.prototype"
    compileSdk = 35
    defaultConfig {
        applicationId = "cn.cgroup.prototype"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
}
