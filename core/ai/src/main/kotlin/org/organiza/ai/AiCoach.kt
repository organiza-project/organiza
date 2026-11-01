package org.organiza.ai

import java.time.DayOfWeek
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

public data class WeeklyReview(val summary: String, val suggestions: List<String>)

public data class SuggestedBlock(val day: DayOfWeek, val startMinute: Int, val minutes: Int, val label: String)

public data class ScheduleSuggestion(val blocks: List<SuggestedBlock>, val note: String)

/** [granted] is true only for an explicit grant. Every failure is a denial (ADR-0009). */
public data class GatekeeperDecision(val granted: Boolean, val reason: String?, val failure: AiFailure?)

/** Runs each AI feature. Replies are untrusted and must parse into a strict shape. */
public class AiCoach(
    private val keys: ApiKeyStore,
    private val client: GroqClient,
    private val sanitizer: Sanitizer,
) {
    public fun weeklyReview(facts: WeeklyFacts): AiResult<WeeklyReview> =
        ask(Prompts.weeklyRetrospective(facts), ::parseReview)

    public fun suggestSchedule(goal: String, busy: List<BusyBlock>): AiResult<ScheduleSuggestion> =
        ask(Prompts.scheduleOptimization(sanitizer.clean(goal), busy, sanitizer), ::parseSchedule)

    public fun gatekeeper(minutesLeft: Long, request: String): GatekeeperDecision {
        val prompt = Prompts.gatekeeper(GatekeeperFacts(minutesLeft, sanitizer.clean(request)))
        return when (val result = ask(prompt, ::parseDecision)) {
            is AiResult.Ok -> result.value
            is AiResult.Failed -> GatekeeperDecision(granted = false, reason = null, failure = result.failure)
        }
    }

    private fun <T : Any> ask(prompt: Prompt, parse: (JsonObject) -> T?): AiResult<T> {
        val key = keys.load() ?: return AiResult.Failed(AiFailure.NO_KEY)
        return when (val reply = client.complete(key, prompt)) {
            is AiResult.Failed -> reply
            is AiResult.Ok -> {
                val parsed = runCatching { parse(Json.parseToJsonElement(reply.value) as JsonObject) }.getOrNull()
                parsed?.let { AiResult.Ok(it) } ?: AiResult.Failed(AiFailure.MALFORMED)
            }
        }
    }

    private fun parseReview(reply: JsonObject): WeeklyReview? {
        val summary = reply["summary"].text() ?: return null
        val suggestions = (reply["suggestions"] as? JsonArray)?.take(MAX_ITEMS)?.map { it.text() ?: return null }
        return WeeklyReview(summary, suggestions ?: return null)
    }

    private fun parseSchedule(reply: JsonObject): ScheduleSuggestion? {
        val blocks = (reply["blocks"] as? JsonArray)?.take(MAX_ITEMS)?.map { element ->
            parseBlock(element as? JsonObject ?: return null) ?: return null
        } ?: return null
        return ScheduleSuggestion(blocks, reply["note"].text().orEmpty())
    }

    private fun parseBlock(block: JsonObject): SuggestedBlock? {
        val day = DayOfWeek.entries.firstOrNull { it.name == block["day"].text() } ?: return null
        val start = TIME.matchEntire(block["start"].text().orEmpty()) ?: return null
        val minutes = (block["minutes"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 1..MAX_BLOCK_MINUTES } ?: return null
        val label = block["label"].text() ?: return null
        return SuggestedBlock(day, start.groupValues[1].toInt() * 60 + start.groupValues[2].toInt(), minutes, label)
    }

    private fun parseDecision(reply: JsonObject): GatekeeperDecision? {
        val decision = reply["decision"].text() ?: return null
        return GatekeeperDecision(granted = decision == "grant", reason = reply["reason"].text(), failure = null)
    }

    private fun JsonElement?.text(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.take(MAX_REPLY_TEXT)

    public companion object {
        public const val MAX_REPLY_TEXT: Int = 1000
        private const val MAX_ITEMS = 50
        private const val MAX_BLOCK_MINUTES = 720
        private val TIME = Regex("""([01]\d|2[0-3]):([0-5]\d)""")
    }
}
