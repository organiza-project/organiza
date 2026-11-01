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

/** Records every request and repeats the last configured reply or failure. */
public class FakeHttpTransport : HttpTransport {
    public data class Request(val url: String, val headers: Map<String, String>, val body: String)

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

    override fun post(url: String, headers: Map<String, String>, body: String): HttpReply {
        recorded += Request(url, headers, body)
        failure?.let { throw it }
        return checkNotNull(reply)
    }
}
