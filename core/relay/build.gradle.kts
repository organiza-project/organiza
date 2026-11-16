plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:ai"))
    api(project(":core:storage"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
}
