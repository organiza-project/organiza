package org.organiza.android.feature.blocker

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Duration
import kotlinx.coroutines.delay
import org.organiza.android.ui.Action
import org.organiza.android.ui.ButtonKind
import org.organiza.android.ui.ChoiceRow
import org.organiza.android.ui.Field
import org.organiza.android.ui.Panel
import org.organiza.android.ui.Space
import org.organiza.android.ui.Theme
import org.organiza.android.ui.Txt
import org.organiza.android.ui.Type
import org.organiza.domain.OverrideReason
import org.organiza.domain.port.Clock
import org.organiza.domain.port.BlockTarget
import org.organiza.enforcement.InterventionFlow
import org.organiza.enforcement.InterventionOutcome
import org.organiza.enforcement.InterventionStep
import org.organiza.enforcement.PhraseMatch
import org.organiza.presentation.Reasons

/** What the intervention screen needs from the app, so it can be tested without one. */
interface InterventionHost {
    val clock: Clock

    fun targetLabel(target: BlockTarget): String

    fun sessionLine(strict: Boolean): String

    /** Hands back an AI reflection on the main thread, or null when there is none. */
    fun reflect(flow: InterventionFlow, done: (String?) -> Unit)
}

/** Draws [flow] one step at a time. Stay focused is on every step (ADR-0016). */
@Composable
fun InterventionScreen(flow: InterventionFlow, app: InterventionHost, onDone: (InterventionOutcome) -> Unit) {
    val colors = Theme.colors
    // The flow is plain Kotlin and keeps its identity, so a counter tells Compose when it has
    // moved on. Steps get the values they show as parameters: Compose skips a step whose
    // parameters are unchanged, and it ignores parameters a step does not read.
    var revision by remember(flow) { mutableIntStateOf(0) }
    fun changed() = revision++
    val step = flow.step.also { revision }
    fun finish(outcome: InterventionOutcome?) {
        changed()
        outcome?.let(onDone)
    }
    LaunchedEffect(flow, step is InterventionStep.Pause) {
        while (flow.step is InterventionStep.Pause) {
            delay(TICK_MS)
            flow.tick()
            changed()
        }
    }
    Box(Modifier.fillMaxSize().background(colors.ground).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp)
                .padding(horizontal = Space.margin, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Space.gap),
        ) {
            Header(flow, app)
            when (step) {
                InterventionStep.Choice -> ChoiceStep(flow, ::changed)
                is InterventionStep.Pause -> PauseStep(Duration.between(app.clock.now(), step.until).toMillis().coerceAtLeast(0))
                InterventionStep.Reason -> ReasonStep(flow, ::changed)
                is InterventionStep.Reflect -> ReflectStep(flow, step, app, ::changed)
                InterventionStep.Type -> TypeStep(flow, flow.wordsCorrect, PhraseMatch.matches(flow.phrase.text, flow.typed), ::changed)
                InterventionStep.Length -> LengthStep { minutes -> finish(flow.choose(minutes)) }
                InterventionStep.Done -> Unit
            }
            if (step != InterventionStep.Done) {
                Spacer(Modifier.height(4.dp))
                Action(if (flow.canOverride) "Stay focused" else "OK", ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth()) {
                    finish(flow.stayFocused())
                }
            }
        }
    }
}

@Composable
private fun Header(flow: InterventionFlow, app: InterventionHost) {
    val colors = Theme.colors
    val name = app.targetLabel(flow.target)
    val title = when (flow.target) {
        is BlockTarget.Guard -> "Organiza's settings are locked"
        is BlockTarget.ShortForm -> "$name is paused"
        else -> "$name is blocked"
    }
    Txt(title, Type.title, modifier = Modifier.semantics { heading() })
    Txt(app.sessionLine(flow.strict), Type.body, colors.muted)
}

@Composable
private fun ChoiceStep(flow: InterventionFlow, changed: () -> Unit) {
    val colors = Theme.colors
    if (!flow.canOverride) {
        Txt("A strict session protects the settings that run it. They open again when the session ends.", Type.body)
        return
    }
    Txt("You chose to protect this time. The urge to check usually fades within a minute.", Type.body)
    Txt(
        "To open it anyway: a ${seconds(flow.pause)} pause, a reason, and a phrase to type. It is logged as your choice.",
        Type.small,
        colors.muted,
    )
    Action("Open it anyway", ButtonKind.TEXT) {
        flow.beginOverride()
        changed()
    }
}

