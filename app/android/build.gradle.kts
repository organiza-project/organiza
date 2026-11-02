plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "org.organiza.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.organiza.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:arena"))
    implementation(project(":core:scheduler"))
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
}
