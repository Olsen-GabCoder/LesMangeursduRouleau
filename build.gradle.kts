// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {

    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.googleGmsServices) apply false
    alias(libs.plugins.navigationSafeArgs) apply false // C'est la bonne déclaration pour Safe Args ici

    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}