@Composable
private fun PauseStep(leftMs: Long) {
    val colors = Theme.colors
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath scale",
    )
    Panel {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(180.dp).semantics { contentDescription = "Breathing circle" }) {
                drawCircle(colors.raised, radius = size.minDimension / 2)
                drawCircle(colors.data, radius = size.minDimension / 2 * scale, alpha = 0.18f)
                drawCircle(colors.data, radius = size.minDimension / 2 * scale * 0.12f)
            }
        }
        Txt(
            "Breathe in as the circle grows, out as it shrinks.",
            Type.body.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
        Txt(
            "${(leftMs + 999) / 1000} s",
            Type.figure,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun ReasonStep(flow: InterventionFlow, changed: () -> Unit) {
    val colors = Theme.colors
    var chosen by remember(flow) { mutableStateOf<OverrideReason?>(null) }
    var note by remember(flow) { mutableStateOf("") }
    Txt("What do you need it for?", Type.section)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OverrideReason.entries.forEach { reason ->
            ChoiceRow(Reasons.label(reason), null, chosen == reason) { chosen = reason }
        }
    }
    Spacer(Modifier.height(4.dp))
    Field(note, { note = it.take(InterventionFlow.MAX_NOTE) }, "A few words, if you like")
    val where = if (flow.strict && flow.reflects) {
        "Kept on this phone. In a strict session with an AI key, it is sent for a reflection with names removed."
    } else {
        "Kept on this phone, for your own look back."
    }
    Txt(where, Type.small, colors.muted)
    Action("Continue", ButtonKind.SECONDARY, enabled = chosen != null) {
        chosen?.let { flow.chooseReason(it, note) }
        changed()
    }
}

@Composable
private fun ReflectStep(flow: InterventionFlow, step: InterventionStep.Reflect, app: InterventionHost, changed: () -> Unit) {
    LaunchedEffect(flow) {
        app.reflect(flow) { text ->
            flow.showReflection(text)
            changed()
        }
    }
    Panel {
        Txt("A second look", Type.section)
        Spacer(Modifier.height(4.dp))
        Txt(step.text ?: "Thinking about what you wrote", Type.body, if (step.text == null) Theme.colors.muted else Theme.colors.text)
    }
    Action("Continue", ButtonKind.SECONDARY, enabled = step.text != null) {
        flow.continueAfterReflection()
        changed()
    }
}

@Composable
private fun TypeStep(flow: InterventionFlow, wordsCorrect: Int, matches: Boolean, changed: () -> Unit) {
    val colors = Theme.colors
    var pasted by remember(flow) { mutableStateOf(false) }
    val field = rememberTextFieldState()
    val words = PhraseMatch.words(flow.phrase.text)
    // Every change goes through the flow as it happens, so the paste check sees each keystroke.
    // Collecting the text afterwards would merge fast typing into one jump that looks like a paste.
    val guard = remember(flow) {
        InputTransformation {
            pasted = !flow.type(toString())
            if (pasted) replace(0, length, "")
            changed()
        }
    }
    Txt("Type this, word by word", Type.section)
    Panel { Txt(flow.phrase.text, Type.section) }
    // No copy or paste menu: the phrase has to be typed.
    CompositionLocalProvider(LocalTextToolbar provides NoTextToolbar) {
        BasicTextField(
            state = field,
            inputTransformation = guard,
            textStyle = Type.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.text),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .background(colors.surface, RoundedCornerShape(Space.radiusControl))
                .border(1.dp, colors.control, RoundedCornerShape(Space.radiusControl))
                .padding(12.dp)
                .semantics { contentDescription = "Type the phrase" },
        )
    }
    Row(Modifier.fillMaxWidth()) {
        Txt("$wordsCorrect of $words words", Type.small, colors.muted, Modifier.weight(1f))
        if (pasted) Txt("Pasting is off. Type it instead", Type.small, colors.negative)
    }
    Action("Continue", ButtonKind.SECONDARY, enabled = matches) {
        flow.submitPhrase()
        changed()
    }
}

@Composable
private fun LengthStep(choose: (Int) -> Unit) {
    Txt("For how long?", Type.section)
    Txt("It blocks again on its own when the time is up.", Type.small, Theme.colors.muted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InterventionFlow.LENGTHS.forEach { minutes ->
            Action("$minutes min", ButtonKind.SECONDARY, modifier = Modifier.weight(1f)) { choose(minutes) }
        }
    }
}

private fun seconds(duration: Duration): String =
    if (duration.seconds < 60) "${duration.seconds} second" else "${duration.toMinutes()} minute"

private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit

    override fun hide() = Unit
}

private const val TICK_MS = 200L
private const val BREATH_MS = 4000
