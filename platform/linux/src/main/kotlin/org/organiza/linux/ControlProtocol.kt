package org.organiza.linux

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

sealed interface Request {
    data object Status : Request

    data object Stop : Request

    data class Start(val minutes: Int, val processes: List<String>, val domains: List<String>, val strict: Boolean) : Request

    data class Import(val ics: String) : Request

    /** Saves the rule that calendar events tagged #[tag] will start. */
    data class Rule(val tag: String, val processes: List<String>, val domains: List<String>, val strict: Boolean) : Request
}

data class Reply(val ok: Boolean, val message: String, val status: List<String> = emptyList())

/** One JSON object per line over the control socket. Unknown or malformed input is refused. */
object ControlProtocol {
    fun encode(request: Request): String = when (request) {
        Request.Status -> buildJsonObject { put("command", "status") }
        Request.Stop -> buildJsonObject { put("command", "stop") }
        is Request.Start -> buildJsonObject {
            put("command", "start")
            put("minutes", request.minutes)
            putJsonArray("processes") { request.processes.forEach { add(it) } }
            putJsonArray("domains") { request.domains.forEach { add(it) } }
            put("strict", request.strict)
        }
        is Request.Import -> buildJsonObject {
            put("command", "import")
            put("ics", request.ics)
        }
        is Request.Rule -> buildJsonObject {
            put("command", "rule")
            put("tag", request.tag)
            putJsonArray("processes") { request.processes.forEach { add(it) } }
            putJsonArray("domains") { request.domains.forEach { add(it) } }
            put("strict", request.strict)
        }
    }.toString()

    fun encode(reply: Reply): String = buildJsonObject {
        put("ok", reply.ok)
        put("message", reply.message)
        putJsonArray("status") { reply.status.forEach { add(it) } }
    }.toString()

    fun parseRequest(line: String): Request? = runCatching {
        val json = Json.parseToJsonElement(line) as JsonObject
        when (json.text("command")) {
            "status" -> Request.Status
            "stop" -> Request.Stop
            "start" -> Request.Start(
                (json["minutes"] as JsonPrimitive).takeUnless { it.isString }?.intOrNull ?: return null,
                json.strings("processes") ?: return null,
                json.strings("domains") ?: return null,
                (json["strict"] as JsonPrimitive).booleanOrNull ?: return null,
            )
            "import" -> Request.Import(json.text("ics") ?: return null)
            "rule" -> Request.Rule(
                json.text("tag") ?: return null,
                json.strings("processes") ?: return null,
                json.strings("domains") ?: return null,
                (json["strict"] as JsonPrimitive).booleanOrNull ?: return null,
            )
            else -> null
        }
    }.getOrNull()

    fun parseReply(line: String): Reply? = runCatching {
        val json = Json.parseToJsonElement(line) as JsonObject
        Reply(
            (json["ok"] as JsonPrimitive).booleanOrNull ?: return null,
            json.text("message") ?: return null,
            json.strings("status") ?: return null,
        )
    }.getOrNull()

    private fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.strings(name: String): List<String>? =
        (this[name] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }
}
