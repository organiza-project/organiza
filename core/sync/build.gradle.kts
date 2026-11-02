plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:arena"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
    testImplementation(libs.embedded.postgres)
    testImplementation(libs.postgresql)
}

// The backend schema lives at the repository root, where the Supabase CLI expects it (ADR-0015).
tasks.withType<Test>().configureEach {
    systemProperty("organiza.supabaseMigrations", rootProject.file("supabase/migrations").absolutePath)
    inputs.dir(rootProject.file("supabase/migrations"))
}
