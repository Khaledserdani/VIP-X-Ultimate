buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") apply false version "9.3.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
