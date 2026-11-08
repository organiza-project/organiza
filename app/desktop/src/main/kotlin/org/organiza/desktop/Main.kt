package org.organiza.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.organiza.ui.OrganizaRoot
import org.organiza.ui.OrganizaTheme

fun main() {
    val app = DesktopApp()
    application {
        // A phone-shaped window by default: the screens centre at 720dp when it is widened.
        Window(::exitApplication, rememberWindowState(width = 440.dp, height = 900.dp), title = "Organiza") {
            OrganizaTheme { OrganizaRoot(app.ui.value, app) }
        }
    }
}
