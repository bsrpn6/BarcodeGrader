// build.gradle.kts (Project-level)
buildscript {
    dependencies {
        classpath(libs.google.services)
        classpath(libs.firebase.crashlytics.gradle)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.dagger.hilt.android) apply false
}