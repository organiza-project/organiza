plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:relay"))
    api(project(":core:scheduler"))
    testImplementation(project(":core:testing"))
}
