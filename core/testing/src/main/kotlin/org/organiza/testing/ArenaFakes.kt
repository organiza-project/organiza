package org.organiza.testing

import java.io.IOException
import org.organiza.arena.ArenaRelay
import org.organiza.arena.RelayMessage
import org.organiza.arena.SecretStore

public class FakeSecretStore : SecretStore {
    private val stored = mutableMapOf<String, ByteArray>()

    public val names: Set<String> get() = stored.keys

    override fun put(name: String, value: ByteArray) {
        stored[name] = value
    }

    override fun get(name: String): ByteArray? = stored[name]

    public fun clear() {
        stored.clear()
    }
}

/** A blind mailbox in memory, which records what a real relay would be able to see. */
public class FakeArenaRelay : ArenaRelay {
    private val mailboxes = mutableMapOf<String, MutableList<ByteArray>>()

    public var offline: Boolean = false

    public val stored: Map<String, List<ByteArray>> get() = mailboxes

    override fun append(mailbox: String, data: ByteArray) {
        if (offline) throw IOException("relay is offline")
        mailboxes.getOrPut(mailbox) { mutableListOf() } += data
    }

    override fun fetch(mailbox: String, after: Long): List<RelayMessage> {
        if (offline) throw IOException("relay is offline")
        return mailboxes[mailbox].orEmpty()
            .mapIndexed { index, data -> RelayMessage(index + 1L, data) }
            .filter { it.cursor > after }
    }
}
