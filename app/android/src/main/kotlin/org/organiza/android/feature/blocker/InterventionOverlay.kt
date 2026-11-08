package org.organiza.android.feature.blocker

import org.organiza.feature.blocker.InterventionScreen
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.organiza.android.OrganizaApp
import org.organiza.ui.OrganizaTheme
import org.organiza.domain.port.BlockSurface
import org.organiza.domain.port.BlockTarget
import org.organiza.enforcement.Friction
import org.organiza.enforcement.InterventionFlow
import org.organiza.enforcement.InterventionOutcome

/**
 * The intervention screen (ADR-0016): a full-screen overlay owned by the accessibility service, so
 * it needs no extra permission (ADR-0005). Back does nothing; only the screen's own choices close it.
 * Each intervention is logged once: as the person's choice, or as staying when they leave it for
 * another app or the session ends. A service shutdown closes it without a log entry.
 */
class InterventionOverlay(
    private val service: AccessibilityService,
    private val app: OrganizaApp,
    private val onChosen: (InterventionFlow, InterventionOutcome) -> Unit,
) : BlockSurface {
    private val windows = service.getSystemService(WindowManager::class.java)
    private val current = mutableStateOf<InterventionFlow?>(null)
    private var logged = false
    private var root: FrameLayout? = null
    private var owner: OverlayOwner? = null

    override fun showOverlay(target: BlockTarget) {
        val shown = current.value
        if (shown != null && shown.target == target && root != null) return
        if (shown != null) log(shown, InterventionOutcome.Stayed)
        val strict = app.session.current.strict
        current.value = InterventionFlow(
            target,
            strict,
            app.overridesToday,
            Friction.phrase(strict),
            reflects = strict && app.hasApiKey(),
            app.clock,
        )
        logged = false
        if (root == null) attach()
    }

    override fun dismissOverlay() {
        current.value?.let { log(it, InterventionOutcome.Stayed) }
        close()
    }

    /** Closes without logging, for when the service itself stops: that is not the person's choice. */
    fun discard() {
        logged = true
        close()
    }

    private fun close() {
        current.value = null
        root?.let(windows::removeView)
        root = null
        owner?.destroy()
        owner = null
    }

    override fun goHome() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    private fun choose(flow: InterventionFlow, outcome: InterventionOutcome) {
        log(flow, outcome)
        onChosen(flow, outcome)
    }

    private fun log(flow: InterventionFlow, outcome: InterventionOutcome) {
        if (logged || flow !== current.value) return
        logged = true
        app.recordIntervention(flow, outcome)
    }

    private fun attach() {
        val lifecycle = OverlayOwner().also { owner = it }
        val compose = ComposeView(service).apply {
            setContent {
                OrganizaTheme {
                    current.value?.let { flow ->
                        InterventionScreen(flow, app, onDone = { outcome -> choose(flow, outcome) })
                    }
                }
            }
        }
        val frame = BackBlockingFrame(service).apply {
            addView(compose)
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Focusable, so the phrase can be typed. The keyboard resizes the window rather than covering it.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }
        windows.addView(frame, params)
        root = frame
    }

    /** Swallows Back, so the intervention closes only through its own choices. */
    @SuppressLint("ViewConstructor")
    private class BackBlockingFrame(context: Context) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
    }

    /** Compose needs a lifecycle and saved state, which a service window does not have. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        init {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
