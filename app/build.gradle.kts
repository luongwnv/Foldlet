plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "vn.lit.screenos"
    compileSdk = 36
    defaultConfig {
        applicationId = "vn.lit.screenos"
        minSdk = 30
        targetSdk = 35
        versionCode = 26
        versionName = "0.10.2"
        testInstrumentationRunner = "vn.lit.screenos.HardwareSmokeTest"
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        getByName("release") {
            // Keep beta updates compatible with the certificate already installed
            // on the test phone. The local keystore is never included in Git.
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
