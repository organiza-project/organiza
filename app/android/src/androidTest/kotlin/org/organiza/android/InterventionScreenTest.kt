package org.organiza.android

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.organiza.android.feature.blocker.InterventionHost
import org.organiza.android.feature.blocker.InterventionScreen
import org.organiza.android.ui.OrganizaTheme
import org.organiza.domain.OverrideReason
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.BlockTarget
import org.organiza.domain.port.Clock
import org.organiza.enforcement.InterventionFlow
import org.organiza.enforcement.InterventionOutcome
import org.organiza.enforcement.Phrase

@RunWith(AndroidJUnit4::class)
class InterventionScreenTest {
    @get:Rule val compose = createComposeRule()

    private var now = Instant.parse("2026-11-02T10:00:00Z")
    private val outcomes = mutableListOf<InterventionOutcome>()
    private val instagram = (PackageName.parse("com.instagram.android") as Parsed.Valid).value
    private val phrase = Phrase("t1", "I choose where my attention goes now.")

    private val host = object : InterventionHost {
        override val clock = Clock { now }
        override fun targetLabel(target: BlockTarget) = "Instagram"
        override fun sessionLine(strict: Boolean) = "Focus session, 20 min left"
        override fun reflect(flow: InterventionFlow, done: (String?) -> Unit) = done("Could it wait twenty minutes?")
    }

    private fun show(target: BlockTarget = BlockTarget.App(instagram), strict: Boolean = false, reflects: Boolean = false) {
        val flow = InterventionFlow(target, strict, 0, phrase, reflects, host.clock)
        compose.setContent { OrganizaTheme { InterventionScreen(flow, host) { outcomes += it } } }
    }

    private fun passThePause(seconds: Long) {
        compose.onNodeWithText("Open it anyway").performClick()
        compose.onNodeWithText("Breathe in as the circle grows, out as it shrinks.").assertExists()
        now = now.plusSeconds(seconds)
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("What do you need it for?") }
    }

    private fun typeWordByWord(text: String) {
        val field = compose.onNode(hasSetTextAction())
        text.split(' ').forEachIndexed { index, word -> field.performTextInput(if (index == 0) word else " $word") }
    }

    @Test
    fun stayingFocusedIsTheFirstChoice() {
        show()
        compose.onNodeWithText("Instagram is blocked").assertExists()
        compose.onNodeWithText("Stay focused").performClick()
        assertEquals(listOf(InterventionOutcome.Stayed), outcomes)
    }

    @Test
    fun aFullOverrideReturnsTheChoice() {
        show()
        passThePause(10)
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        typeWordByWord("I choose where my attention goes now")
        compose.onNodeWithText("7/7 words typed").assertExists()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("5 min").performClick()
        assertEquals(listOf(InterventionOutcome.Overrode(5, OverrideReason.WORK, "", Duration.ofSeconds(10), reflected = false)), outcomes)
    }

    @Test
    fun aPastedPhraseIsRefused() {
        show()
        passThePause(10)
        compose.onNodeWithText("Boredom").performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput(phrase.text)
        compose.onNodeWithText("Pasting is off. Type it instead").assertExists()
        compose.onNodeWithText("0/7 words typed").assertExists()
    }

    @Test
    fun strictSessionsShowTheReflectionFirst() {
        show(strict = true, reflects = true)
        passThePause(30)
        compose.onNodeWithText("A message").performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Could it wait twenty minutes?").assertExists()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Type this passage, word by word").assertExists()
    }

    @Test
    fun theSettingsGuardOnlyOffersOk() {
        show(target = BlockTarget.Guard((PackageName.parse("com.android.settings") as Parsed.Valid).value), strict = true)
        compose.onNodeWithText("Open it anyway").assertDoesNotExist()
        compose.onNodeWithText("OK").performClick()
        assertTrue(outcomes.single() is InterventionOutcome.Stayed)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextExists(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aDailyLimitSaysWhatIsLocked() {
        show(target = BlockTarget.Limit(instagram))
        compose.onNodeWithText("Limit reached").assertExists()
        compose.onNodeWithText("(Instagram locked for today)").assertExists()
        compose.onNodeWithText("Open it anyway").assertExists()
    }
}
