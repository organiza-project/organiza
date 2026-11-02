package org.organiza.testing

import java.io.IOException
import org.organiza.ai.ApiKey
import org.organiza.ai.ApiKeyStore
import org.organiza.ai.HttpReply
import org.organiza.ai.HttpTransport

public class FakeApiKeyStore : ApiKeyStore {
    private var stored: ApiKey? = null

    override fun save(key: ApiKey) {
        stored = key
    }

    override fun load(): ApiKey? = stored

    override fun clear() {
        stored = null
    }
}

/** Records every request. Queued replies go first, then the last configured reply or failure repeats. */
public class FakeHttpTransport : HttpTransport {
    public data class Request(val url: String, val headers: Map<String, String>, val body: String)

    private val queued = ArrayDeque<HttpReply>()
    private var reply: HttpReply? = null
    private var failure: IOException? = IOException("no response configured")
    private val recorded = mutableListOf<Request>()

    public val requests: List<Request> get() = recorded.toList()

    public fun respond(status: Int, body: String) {
        reply = HttpReply(status, body)
        failure = null
    }

    public fun fail(error: IOException) {
        failure = error
    }

    /** Answers the next request with this, once, before the repeating reply. */
    public fun queue(status: Int, body: String) {
        queued.addLast(HttpReply(status, body))
    }

    override fun post(url: String, headers: Map<String, String>, body: ByteArray): HttpReply {
        recorded += Request(url, headers, String(body, Charsets.ISO_8859_1))
        queued.removeFirstOrNull()?.let { return it }
        failure?.let { throw it }
        return checkNotNull(reply)
    }
}
