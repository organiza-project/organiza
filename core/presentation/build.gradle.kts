plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:arena"))
    api(project(":core:scheduler"))
    testImplementation(project(":core:testing"))
}
