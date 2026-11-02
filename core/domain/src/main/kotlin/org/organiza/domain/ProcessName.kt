package org.organiza.domain

/** A desktop program to block: an executable's name or a Flatpak app ID, never a path (ADR-0013). */
@JvmInline
public value class ProcessName private constructor(public val value: String) {
    public companion object {
        private const val MAX_LENGTH = 255
        private val FORMAT = Regex("""[A-Za-z0-9_][A-Za-z0-9._+-]*""")

        public fun parse(raw: String): Parsed<ProcessName> = when {
            raw.isBlank() -> Parsed.Invalid(InvalidReason.BLANK)
            raw.length > MAX_LENGTH -> Parsed.Invalid(InvalidReason.TOO_LONG)
            !FORMAT.matches(raw) -> Parsed.Invalid(InvalidReason.MALFORMED)
            else -> Parsed.Valid(ProcessName(raw))
        }
    }
}
