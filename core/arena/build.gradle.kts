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

// A second participant for scripts/verify-android.sh: `-Ppeer=<mode>,<relay>,<invite>`.
tasks.register<JavaExec>("arenaPeer") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.organiza.arena.ArenaPeerKt")
    args((project.findProperty("peer") as String? ?: "").split(","))
}
