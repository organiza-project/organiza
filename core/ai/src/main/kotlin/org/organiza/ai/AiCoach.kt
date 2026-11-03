package org.organiza.ai

import java.time.DayOfWeek
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.organiza.domain.OverrideReason

public data class WeeklyReview(val summary: String, val suggestions: List<String>)

public data class SuggestedBlock(val day: DayOfWeek, val startMinute: Int, val minutes: Int, val label: String)

public data class ScheduleSuggestion(val blocks: List<SuggestedBlock>, val note: String)

public data class CopilotAdvice(val focusBlocks: List<SuggestedBlock>, val warnings: List<String>, val habitTips: List<String>)

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

    /** A reflection on an override. It never decides anything: the caller continues either way (ADR-0017). */
    public fun reflect(minutesLeft: Long, overridesToday: Int, reason: OverrideReason, note: String): AiResult<String> =
        ask(Prompts.overrideReflection(ReflectionFacts(minutesLeft, overridesToday, reason, sanitizer.clean(note)))) {
            it["reflection"].text()
        }

    public fun copilot(facts: CopilotFacts): AiResult<CopilotAdvice> = ask(Prompts.copilot(facts, sanitizer), ::parseAdvice)

    /** Weekly blocks from a timetable's text. Nothing is scheduled until the person adds them. */
    public fun extractSchedule(text: String): AiResult<List<SuggestedBlock>> =
        ask(Prompts.scheduleExtraction(sanitizer.clean(text.take(MAX_SCHEDULE_TEXT)))) { reply ->
            (reply["blocks"] as? JsonArray)?.take(MAX_ITEMS)?.map { element ->
                parseBlock(element as? JsonObject ?: return@ask null) ?: return@ask null
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

    private fun parseAdvice(reply: JsonObject): CopilotAdvice? {
        val blocks = (reply["focusBlocks"] as? JsonArray)?.take(MAX_ADVICE_BLOCKS)?.map { element ->
            parseBlock(element as? JsonObject ?: return null) ?: return null
        } ?: return null
        return CopilotAdvice(blocks, texts(reply["warnings"]) ?: return null, texts(reply["habitTips"]) ?: return null)
    }

    private fun texts(element: JsonElement?): List<String>? =
        (element as? JsonArray)?.take(MAX_ADVICE_TEXTS)?.map { it.text() ?: return null }

    private fun JsonElement?.text(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.take(MAX_REPLY_TEXT)

    public companion object {
        public const val MAX_REPLY_TEXT: Int = 1000
        private const val MAX_ITEMS = 50
        private const val MAX_SCHEDULE_TEXT = 4000
        private const val MAX_ADVICE_BLOCKS = 5
        private const val MAX_ADVICE_TEXTS = 3
        private const val MAX_BLOCK_MINUTES = 720
        private val TIME = Regex("""([01]\d|2[0-3]):([0-5]\d)""")
    }
}
