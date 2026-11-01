plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:ai"))
    testImplementation(project(":core:testing"))
}
