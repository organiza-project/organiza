package org.organiza.enforcement

import java.time.Duration
import java.time.Instant
import org.organiza.domain.OverrideReason
import org.organiza.domain.port.BlockTarget
import org.organiza.domain.port.Clock

public sealed interface InterventionStep {
    /** Stay focused, or start the override path. */
    public data object Choice : InterventionStep

    public data class Pause(val until: Instant) : InterventionStep

    public data object Reason : InterventionStep

    /** [text] is null while the AI reflection is on its way. */
    public data class Reflect(val text: String?) : InterventionStep

    public data object Type : InterventionStep

    public data object Length : InterventionStep

    public data object Done : InterventionStep
}

public sealed interface InterventionOutcome {
    public data object Stayed : InterventionOutcome

    public data class Overrode(
        val minutes: Int,
        val reason: OverrideReason,
        val note: String,
        val pause: Duration,
        val reflected: Boolean,
    ) : InterventionOutcome
}

/**
 * The steps between a blocked app and a pass (ADR-0016). Staying focused is possible at every
 * step, and nothing is skipped. Not thread-safe: drive it from the UI thread.
 */
public class InterventionFlow(
    public val target: BlockTarget,
    public val strict: Boolean,
    overridesToday: Int,
    public val phrase: Phrase,
    /** Whether an AI reflection comes before the phrase: strict sessions with a key only. */
    public val reflects: Boolean,
    private val clock: Clock,
) {
    public val pause: Duration = Friction.pause(strict, overridesToday)
    public val canOverride: Boolean = target !is BlockTarget.Guard

    public var step: InterventionStep = InterventionStep.Choice
        private set

    public var typed: String = ""
        private set

    private var reason: OverrideReason? = null
    private var note = ""
    private var reflected = false

    public val wordsCorrect: Int get() = PhraseMatch.wordsCorrect(phrase.text, typed)

    /** The reason and note given so far, for the reflection request. */
    public val chosenReason: OverrideReason? get() = reason
    public val chosenNote: String get() = note

    public fun stayFocused(): InterventionOutcome? {
        if (step == InterventionStep.Done) return null
        step = InterventionStep.Done
        return InterventionOutcome.Stayed
    }

    public fun beginOverride() {
        if (step == InterventionStep.Choice && canOverride) step = InterventionStep.Pause(clock.now().plus(pause))
    }

    /** Moves past the pause once it has run. Call it from a timer. */
    public fun tick() {
        val current = step
        if (current is InterventionStep.Pause && !clock.now().isBefore(current.until)) step = InterventionStep.Reason
    }

    public fun chooseReason(chosen: OverrideReason, note: String): Boolean {
        if (step != InterventionStep.Reason) return false
        reason = chosen
        this.note = note.trim().take(MAX_NOTE)
        step = if (strict && reflects) InterventionStep.Reflect(null) else InterventionStep.Type
        return true
    }

    /** Shows the model's reflection, or the local one when it failed. */
    public fun showReflection(text: String?) {
        if (step !is InterventionStep.Reflect) return
        reflected = text != null
        step = InterventionStep.Reflect(text?.take(MAX_REFLECTION) ?: LOCAL_REFLECTION)
    }

    public fun continueAfterReflection(): Boolean {
        val current = step
        if (current !is InterventionStep.Reflect || current.text == null) return false
        step = InterventionStep.Type
        return true
    }

    /** Takes the field's new text. A paste is refused and clears the field. */
    public fun type(text: String): Boolean {
        if (step != InterventionStep.Type) return false
        if (!TypingGuard.accepts(typed, text)) {
            typed = ""
            return false
        }
        typed = text
        return true
    }

    public fun submitPhrase(): Boolean {
        if (step != InterventionStep.Type || !PhraseMatch.matches(phrase.text, typed)) return false
        step = InterventionStep.Length
        return true
    }

    public fun choose(minutes: Int): InterventionOutcome? {
        val chosen = reason
        if (step != InterventionStep.Length || minutes !in LENGTHS || chosen == null) return null
        step = InterventionStep.Done
        return InterventionOutcome.Overrode(minutes, chosen, note, pause, reflected)
    }

    public companion object {
        public val LENGTHS: List<Int> = listOf(1, 5, 15)
        public const val MAX_NOTE: Int = 200
        private const val MAX_REFLECTION = 600
        public const val LOCAL_REFLECTION: String =
            "Take one more breath. Will opening this help with what you set out to do, or is it a way to avoid it?"
    }
}
