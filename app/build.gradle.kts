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
        versionCode = 13
        versionName = "0.5.2"
        testInstrumentationRunner = "vn.lit.screenos.HardwareSmokeTest"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
