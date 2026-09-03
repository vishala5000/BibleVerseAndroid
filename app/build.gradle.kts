plugins { id("com.android.application") }

android {
    namespace = "com.example.bibleverse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bibleverse"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7")
}
