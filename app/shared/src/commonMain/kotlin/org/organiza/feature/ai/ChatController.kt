package org.organiza.feature.ai

import java.time.ZoneId
import org.organiza.ai.AiFailure
import org.organiza.ai.AiResult
import org.organiza.ai.ChatTurn
import org.organiza.ai.GroqClient
import org.organiza.app.OrganizaCore
import org.organiza.ui.ChatView
import org.organiza.voice.CommandParser

/**
 * The copilot chat (ADR-0020). A message the local grammar understands becomes a command waiting
 * for Confirm, with no AI call. Anything else goes to Groq with usage by category, and any change
 * it suggests is parsed by the same grammar, so the model can never act on its own (ADR-0010).
 * Turns live in memory only.
 */
class ChatController(private val app: OrganizaCore) {
    @Volatile private var turns: List<ChatTurn> = emptyList()
    @Volatile private var busy = false

    fun view() = ChatView(turns, busy)

    fun clear() {
        turns = emptyList()
        app.requestUiRefresh()
    }

    fun send(raw: String) {
        val text = raw.trim().take(MAX_MESSAGE)
        if (text.isEmpty() || busy) return
        val history = turns
        turns = history + ChatTurn(fromUser = true, text = text)
        busy = true
        app.requestUiRefresh()
        app.onStores { stores ->
            val now = app.clock.now().atZone(ZoneId.systemDefault())
            val local = CommandParser.parse(text, now)
            if (local != null) {
                app.propose(local)
                return@onStores answer("I can do that. Check it below and tap Confirm.")
            }
            val facts = app.usageFacts(stores)
            app.onAi {
                val result = try {
                    app.coach(GroqClient.CHAT_MODEL).chat(text, history, facts)
                } catch (failure: RuntimeException) {
                    answer("The copilot could not answer: ${failure.javaClass.simpleName}")
                    return@onAi
                }
                when (result) {
                    is AiResult.Ok -> {
                        // A placeholder means the sanitizer removed what the command would act on.
                        val command = result.value.command?.takeUnless { '[' in it }?.let { CommandParser.parse(it, now) }
                        command?.let(app::propose)
                        answer(result.value.reply + if (command != null) "\n\nCheck the change below and tap Confirm." else "")
                    }
                    is AiResult.Failed -> answer(
                        if (result.failure == AiFailure.NO_KEY) {
                            "Add your Groq key in Profile to ask questions. Commands like \"log 30 minutes of reading\" work without one."
                        } else {
                            "The copilot could not answer: ${result.failure}"
                        },
                    )
                }
            }
        }
    }

    private fun answer(text: String) {
        turns = turns + ChatTurn(fromUser = false, text = text.take(MAX_REPLY))
        busy = false
        app.requestUiRefresh()
    }

    private companion object {
        const val MAX_MESSAGE = 500
        const val MAX_REPLY = 2000
    }
}
