import java.io.File
import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.vipx.ultimate"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vipx.ultimate"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "2.3.3"
    }
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("VIP_RELEASE_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("VIP_RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("VIP_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("VIP_RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val poseModelFile = layout.projectDirectory.file("src/main/assets/pose_landmarker_full.task").asFile
val downloadPoseLandmarkerModel by tasks.registering {
    outputs.file(poseModelFile)
    doLast {
        val expectedSha256 = "4eaa5eb7a98365221087693fcc286334cf0858e2eb6e15b506aa4a7ecdcec4ad"
        val modelUrl = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task"
        poseModelFile.parentFile.mkdirs()
        if (!poseModelFile.exists()) {
            val temporary = File(poseModelFile.parentFile, poseModelFile.name + ".download")
            URL(modelUrl).openStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            temporary.copyTo(poseModelFile, overwrite = true)
            temporary.delete()
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(poseModelFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (digest != expectedSha256) {
            poseModelFile.delete()
            throw GradleException("MediaPipe pose model SHA-256 mismatch: $digest")
        }
    }
}
tasks.named("preBuild").configure {
    dependsOn(downloadPoseLandmarkerModel)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("com.google.mediapipe:tasks-vision:0.10.35")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
