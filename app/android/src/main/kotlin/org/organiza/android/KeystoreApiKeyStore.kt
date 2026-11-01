package org.organiza.android

import android.content.Context
import java.io.File
import org.organiza.ai.ApiKey
import org.organiza.ai.ApiKeyStore
import org.organiza.domain.Parsed

/** Holds the user's Groq key, sealed by the Keystore and kept out of backups (ADR-0009). */
class KeystoreApiKeyStore(context: Context) : ApiKeyStore {
    private val file = File(context.noBackupFilesDir, "groq.key")
    private val sealer = KeystoreSealer("organiza_api_key_wrap")

    override fun save(key: ApiKey) {
        writeAtomically(file, sealer.seal(key.value.toByteArray()))
    }

    /** A damaged file reads as no key: AI features stop, and saving a key again fixes it. */
    override fun load(): ApiKey? {
        if (!file.exists()) return null
        return try {
            (ApiKey.parse(sealer.open(file.readBytes()).decodeToString()) as? Parsed.Valid)?.value
        } catch (_: SealedDataException) {
            null
        }
    }

    override fun clear() {
        file.delete()
    }
}
