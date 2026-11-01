plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
}
