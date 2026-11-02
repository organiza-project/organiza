plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:ai"))
    api(project(":core:arena"))
    api(project(":core:domain"))
    api(project(":core:storage"))
    api(libs.sqlite.jdbc)
}
