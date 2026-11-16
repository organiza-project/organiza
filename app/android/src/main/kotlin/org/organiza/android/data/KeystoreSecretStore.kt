package org.organiza.android.data

import android.content.Context
import java.io.File
import org.organiza.relay.SecretStore

/**
 * Holds the device link and sync session, sealed by the Keystore and kept out of backups. The folder and
 * key alias keep their Arena-era names so secrets saved by earlier versions are still found.
 */
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

    override fun remove(name: String) {
        File(directory, name).delete()
    }
}
