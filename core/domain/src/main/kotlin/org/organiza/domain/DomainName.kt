package org.organiza.domain

/** A normalised hostname: lowercase, no trailing dot, at least two labels, not an IP. */
@JvmInline
public value class DomainName private constructor(public val value: String) {
    /** True when [other] is this domain or one of its subdomains. */
    public fun covers(other: DomainName): Boolean =
        other.value == value || other.value.endsWith(".$value")

    public companion object {
        private const val MAX_NAME_LENGTH = 253
        private const val MAX_LABEL_LENGTH = 63
        private val LABEL = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?")

        public fun parse(raw: String): Parsed<DomainName> {
            if (raw.isBlank()) return Parsed.Invalid(InvalidReason.BLANK)
            val name = raw.trim().lowercase().removeSuffix(".")
            val labels = name.split('.')
            return when {
                name.length > MAX_NAME_LENGTH -> Parsed.Invalid(InvalidReason.TOO_LONG)
                labels.any { it.length > MAX_LABEL_LENGTH } -> Parsed.Invalid(InvalidReason.TOO_LONG)
                labels.size < 2 -> Parsed.Invalid(InvalidReason.MALFORMED)
                !labels.all(LABEL::matches) -> Parsed.Invalid(InvalidReason.MALFORMED)
                labels.last().all(Char::isDigit) -> Parsed.Invalid(InvalidReason.MALFORMED)
                else -> Parsed.Valid(DomainName(name))
            }
        }
    }
}
