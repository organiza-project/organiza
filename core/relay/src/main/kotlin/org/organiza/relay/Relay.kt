package org.organiza.relay

import java.io.IOException

public class RelayMessage(public val cursor: Long, public val data: ByteArray)

/** A blind mailbox. It holds opaque bytes and has no keys (ADR-0028). */
public interface Relay {
    /** Throws [IOException] when the relay cannot be reached. */
    public fun append(mailbox: String, data: ByteArray)

    /** Throws [IOException] when the relay cannot be reached. */
    public fun fetch(mailbox: String, after: Long): List<RelayMessage>
}

/** Key storage for the database key, the AI key, the device link and the sync session. Android: sealed by a Keystore key. */
public interface SecretStore {
    public fun put(name: String, value: ByteArray)

    public fun get(name: String): ByteArray?

    public fun remove(name: String)
}
