package org.organiza.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.SystemTray
import java.awt.Toolkit
import org.organiza.app.OrganizaCore
import org.organiza.feature.blocker.InterventionScreen
import org.organiza.ui.OrganizaRoot
import org.organiza.ui.OrganizaTheme
import org.organiza.ui.Route

/** Kept in a static field so the lock lives as long as the process. */
private var instance: AutoCloseable? = null

fun main(args: Array<String>) {
    val socket = Instance.socket()
    instance = Instance.lock(socket.resolveSibling("organiza.lock"))
    if (instance == null) {
        // A later start shows the running Organiza, or its copilot, and ends. The first may still be starting.
        val command = if (COPILOT in args) "copilot" else "show"
        repeat(HANDOFF_TRIES) { if (Instance.handOff(socket, command)) return else Thread.sleep(100) }
        return
    }
    nameWindows()
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
                icon = Brand.mark,
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
            icon = Brand.icon,
        ) {
            OrganizaTheme { OrganizaRoot(core.uiState.value, actions, request.value) }
        }
        app.pause.value?.let { flow ->
            // The pause covers the screen and stays on top; it closes only through its own choices.
            Window(
                onCloseRequest = {},
                state = rememberWindowState(placement = WindowPlacement.Fullscreen),
                title = "Organiza pause",
                icon = Brand.icon,
                undecorated = true,
                alwaysOnTop = true,
                resizable = false,
            ) {
                OrganizaTheme { InterventionScreen(flow, core, onDone = { outcome -> app.choose(flow, outcome) }) }
            }
        }
    }
}

private const val HANDOFF_TRIES = 50

/**
 * GNOME matches a window to its launcher by WM_CLASS, which AWT otherwise takes from the main class
 * (`org-organiza-desktop-MainKt`). The installed launcher is organiza-Organiza.desktop.
 */
private fun nameWindows() {
    val toolkit = Toolkit.getDefaultToolkit()
    // Only the X11 toolkit has the field; it needs `--add-opens java.desktop/sun.awt.X11` (build.gradle.kts).
    runCatching { toolkit.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(toolkit, "organiza-Organiza") }
        .onFailure { System.err.println("organiza: window class not set: ${it.javaClass.simpleName}") }
}
