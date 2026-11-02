plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    applicationName = "organiza"
    mainClass.set("org.organiza.linux.MainKt")
}

dependencies {
    implementation(project(":core:scheduler"))
    implementation(project(":core:calendar"))
    implementation(project(":core:presentation"))
    implementation(libs.sqlite.jdbc.crypt)
    implementation(libs.kotlinx.serialization.json)
    // The fakes come with the plain xerial driver, which would clash with the encrypting one.
    testImplementation(project(":core:testing")) {
        exclude(group = "org.xerial")
    }
}
