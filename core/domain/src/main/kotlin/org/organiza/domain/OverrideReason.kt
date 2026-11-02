package org.organiza.domain

/** Why someone opened a blocked app anyway. Stored by name, so entries are only ever added (ADR-0016). */
public enum class OverrideReason { WORK, COMMUNICATION, ERRAND, BOREDOM, HABIT, OTHER }
