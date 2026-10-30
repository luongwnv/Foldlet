plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "vn.lit.screenos.controls"
    compileSdk = 36
    defaultConfig {
        applicationId = "vn.lit.screenos.controls"
        minSdk = 28
        // Keep the documented legacy Wi-Fi/Bluetooth toggle APIs available.
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
        }
    }
    lint {
        // This optional, sideloaded compatibility helper is not a Play Store app.
        disable += "ExpiredTargetSdkVersion"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
