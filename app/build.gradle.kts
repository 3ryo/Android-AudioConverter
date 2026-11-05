plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.arun.audioconverter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arun.audioconverter"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.5"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation("com.arthenica:smart-exception-common:0.2.1")
}
