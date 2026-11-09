package org.organiza.desktop

import androidx.compose.runtime.mutableStateOf
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.JOptionPane
import org.organiza.app.CoreActions
import org.organiza.app.OrganizaCore
import org.organiza.app.Recorder
import org.organiza.domain.PackageName
import org.organiza.domain.port.BlockSurface
import org.organiza.domain.port.BlockTarget
import org.organiza.enforcement.BlockEngine
import org.organiza.enforcement.Friction
import org.organiza.enforcement.InterventionFlow
import org.organiza.enforcement.InterventionOutcome
import org.organiza.enforcement.SessionPolicy
import org.organiza.enforcement.UsageLimits
import org.organiza.presentation.Permission
import org.organiza.storage.FocusLog

/**
 * What only the desktop does around the shared core (ADR-0024, ADR-0025): it records the app in
 * front, and blocks during sessions and past daily limits with a full-screen pause while the app's
 * processes are stopped. The block engine runs on the UI thread, as on Android.
 */
class DesktopApp(
    val core: OrganizaCore,
    private val platform: DesktopPlatform,
    private val focus: FocusSource,
    private val freezer: Freezer = Freezer(platform.dataDir.resolve("frozen")),
) : AutoCloseable {
    /** The pause on screen, if any. */
    val pause = mutableStateOf<InterventionFlow?>(null)

    private val packages = platform.apps.associate { it.id to it.pkg }
    private val ticker = Executors.newSingleThreadScheduledExecutor { Thread(it, "organiza-blocker").apply { isDaemon = true } }
    private var limitTick: ScheduledFuture<*>? = null
    private var passEnd: ScheduledFuture<*>? = null
    private var dayEnd: ScheduledFuture<*>? = null
    @Volatile private var tracking: AutoCloseable? = null
    @Volatile private var current: Focus? = null
    @Volatile private var closed = false
    private var logged = false

    private val surface = object : BlockSurface {
        override fun showOverlay(target: BlockTarget) {
            val shown = pause.value
            if (shown != null && shown.target == target) return
            if (shown != null) log(shown, InterventionOutcome.Stayed)
            current?.takeIf { packages[it.app] == target.app }?.pid?.let(freezer::freeze)
            if (target is BlockTarget.Limit) thawAtMidnight()
            val strict = core.session.current.strict
            pause.value = InterventionFlow(target, strict, core.overridesToday, Friction.phrase(strict), reflects = strict && core.hasApiKey(), core.clock)
            logged = false
        }

        override fun dismissOverlay() {
            pause.value?.let { log(it, InterventionOutcome.Stayed) }
            pause.value = null
        }

        // The blocked app stays stopped behind the pause; there is no home screen to send it to.
        override fun goHome() = Unit
    }

    private val engine = BlockEngine(surface, core.clock, DEBOUNCE, ignored = setOf(DesktopPlatform.SELF), exempt = emptySet(), passes = core.passes)

    private val subscription = core.session.subscribe { policy ->
        platform.onMain {
            // A new policy frees what the old one stopped; anything still blocked is stopped again when it comes back.
            freezer.thawAll()
            engine.update(policy.blockList, policy != SessionPolicy.IDLE)
        }
    }

    init {
        freezer.recover()
        // shortcut: a stretch left open by a crash is dropped, since when it ended is unknown.
        core.onStores { stores -> FocusLog(stores.db).last()?.takeIf { it.app != null }?.let { FocusLog(stores.db).record(it.at, null) } }
        if (focus === GnomeFocus) GnomeFocus.updateIfOld()?.let(core::showMessage)
        startTracking()
    }

    /** Starts the focus source if it is not running. False when this desktop cannot report focus yet. */
    fun startTracking(): Boolean {
        if (tracking == null) tracking = focus.start(::onFocus)
        platform.tracking = tracking != null
        core.requestUiRefresh()
        return tracking != null
    }

    private fun onFocus(change: Focus?) {
        if (closed) return
        val at = core.clock.now()
        val app = change?.app?.let(packages::get)
        current = change?.takeIf { app != null }
        core.onStores { stores -> FocusLog(stores.db).record(at, app) }
        if (app != null) {
            platform.onMain {
                engine.onForeground(app)
                checkLimit(app)
            }
        }
        core.requestReconcile()
    }

    /** The person finished a pause. An override grants its pass and lets the app run again. */
    fun choose(flow: InterventionFlow, outcome: InterventionOutcome) {
        log(flow, outcome)
        if (outcome is InterventionOutcome.Overrode) {
            core.passes.grant(flow.target, Duration.ofMinutes(outcome.minutes.toLong()))
            freezer.thawAll()
            schedulePassEnd()
        }
        engine.dismiss()
    }

    private fun log(flow: InterventionFlow, outcome: InterventionOutcome) {
        if (logged || flow !== pause.value) return
        logged = true
        core.recordIntervention(flow, outcome)
    }

    private fun schedulePassEnd() {
        passEnd?.cancel(false)
        val until = core.passes.nextExpiry() ?: return
        passEnd = ticker.schedule(
            { platform.onMain { engine.recheck(); currentApp()?.let(::checkLimit) } },
            Duration.between(core.clock.now(), until).toMillis() + GRACE_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    /** Limits start again at midnight, so whatever one stopped runs again then. */
    private fun thawAtMidnight() {
        if (dayEnd?.isDone == false) return
        val zone = ZoneId.systemDefault()
        val now = core.clock.now()
        val midnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        dayEnd = ticker.schedule({ platform.onMain { freezer.thawAll() } }, Duration.between(now, midnight).toMillis() + GRACE_MS, TimeUnit.MILLISECONDS)
    }

    private fun currentApp(): PackageName? = current?.app?.let(packages::get)

    /** Blocks [app] once today's use reaches its limit, or checks again when it would (ADR-0018). */
    private fun checkLimit(app: PackageName) {
        limitTick?.cancel(false)
        val limit = core.hub.limits[app] ?: return
        val target = BlockTarget.Limit(app)
        if (core.passes.allows(target) || !platform.usage.permitted()) return
        core.onStores {
            val now = core.clock.now()
            val zone = ZoneId.systemDefault()
            // Other devices' time counts against the same limit (ADR-0028).
            val used = platform.usage.foregroundTime(app, now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant(), now) + core.devices.elsewhere(app)
            platform.onMain {
                if (currentApp() != app) return@onMain
                if (UsageLimits.over(limit, used)) {
                    engine.block(target)
                } else {
                    limitTick = ticker.schedule({ platform.onMain { checkLimit(app) } }, UsageLimits.left(limit, used).toMillis() + GRACE_MS, TimeUnit.MILLISECONDS)
                }
            }
        }
    }

    /** Ends the stretch in front, frees every stopped app and stops tracking. */
    override fun close() {
        if (closed) return
        closed = true
        tracking?.close()
        subscription.close()
        freezer.thawAll()
        val at = core.clock.now()
        val done = java.util.concurrent.CountDownLatch(1)
        core.onStores { stores -> FocusLog(stores.db).record(at, null); done.countDown() }
        done.await(2, TimeUnit.SECONDS)
        ticker.shutdownNow()
    }

    private companion object {
        val DEBOUNCE: Duration = Duration.ofMillis(800)
        const val GRACE_MS = 1_000L
    }
}

/** The shared actions, with the desktop's file pickers, dialogs, clipboard, microphone and setup steps. */
class DesktopActions(core: OrganizaCore, private val app: DesktopApp, private val platform: DesktopPlatform, private val focus: FocusSource) : CoreActions(core) {
    override fun record(): Recorder? = DesktopRecorder.start() ?: null.also { core.showMessage("No microphone could be opened") }

    override fun askVoiceConsent() {
        val answer = JOptionPane.showConfirmDialog(null, VOICE_NOTICE, "Send recordings to Groq?", JOptionPane.OK_CANCEL_OPTION)
        if (answer == JOptionPane.OK_OPTION) setVoiceConsent(true)
    }

    override fun grant(permission: Permission) {
        val message = when (permission) {
            Permission.TRACKING, Permission.USAGE, Permission.ACCESSIBILITY ->
                if (app.startTracking()) "Usage tracking is on" else focus.enable()
            Permission.AUTOSTART -> platform.autostart.enable()
            Permission.TRAY -> {
                platform.trayWanted = true
                if (WINDOWS) "Closing the window now leaves Organiza in the notification area" else if (java.awt.SystemTray.isSupported()) "Closing the window now leaves Organiza in the top bar" else
                    "Your desktop shows no top bar icons. On GNOME, turn on the AppIndicator extension"
            }
            Permission.SHORTCUT -> Shortcut.install().also { platform.shortcut = Shortcut.installed() }
            else -> "Not needed on this computer"
        }
        core.showMessage(message)
        core.requestUiRefresh()
    }

    override fun importCalendar() {
        val file = choose("Import a calendar", "*.ics") ?: return
        try {
            core.importCalendar(file.readText())
        } catch (_: IOException) {
            core.showMessage("Could not read that file")
        }
    }

    /** Photos are read with Tesseract on this computer when it is installed; nothing is sent. */
    override fun pickTimetablePhoto() {
        if (output("tesseract", "--version") == null) return core.showMessage("Reading photos needs Tesseract. Install tesseract-ocr, or paste the text")
        val file = choose("Choose a timetable photo", "*.png;*.jpg;*.jpeg") ?: return
        core.schedule.readPhoto { output("tesseract", file.absolutePath, "stdout") }
    }

    override fun shareInvite() = core.inviteToShare { invite ->
        if (invite == null) {
            core.showMessage("Create or join a challenge first")
        } else {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(invite), null)
            core.showMessage("Invite copied. Share it like a password")
        }
    }

    override fun shareDeviceCode() {
        val code = core.devices.code() ?: return core.showMessage("Start sharing usage first")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
        core.showMessage("Device code copied. Paste it on your other device, and share it like a password")
    }

    override fun deleteAccount() {
        val answer = JOptionPane.showConfirmDialog(null, DELETE_NOTICE, "Delete account and cloud data?", JOptionPane.OK_CANCEL_OPTION)
        if (answer == JOptionPane.OK_OPTION) core.sync.deleteAccount()
    }

    private fun choose(title: String, pattern: String): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply { file = pattern; isVisible = true }
        return dialog.files.firstOrNull()
    }
}
