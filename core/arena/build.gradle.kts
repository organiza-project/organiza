plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:ai"))
    api(project(":core:storage"))
    implementation(libs.bouncycastle)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
}
