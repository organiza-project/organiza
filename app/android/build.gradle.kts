plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.organiza.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.organiza.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The hosted backend (ADR-0015). Builds without these properties offer local and self-hosted only.
        buildConfigField("String", "SUPABASE_URL", quoted(providers.gradleProperty("organiza.supabaseUrl").orNull))
        buildConfigField("String", "SUPABASE_ANON_KEY", quoted(providers.gradleProperty("organiza.supabaseAnonKey").orNull))
    }

    // A release key is read from properties kept outside the repository (CONTRIBUTING.md).
    val releaseStore = providers.gradleProperty("organiza.release.storeFile").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("organiza.release.storePassword").get()
                keyAlias = providers.gradleProperty("organiza.release.keyAlias").get()
                keyPassword = providers.gradleProperty("organiza.release.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a release key, dogfood builds are signed with the debug key (ADR-0014).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

composeCompiler {
    // Its mapping task cannot read the Java 25 class files some dependencies now ship, and the
    // file only maps Compose groups in obfuscated stack traces, which R8's own mapping covers.
    includeComposeMappingFile.set(false)
}

fun quoted(value: String?): String = "\"" + value.orEmpty().replace("\\", "").replace("\"", "") + "\""

dependencies {
    implementation(project(":core:arena"))
    implementation(project(":core:sync"))
    implementation(project(":core:presentation"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(project(":core:scheduler"))
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    // Compose testing pulls an Espresso too old for API 36, so a current one is pinned.
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}
