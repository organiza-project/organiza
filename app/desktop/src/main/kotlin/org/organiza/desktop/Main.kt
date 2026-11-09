package org.organiza.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import java.awt.SystemTray
import org.organiza.app.OrganizaCore
import org.organiza.feature.blocker.InterventionScreen
import org.organiza.ui.OrganizaRoot
import org.organiza.ui.OrganizaTheme
import org.organiza.ui.Route

fun main(args: Array<String>) {
    val socket = Instance.socket()
    // A second start shows the running Organiza, or its copilot, and ends.
    if (Instance.handOff(socket, if (COPILOT in args) "copilot" else "show")) return
    val platform = DesktopPlatform()
    val core = OrganizaCore(platform)
    core.start()
    val focus = FocusSource.forSession(apps = platform.apps)
    val app = DesktopApp(core, platform, focus)
    val actions = DesktopActions(core, app, platform, focus)
    // Logging out stops the JVM too; the stretch in front ends then and stopped apps run again.
    Runtime.getRuntime().addShutdownHook(Thread(app::close))
    val visible = mutableStateOf(BACKGROUND !in args)
    val request = mutableStateOf<Pair<Route, Long>?>(if (COPILOT in args) Route.COPILOT to System.nanoTime() else null)
    val listener = Instance.listen(socket) { command ->
        platform.onMain {
            visible.value = true
            if (command == "copilot") request.value = Route.COPILOT to System.nanoTime()
        }
    }
    application {
        val tray = platform.trayWanted && SystemTray.isSupported()
        if (tray) {
            Tray(
                icon = trayIcon(),
                tooltip = "Organiza",
                onAction = { visible.value = true },
                menu = {
                    Item("Open Organiza") { visible.value = true }
                    Item("Copilot") {
                        visible.value = true
                        request.value = Route.COPILOT to System.nanoTime()
                    }
                    Separator()
                    Item("Quit") {
                        listener.close()
                        exitApplication()
                    }
                },
            )
        }
        Window(
            onCloseRequest = {
                // With the tray on, closing hides the window and tracking carries on.
                if (tray) visible.value = false else exitApplication()
            },
            state = rememberWindowState(width = 1120.dp, height = 800.dp),
            visible = visible.value,
            title = "Organiza",
            icon = trayIcon(),
        ) {
            OrganizaTheme { OrganizaRoot(core.uiState.value, actions, request.value) }
        }
        app.pause.value?.let { flow ->
            // The pause covers the screen and stays on top; it closes only through its own choices.
            Window(
                onCloseRequest = {},
                state = rememberWindowState(placement = WindowPlacement.Fullscreen),
                title = "Organiza pause",
                undecorated = true,
                alwaysOnTop = true,
                resizable = false,
            ) {
                OrganizaTheme { InterventionScreen(flow, core, onDone = { outcome -> app.choose(flow, outcome) }) }
            }
        }
    }
}

/** The ring of the logo mark, for the window and the top bar, where an image file would add nothing. */
private fun trayIcon(): Painter = object : Painter() {
    override val intrinsicSize: Size = Size(64f, 64f)

    override fun DrawScope.onDraw() {
        val side = size.minDimension
        drawCircle(Color.White, radius = side * 0.38f, style = Stroke(side * 0.14f))
        drawCircle(Color(0xFF4C8DF6), radius = side * 0.12f)
    }
}
