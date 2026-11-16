package org.organiza.presentation

/**
 * The lockout (ADR-0032): loosening a limit, a blocker or a session needs this essay typed out, one
 * letter at a time. Original text written for Organiza.
 */
public object Lockout {
    public const val ESSAY: String =
        "I set these limits when my mind was clear, because I knew this moment would come. Right now a small " +
            "voice says that just this once does not matter, that I have earned a break, that the rule was too strict. " +
            "That voice is not wrong about how I feel, but it is wrong about what I want. What I want is a life where " +
            "my attention goes where I choose. If I still want to change this rule after typing every word of this, " +
            "I will change it on purpose, and I will not pretend it was an accident."

    /** The text after one edit: kept when it adds the next right letter or deletes; a paste or a wrong letter is refused. */
    public fun accept(old: String, new: String): String = when {
        new.length < old.length && old.startsWith(new) -> new
        new.length == old.length + 1 && new.startsWith(old) && ESSAY.startsWith(new) -> new
        else -> old
    }

    public fun done(typed: String): Boolean = typed == ESSAY
}
