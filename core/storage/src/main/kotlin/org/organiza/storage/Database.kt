package org.organiza.storage

/** One result row. Columns are zero-based. */
public interface Row {
    public fun string(column: Int): String?

    public fun long(column: Int): Long?
}

/** The SQL engine. Implementations must have foreign keys enabled (ADR-0008). */
public interface Database {
    public fun execute(sql: String, args: List<Any?> = emptyList())

    public fun <T> query(sql: String, args: List<Any?> = emptyList(), map: (Row) -> T): List<T>

    /** Runs [block] atomically: an exception rolls everything back and is rethrown. */
    public fun <T> transaction(block: () -> T): T
}
