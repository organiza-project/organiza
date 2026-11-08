package org.organiza.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.hasText
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.domain.port.Clock
import org.organiza.storage.FocusChange
import org.organiza.storage.FocusLog
import org.organiza.testing.FakeSecretStore
import org.organiza.ui.OrganizaRoot
import org.organiza.ui.OrganizaTheme

@OptIn(ExperimentalTestApi::class)
class DesktopAppTest {
    private val db = DesktopStorage.open(Files.createTempDirectory("organiza").resolve("a.db"), FakeSecretStore())
    private var now = Instant.parse("2026-11-09T09:00:00Z")
    private val focus = FakeFocus()
    private val apps = listOf(
        LinuxApp("org.gnome.Nautilus", "Files", null, "Utilities", null),
        LinuxApp("google-chrome", "Chrome", null, "Internet", null),
    )
    private val app = DesktopApp(db, Clock { now }, focus, apps)

    @AfterTest
    fun close() = app.close()

    @Test
    fun `the shared screens render and switch tabs on the desktop`() = runComposeUiTest {
        setContent { OrganizaTheme { OrganizaRoot(app.ui.value, app) } }
        onNodeWithText("Voice").assertDoesNotExist()
        onNodeWithContentDescription("Profile").performClick()
        onNodeWithText("Voice").assertExists()
    }

    @Test
    fun `an action Linux cannot do yet says so on screen`() = runComposeUiTest {
        setContent { OrganizaTheme { OrganizaRoot(app.ui.value, app) } }
        onNodeWithText("Not on Linux yet", substring = true).assertDoesNotExist()
        app.syncArena()
        onNodeWithText("Not on Linux yet", substring = true).assertExists()
    }

    @Test
    fun `the time an app is in front shows on the metrics tab`() = runComposeUiTest {
        setContent { OrganizaTheme { OrganizaRoot(app.ui.value, app) } }
        focus.report("org.gnome.Nautilus")
        now += Duration.ofMinutes(25)
        focus.report("unknown-window")
        now += Duration.ofMinutes(5)
        app.refreshUi()
        onNodeWithContentDescription("Metrics").performClick()
        waitUntilExactlyOneExists(hasContentDescription("Files, 25 m today"))
        onNodeWithText("Chrome", substring = true).assertDoesNotExist()
    }

    @Test
    fun `closing the app ends the stretch in front`() {
        focus.report("google-chrome")
        now += Duration.ofMinutes(3)
        app.close()
        assertEquals(FocusChange(now, null), FocusLog(db).last())
    }

    @Test
    fun `without a focus source the dashboard asks for usage access`() = runComposeUiTest {
        val blind = DesktopApp(db, Clock { now }, FocusSource { null }, apps)
        setContent { OrganizaTheme { OrganizaRoot(blind.ui.value, blind) } }
        onNodeWithContentDescription("Metrics").performClick()
        waitUntilExactlyOneExists(hasText("Allow usage access"))
        blind.close()
    }

    private class FakeFocus : FocusSource {
        private var listener: ((String?) -> Unit)? = null

        override fun start(onChange: (String?) -> Unit): AutoCloseable {
            listener = onChange
            return AutoCloseable { listener = null }
        }

        fun report(id: String?) = listener?.invoke(id)
    }
}
