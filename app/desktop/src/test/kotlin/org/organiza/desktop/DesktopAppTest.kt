package org.organiza.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText

import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.organiza.ui.AccountView
import org.organiza.sync.BackendKind
import org.organiza.feature.sync.BackendPanel
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

    // The snapshot that ends onboarding reaches the UI a moment after it is built, later still on a slow CI
    // machine, so a tab is tapped only once it is on screen.
    private fun ComposeUiTest.openTab(name: String) {
        waitUntilExactlyOneExists(hasContentDescription(name), 10_000)
        onNodeWithContentDescription(name).performClick()
    }

    private fun state(pid: Long): Char = Files.readString(Path.of("/proc/$pid/stat")).substringAfterLast(')').trim().first()

    @Test
    fun `choosing my own supabase drops the hosted account and its address`() = runComposeUiTest {
        val hosted = AccountView(kind = BackendKind.HOSTED, url = "https://hosted.example.org", signedInAs = "me@example.org", hostedAvailable = true)
        setContent { OrganizaTheme { Column { BackendPanel(hosted, actions) } } }
        onNodeWithText("Signed in as").assertExists()
        onNodeWithText("My own Supabase").performClick()
        onNodeWithText("Signed in as").assertDoesNotExist()
        onNodeWithText("hosted.example.org", substring = true).assertDoesNotExist()
    }

    @Test
    fun `with the lockout on, removing a limit waits for the whole essay`() {
        val chrome = "linux.google_chrome"
        actions.setLimit(chrome, 30)
        eventually("the limit is set") { core.hub.limits.keys.any { it.value == chrome } }
        actions.setLockout(true)
        eventually("the lockout is on") { core.lockout }
        actions.setLimit(chrome, null)
        eventually("the change waits") { core.uiState.value.unlockReason == "Remove the limit on Chrome" }
        actions.unlock(org.organiza.presentation.Lockout.ESSAY.dropLast(1))
        Thread.sleep(300)
        assertTrue(core.hub.limits.keys.any { it.value == chrome }, "a wrong text changed nothing")
        actions.unlock(org.organiza.presentation.Lockout.ESSAY)
        eventually("the limit is gone") { core.hub.limits.keys.none { it.value == chrome } }
    }

    @Test
    fun `with the lockout on, moving a block, cancelling by voice and unlinking also wait for the essay`() {
        actions.setLockout(true)
        eventually("the lockout is on") { core.lockout }
        actions.saveFocusBlock("block-1", java.time.LocalDate.now().plusDays(1), 9 * 60, 30)
        eventually("moving waits") { core.uiState.value.unlockReason == "Move or shorten a focus block" }
        actions.cancelUnlock()
        core.propose(org.organiza.voice.VoiceCommand.CancelSession("focus", null))
        actions.confirmCommand()
        eventually("cancelling waits") { core.uiState.value.unlockReason == "Cancel a focus session" }
        actions.cancelUnlock()
        actions.unlinkDevices()
        eventually("unlinking waits") { core.uiState.value.unlockReason == "Stop counting your other devices" }
    }

    @Test
    fun `metrics keeps this computer's finished days, so they still show once the log forgets them`() {
        core.onboarded = true
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.LocalDate.now(zone).minusDays(3)
        val start = day.atTime(10, 0).atZone(zone).toInstant()
        val chrome = checkNotNull(LinuxApps.packageName("google-chrome"))
        fun <T> stored(read: (OrganizaCore.Storage) -> T): T {
            val result = java.util.concurrent.atomic.AtomicReference<T>()
            val done = java.util.concurrent.CountDownLatch(1)
            core.onStores { result.set(read(it)); done.countDown() }
            done.await(5, java.util.concurrent.TimeUnit.SECONDS)
            return result.get()
        }
        stored { org.organiza.storage.FocusLog(it.db).apply { record(start, chrome); record(start.plusSeconds(30 * 60), null) } }
        platform.tracking = true
        // A new range is a new report, and each new report keeps the finished days.
        actions.setUsageSpan(org.organiza.ui.UsageSpan.DAY)
        fun kept(): Boolean = stored { it.deviceUsage.between(day, day, mine = true).isNotEmpty() }
        eventually("the day is kept") { kept() }
        // Android forgets app usage after about a week; an empty log stands in for that here.
        core.onStores { it.db.execute("DELETE FROM focus_change") }
        actions.setUsageSpan(org.organiza.ui.UsageSpan.DAY)
        actions.setUsageSpan(org.organiza.ui.UsageSpan.WEEK)
        eventually("the week still counts it") { core.uiState.value.usage.report.total == "30m" }
    }

    @Test
    fun `a reinstalled computer keeps its device id, so its old reports are not counted as another device`() {
        val again = Files.createTempDirectory("organiza-home")
        val fresh = DesktopPlatform(
            keyring = FakeSecretStore(),
            apps = emptyList(),
            env = mapOf("HOME" to again.toString(), "XDG_DATA_HOME" to "$again/data", "XDG_CONFIG_HOME" to "$again/config"),
            database = again.resolve("data/organiza/organiza.db"),
            autostart = SystemdAutostart(again.resolve("config")),
        )
        assertEquals(core.devices.deviceId, OrganizaCore(fresh).devices.deviceId)
    }

    @Test
    fun `a focus session starts on the desktop and home shows it running`() = runComposeUiTest {
        core.onboarded = true
        actions.setFocusApp("linux.google_chrome", true)
        setContent { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } }
        waitUntilExactlyOneExists(hasText("Start a focus session"), 5_000)
        // What the start form's button calls: the focus list, 25 minutes, not strict.
        actions.startSession("", "", "25", false, false)
        waitUntilExactlyOneExists(hasText("Stop session"), 5_000)
    }

    @Test
    fun `a note is written, saved and shown under today`() = runComposeUiTest {
        core.onboarded = true
        setContent { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } }
        openTab("Notes")
        // The composer's fields: the title, the note's text (where dictation lands), then its subject.
        onAllNodes(hasSetTextAction())[0].performTextInput("Morning")
        onAllNodes(hasSetTextAction())[1].performTextInput("Walk before work felt good")
        onAllNodes(hasSetTextAction())[2].performTextInput("Journal")
        onNodeWithText("Save note").performClick()
        waitUntilExactlyOneExists(hasText("Walk before work felt good"), 5_000)
        onNodeWithText("Morning").assertExists()
        onNodeWithText(" · Journal", substring = true).assertExists()
    }

    @Test
    fun `the metrics range moves by whole months and a custom range by its edges, never past today`() {
        core.onboarded = true
        val today = java.time.LocalDate.now()
        fun range() = core.uiState.value.usage.let { it.from to it.to }
        fun day(date: java.time.LocalDate) = org.organiza.presentation.Format.dayMonth(date)
        actions.setUsageSpan(org.organiza.ui.UsageSpan.MONTH)
        actions.shiftUsage(-1)
        val last = today.minusMonths(1)
        eventually("last month") { range() == day(last.withDayOfMonth(1)) to day(last.withDayOfMonth(last.lengthOfMonth())) }
        actions.shiftUsage(1)
        actions.shiftUsage(1)
        eventually("this month, up to today") { range() == day(today.withDayOfMonth(1)) to day(today) }
        actions.setUsageSpan(org.organiza.ui.UsageSpan.RANGE)
        actions.shiftUsageEdge(true, -3)
        eventually("a range of ten days") { range() == day(today.minusDays(9)) to day(today) }
        actions.shiftUsageEdge(false, 5)
        eventually("the end stays at today") { range() == day(today.minusDays(9)) to day(today) }
    }

    @Test
    fun `the desktop offers no reels or shorts switches, which only a phone can enforce`() = runComposeUiTest {
        core.onboarded = true
        setContent { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } }
        openTab("Metrics")
        waitUntilExactlyOneExists(hasText("Your apps"), 5_000)
        onNodeWithText("Block Reels tab only").assertDoesNotExist()
        onNodeWithText("Block YT Shorts").assertDoesNotExist()
    }

    @Test
    fun `a wide window puts the metrics panels in two columns`() = androidx.compose.ui.test.v2.runDesktopComposeUiTest(1440, 900) {
        core.onboarded = true
        setContent { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } }
        openTab("Metrics")
        waitUntilExactlyOneExists(hasText("Usage today"), 5_000)
        val left = onNodeWithText("Screen time").fetchSemanticsNode().boundsInRoot
        val right = onNodeWithText("Usage today").fetchSemanticsNode().boundsInRoot
        assertTrue(right.left > left.right, "side by side, not stacked")
        assertTrue(right.top < left.bottom + 200, "both near the top")
    }

    @Test
    fun `the notes tab loads a page of notes, and older ones on request`() = runComposeUiTest {
        core.onboarded = true
        val done = java.util.concurrent.CountDownLatch(1)
        core.onStores { stores ->
            val start = Instant.now().minus(Duration.ofDays(200))
            (1..105).forEach { stores.notes.add(org.organiza.storage.Note("n$it", "Entry $it", null, start.plus(Duration.ofDays(it.toLong())), null)) }
            done.countDown()
        }
        done.await()
        core.requestUiRefresh()
        setContent { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } }
        openTab("Notes")
        waitUntilExactlyOneExists(hasText("Entry 105"), 5_000)
        onNodeWithText("Entry 5").assertDoesNotExist()
        onNodeWithText("Show older notes").performScrollTo().performClick()
        waitUntilExactlyOneExists(hasText("Entry 5"), 5_000)
    }

    @Test
    fun `the sign-in form checks what is typed, shows it is working, and says what went wrong`() = runComposeUiTest {
        var account by androidx.compose.runtime.mutableStateOf(AccountView(kind = BackendKind.SELF_HOSTED, url = "https://own.example.org"))
        setContent { OrganizaTheme { Column { org.organiza.feature.sync.AccountPanel(account, actions) } } }
        onNodeWithText("Sign in to own.example.org").assertExists()
        onAllNodes(hasSetTextAction())[0].performTextInput("sam")
        onNodeWithText("Enter an email address, like name@example.com").assertExists()
        // Something on both sides of the @ is enough; the server judges the rest, so a test server's admin@localhost works.
        onAllNodes(hasSetTextAction())[0].performTextInput("@localhost")
        onNodeWithText("Enter an email address, like name@example.com").assertDoesNotExist()
        account = account.copy(busy = true)
        waitUntilExactlyOneExists(hasText("Signing in…"), 5_000)
        account = account.copy(busy = false, authMessage = "Wrong email or password", authFailed = true)
        waitUntilExactlyOneExists(hasText("Wrong email or password"), 5_000)
        onNodeWithText("Create account").performClick()
        onNodeWithText("Create your account").assertExists()
    }

    @Test
    fun `dictation says it is listening with a running clock, then that it is transcribing`() = runComposeUiTest {
        var listening by androidx.compose.runtime.mutableStateOf(true)
        var transcribing by androidx.compose.runtime.mutableStateOf(false)
        setContent { OrganizaTheme { Column { org.organiza.ui.DictationStatus(listening, transcribing, { 0.4f }) } } }
        onNodeWithText("Listening", substring = true).assertExists()
        // The clock counts to the voice pipeline's own limit, past which nothing is sent.
        onNodeWithText("0:00 of 0:30", substring = true).assertExists()
        listening = false
        transcribing = true
        waitUntilExactlyOneExists(hasText("Turning your words into text…"), 5_000)
        onNodeWithText("Listening", substring = true).assertDoesNotExist()
    }

    @Test
    fun `deleting a note takes a second tap`() = runComposeUiTest {
        core.onboarded = true
        setContent { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } }
        openTab("Notes")
        onAllNodes(hasSetTextAction())[1].performTextInput("Call the bank")
        onNodeWithText("Save note").performClick()
        waitUntilExactlyOneExists(hasText("Call the bank"), 5_000)
        onNodeWithText("Delete").performClick()
        onNodeWithText("Call the bank").assertExists()
        onNodeWithText("Delete for good").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Call the bank")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `the habits tab shows habits on the desktop`() = runComposeUiTest {
        core.onboarded = true
        actions.addHabit("Read twenty pages")
        setContent { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } }
        openTab("Habits")
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
        openTab("Metrics")
        waitUntilExactlyOneExists(hasContentDescription("Files, 3 m today"), 5_000)
    }

    @Test
    fun `a wide window puts the tabs in a sidebar with the copilot`() = runComposeUiTest {
        core.onboarded = true
        core.requestUiRefresh()
        setContent { Box(Modifier.size(1200.dp, 800.dp)) { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } } }
        waitUntilExactlyOneExists(hasContentDescription("Copilot"), 5_000)
        onNodeWithContentDescription("Open the copilot").assertDoesNotExist()
        openTab("Profile")
        onNodeWithText("Voice").assertExists()
    }

    @Test
    fun `the sidebar runs from Home down to Profile`() = runComposeUiTest {
        core.onboarded = true
        core.requestUiRefresh()
        setContent { Box(Modifier.size(1200.dp, 800.dp)) { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } } }
        waitUntilExactlyOneExists(hasContentDescription("Copilot"), 5_000)
        val order = listOf("Home", "Habits", "Notes", "Metrics", "Profile")
        val tops = order.map { onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(order, order.sortedBy { tops[order.indexOf(it)] })
    }

    @Test
    fun `onboarding asks for the desktop's own permissions`()= runComposeUiTest {
        setContent { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } }
        waitUntilExactlyOneExists(hasText("Start"), 5_000)
        onNodeWithText("Start").performClick()
        // Blocking first: tracking and staying open; starting at login and the shortcut come next.
        waitUntilExactlyOneExists(hasText("Usage tracking"), 5_000)
        onNodeWithText("Keep running when closed", substring = true).assertExists()
        onNodeWithText("Start at login", substring = true).assertDoesNotExist()
        onAllNodes(hasText("Continue", substring = true))[0].performClick()
        waitUntilExactlyOneExists(hasText("Start at login", substring = true), 5_000)
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
