plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.ghalam.core.ui"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    api(platform("androidx.compose:compose-bom:2024.09.03"))
    api("androidx.compose.material3:material3")
    api("androidx.compose.ui:ui")
    api("io.coil-kt:coil-compose:2.7.0")
    api("com.squareup.okhttp3:okhttp:4.12.0")
}
