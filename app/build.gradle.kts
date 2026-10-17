plugins {
    id("com.android.application")
}
android {
    namespace = "cn.cgroup.prototype"
    compileSdk = 37
    defaultConfig {
        applicationId = "cn.cgroup.prototype"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
    }
    flavorDimensions += "engine"
    productFlavors {
        create("system") {
            dimension = "engine"
            manifestPlaceholders["launcherActivity"] = ".MainActivity"
            resValue("string", "app_name", "3C Prototype")
            resValue("string", "apk_asset_prefix", "3C-Prototype-")
        }
        create("gecko") {
            dimension = "engine"
            applicationIdSuffix = ".gecko"
            minSdk = 26
            manifestPlaceholders["launcherActivity"] = ".GeckoActivity"
            resValue("string", "app_name", "3C Prototype Gecko")
            resValue("string", "apk_asset_prefix", "3C-Prototype-Gecko-")
        }
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core:core:1.13.1")
    add("geckoImplementation", "org.mozilla.geckoview:geckoview:158.0.20261007201254")
}
