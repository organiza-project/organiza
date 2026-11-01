package org.organiza.enforcement

import java.util.concurrent.CopyOnWriteArrayList

public data class SessionPolicy(
    val blockList: BlockList,
    val strict: Boolean = false,
    val blockShortForm: Boolean = false,
) {
    public companion object {
        public val IDLE: SessionPolicy = SessionPolicy(BlockList.EMPTY)
    }
}

/** The policy in force, shared by the accessibility and VPN layers across threads. */
public class ActiveSession {
    private val listeners = CopyOnWriteArrayList<(SessionPolicy) -> Unit>()

    @Volatile
    public var current: SessionPolicy = SessionPolicy.IDLE
        private set

    public fun set(policy: SessionPolicy) {
        current = policy
        listeners.forEach { it(policy) }
    }

    /** Calls [listener] with the current policy now and on each change, until closed. */
    public fun subscribe(listener: (SessionPolicy) -> Unit): AutoCloseable {
        listeners += listener
        listener(current)
        return AutoCloseable { listeners -= listener }
    }
}
