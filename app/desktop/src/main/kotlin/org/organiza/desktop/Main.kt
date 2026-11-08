package org.organiza.desktop

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.time.Instant
import org.organiza.domain.port.Clock
import org.organiza.ui.OrganizaRoot
import org.organiza.ui.OrganizaTheme

fun main() {
    val db = try {
        DesktopStorage.open(DesktopStorage.file(), Keyring())
    } catch (failure: StorageUnavailable) {
        application {
            Window(::exitApplication, title = "Organiza") { BasicText(failure.message.orEmpty(), Modifier.padding(24.dp)) }
        }
        return
    }
    val app = DesktopApp(db, Clock { Instant.now() }, FocusSource.forSession(), LinuxApps.installed)
    // Logging out stops the JVM too, and the stretch in front ends then.
    Runtime.getRuntime().addShutdownHook(Thread { app.close(); db.close() })
    application {
        // A phone-shaped window by default: the screens centre at 720dp when it is widened.
        Window(::exitApplication, rememberWindowState(width = 440.dp, height = 900.dp), title = "Organiza") {
            OrganizaTheme { OrganizaRoot(app.ui.value, app) }
        }
    }
}
