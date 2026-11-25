plugins {
    id("com.android.application")
}

android {
    namespace = "com.vipx.ultimate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vipx.ultimate"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "2.6.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
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
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.11.1")\n    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")\n    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("com.google.guava:guava:33.3.1-android")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
