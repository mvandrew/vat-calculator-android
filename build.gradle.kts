buildscript {
    dependencies {
        // Override AGP's built-in KGP without applying kotlin.android.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
