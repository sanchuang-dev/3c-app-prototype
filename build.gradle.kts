plugins {
    id("com.android.application") version "9.1.1" apply false
}

// GeckoView 158 dependencies publish Kotlin 2.4 metadata. AGP built-in Kotlin
// otherwise selects the older KGP 2.2 toolchain; use the documented override.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}
