package org.organiza.android

import android.content.Context
import java.io.File
import org.organiza.arena.SecretStore

/** Holds challenge secrets, sealed by the Keystore and kept out of backups (ADR-0011). */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val directory = File(context.noBackupFilesDir, "arena")
    private val sealer = KeystoreSealer("organiza_arena_wrap")

    override fun put(name: String, value: ByteArray) {
        directory.mkdirs()
        writeAtomically(File(directory, name), sealer.seal(value))
    }

    override fun get(name: String): ByteArray? {
        val file = File(directory, name)
        if (!file.exists()) return null
        return try {
            sealer.open(file.readBytes())
        } catch (_: SealedDataException) {
            null
        }
    }
}
