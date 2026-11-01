plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:ai"))
    api(project(":core:calendar"))
    api(project(":core:enforcement"))
    api(project(":core:storage"))
    api(project(":core:voice"))
    testImplementation(project(":core:testing"))
}
