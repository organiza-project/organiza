package org.organiza.domain

public enum class InvalidReason { BLANK, MALFORMED, TOO_LONG }

public sealed interface Parsed<out T> {
    public data class Valid<T>(val value: T) : Parsed<T>

    public data class Invalid(val reason: InvalidReason) : Parsed<Nothing>
}
