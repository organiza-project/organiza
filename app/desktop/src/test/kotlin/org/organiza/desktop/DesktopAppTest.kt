package org.organiza.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.dp
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.organiza.app.OrganizaCore
import org.organiza.domain.OverrideReason
import org.organiza.enforcement.InterventionOutcome
import org.organiza.enforcement.UsageLimits
import org.organiza.storage.FocusLog
import org.organiza.storage.FocusTier
import org.organiza.testing.FakeSecretStore
import org.organiza.ui.OrganizaRoot
import org.organiza.ui.OrganizaTheme

@OptIn(ExperimentalTestApi::class)
class DesktopAppTest {
    private val home: Path = Files.createTempDirectory("organiza-home")
    private val env = mapOf("HOME" to home.toString(), "XDG_DATA_HOME" to "$home/data", "XDG_CONFIG_HOME" to "$home/config")
    private val files = LinuxApp("org.gnome.Nautilus", "Files", null, "Utilities", null)
    private val platform = DesktopPlatform(
        keyring = FakeSecretStore(),
        apps = listOf(files, LinuxApp("google-chrome", "Chrome", null, "Internet", null)),
        env = env,
        database = home.resolve("data/organiza/organiza.db"),
        autostart = SystemdAutostart(home.resolve("config")),
    )
    private val core = OrganizaCore(platform).also { it.start() }
    private val focus = FakeFocus()
    private val app = DesktopApp(core, platform, focus)
    private val actions = DesktopActions(core, app, platform, focus)
    private val child = ProcessBuilder("sleep", "60").start()

    @AfterTest
    fun end() {
        app.close()
        child.destroy()
    }

    private fun eventually(what: String, condition: () -> Boolean) {
        val until = System.nanoTime() + 5_000_000_000
        while (!condition()) {
            check(System.nanoTime() < until) { "timed out waiting for $what" }
            Thread.sleep(20)
        }
    }

    private fun state(pid: Long): Char = Files.readString(Path.of("/proc/$pid/stat")).substringAfterLast(')').trim().first()

    @Test
    fun `the habits tab shows habits on the desktop`() = runComposeUiTest {
        core.onboarded = true
        actions.addHabit("Read twenty pages")
        setContent { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } }
        onNodeWithContentDescription("Habits").performClick()
        waitUntilExactlyOneExists(hasText("Read twenty pages"), 5_000)
    }

    @Test
    fun `the time an app is in front shows on the metrics tab`() = runComposeUiTest {
        core.onboarded = true
        focus.report(Focus("org.gnome.Nautilus", null))
        core.clockOffsetForTesting = Duration.ofMinutes(3)
        focus.report(Focus("unknown-window", null))
        core.requestUiRefresh()
        setContent { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } }
        onNodeWithContentDescription("Metrics").performClick()
        waitUntilExactlyOneExists(hasContentDescription("Files, 3 m today"), 5_000)
    }

    @Test
    fun `a wide window puts the tabs in a sidebar with the copilot`() = runComposeUiTest {
        core.onboarded = true
        core.requestUiRefresh()
        setContent { Box(Modifier.size(1200.dp, 800.dp)) { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } } }
        waitUntilExactlyOneExists(hasContentDescription("Copilot"), 5_000)
        onNodeWithContentDescription("Open the copilot").assertDoesNotExist()
        onNodeWithContentDescription("Profile").performClick()
        onNodeWithText("Voice").assertExists()
    }

    @Test
    fun `the sidebar runs from Home down to Profile`() = runComposeUiTest {
        core.onboarded = true
        core.requestUiRefresh()
        setContent { Box(Modifier.size(1200.dp, 800.dp)) { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } } }
        waitUntilExactlyOneExists(hasContentDescription("Copilot"), 5_000)
        val order = listOf("Home", "Habits", "Metrics", "Arena", "Profile")
        val tops = order.map { onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(order, order.sortedBy { tops[order.indexOf(it)] })
    }

    @Test
    fun `onboarding asks for the desktop's own permissions`()= runComposeUiTest {
        setContent { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } }
        waitUntilExactlyOneExists(hasText("Start"), 5_000)
        onNodeWithText("Start").performClick()
        waitUntilExactlyOneExists(hasText("Usage tracking"), 5_000)
        onNodeWithText("Start at login", substring = true).assertExists()
        onNodeWithText("Copilot shortcut", substring = true).assertExists()
        onNodeWithText("Uninstall protection", substring = true).assertDoesNotExist()
    }

    @Test
    fun `allowing the top bar icon takes effect without a restart`() = runComposeUiTest {
        setContent { androidx.compose.foundation.text.BasicText(if (platform.trayWanted) "tray on" else "tray off") }
        waitUntilExactlyOneExists(hasText("tray off"), 5_000)
        platform.trayWanted = true
        waitUntilExactlyOneExists(hasText("tray on"), 5_000)
    }

    @Test
    fun `a blocked app is paused and stopped until the person overrides`() {
        actions.setFocusApp(checkNotNull(files.pkg).value, true)
        actions.startFocus(25, strict = false, shortForm = false, tier = FocusTier.STANDARD)
        eventually("the session") { core.session.current.blockList.packages.isNotEmpty() }
        Thread.sleep(200)
        focus.report(Focus("org.gnome.Nautilus", child.pid()))
        eventually("the pause") { app.pause.value != null }
        assertEquals('T', state(child.pid()))
        val flow = assertNotNull(app.pause.value)
        javax.swing.SwingUtilities.invokeAndWait {
            app.choose(flow, InterventionOutcome.Overrode(5, OverrideReason.WORK, "reply", Duration.ofSeconds(20), reflected = false))
        }
        assertNull(app.pause.value)
        assertEquals('S', state(child.pid()))
        assertTrue(core.passes.allows(flow.target))
    }

    @Test
    fun `an app stopped at its daily limit runs again at midnight`() {
        actions.setLimit(checkNotNull(files.pkg).value, UsageLimits.MINUTES.first.toLong())
        eventually("the limit") { core.hub.limits.isNotEmpty() }
        val zone = ZoneId.systemDefault()
        val midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
        core.clockOffsetForTesting = Duration.between(Instant.now(), midnight.minus(Duration.ofMinutes(UsageLimits.MINUTES.first + 2L)))
        focus.report(Focus("org.gnome.Nautilus", child.pid()))
        core.clockOffsetForTesting = Duration.between(Instant.now(), midnight.minusSeconds(2))
        focus.report(Focus("org.gnome.Nautilus", child.pid()))
        eventually("the pause") { app.pause.value != null }
        assertEquals('T', state(child.pid()))
        val flow = assertNotNull(app.pause.value)
        javax.swing.SwingUtilities.invokeAndWait { app.choose(flow, InterventionOutcome.Stayed) }
        eventually("midnight") { state(child.pid()) == 'S' }
    }

    @Test
    fun `closing the app ends the stretch in front and frees stopped apps`() {
        focus.report(Focus("google-chrome", null))
        app.close()
        val last = java.util.concurrent.atomic.AtomicReference<Any?>("unset")
        val done = java.util.concurrent.CountDownLatch(1)
        core.onStores { last.set(FocusLog(it.db).last()?.app); done.countDown() }
        done.await()
        assertNull(last.get())
    }

    private class FakeFocus : FocusSource {
        private var listener: ((Focus?) -> Unit)? = null

        override fun start(onChange: (Focus?) -> Unit): AutoCloseable {
            listener = onChange
            return AutoCloseable { listener = null }
        }

        fun report(change: Focus?) = listener?.invoke(change)
    }
}
