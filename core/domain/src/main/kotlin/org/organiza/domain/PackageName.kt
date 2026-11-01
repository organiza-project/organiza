package org.organiza.domain

@JvmInline
public value class PackageName private constructor(public val value: String) {
    public companion object {
        private val FORMAT = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")

        public fun parse(raw: String): Parsed<PackageName> = when {
            raw.isBlank() -> Parsed.Invalid(InvalidReason.BLANK)
            !FORMAT.matches(raw) -> Parsed.Invalid(InvalidReason.MALFORMED)
            else -> Parsed.Valid(PackageName(raw))
        }
    }
}
