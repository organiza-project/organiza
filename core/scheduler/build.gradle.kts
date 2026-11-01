plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:calendar"))
    api(project(":core:enforcement"))
    api(project(":core:storage"))
    testImplementation(project(":core:testing"))
}
