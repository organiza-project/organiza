import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":app:shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.sqlite.jdbc.crypt)
    // User32, ntdll, DPAPI and the registry on Windows (ADR-0027).
    implementation(libs.jna.platform)
    testImplementation(libs.compose.mp.ui.test)
    // The fakes come with the plain xerial driver, which would clash with the encrypting one.
    testImplementation(project(":core:testing")) {
        exclude(group = "org.xerial")
    }
}

tasks.test {
    // gsettings writes from tests land in a file under build/, never in the person's dconf.
    environment("GSETTINGS_BACKEND", "keyfile")
    environment("XDG_CONFIG_HOME", layout.buildDirectory.dir("test-config").get().asFile.path)
}

// The desktop app for Linux and Windows (ADR-0023, ADR-0027). `run` opens the window; `packageDeb`
// builds the Linux installer and `packageMsi`, run on Windows, the Windows one.
compose.desktop {
    application {
        mainClass = "org.organiza.desktop.MainKt"
        // Lets Main name its windows after the launcher instead of the main class.
        jvmArgs += listOf("--add-opens", "java.desktop/sun.awt.X11=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            // The encrypted SQLite driver needs java.sql; TLS to Groq and Supabase needs the EC ciphers.
            modules("java.sql", "jdk.unsupported", "jdk.crypto.ec")
            // The name people see in the app grid; the Debian package itself stays lowercase.
            packageName = "Organiza"
            packageVersion = "0.3.1"
            description = "Focus sessions, habits and app blocking that stay on your computer"
            vendor = "Organiza Project"
            licenseFile.set(rootProject.file("LICENSE"))
            windows {
                iconFile.set(file("packaging/organiza.ico"))
                menuGroup = "Organiza"
                // Installs for this user only, so no administrator is needed and the data stays in the profile.
                perUserInstall = true
                dirChooser = true
                shortcut = true
                // Fixed for good: Windows matches this to replace an older Organiza on upgrade.
                upgradeUuid = "6f1d0b5e-4b7a-4c43-9a5e-0c2f4f3f7a21"
            }
            linux {
                iconFile.set(file("packaging/organiza.png"))
                packageName = "organiza"
                debMaintainer = "radwandoufan@gmail.com"
                appCategory = "Utility"
                menuGroup = "Utility"
                shortcut = true
            }
        }
    }
}

// jpackage has no option for the homepage, AppStream metadata or a themed icon, so they are added to the finished .deb.
tasks.matching { it.name == "packageDeb" }.configureEach {
    doLast {
        val deb = outputs.files.asFileTree.matching { include("*.deb") }.singleFile
        val finish = ProcessBuilder(file("packaging/finish-deb.sh").path, deb.path).inheritIO().start()
        check(finish.waitFor() == 0) { "finish-deb.sh failed" }
    }
}
