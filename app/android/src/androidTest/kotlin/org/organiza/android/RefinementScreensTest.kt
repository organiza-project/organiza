package org.organiza.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.organiza.ai.ChatTurn
import org.organiza.feature.ai.CopilotScreen
import org.organiza.feature.focus.FocusCard
import org.organiza.feature.metrics.UsageDashboard
import org.organiza.feature.sync.AccountPanel
import org.organiza.ui.AccountView
import org.organiza.ui.ChatView
import org.organiza.ui.HubApp
import org.organiza.ui.HubView
import org.organiza.ui.OrganizaTheme
import org.organiza.ui.UiSnapshot
import org.organiza.enforcement.SessionPolicy
import org.organiza.presentation.FocusModel
import org.organiza.sync.BackendKind

/** The usage dashboard, focus tiers, copilot chat and account deletion (ADR-0020). */
@RunWith(AndroidJUnit4::class)
class RefinementScreensTest {
    @get:Rule val compose = createComposeRule()
    private val actions = RecordingActions()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        OrganizaTheme { Column(Modifier.verticalScroll(rememberScrollState())) { content() } }
    }

    @Test
    fun appsAreRankedByUseAndATapSetsALimitAndBlocksReels() {
        val hub = HubView(
            apps = listOf(
                HubApp("org.example.chess", "Chess", onList = false, usedMinutes = 12, limitMinutes = null, locked = false, category = "Games"),
                HubApp("com.instagram.android", "Instagram", onList = false, usedMinutes = 95, limitMinutes = 120, locked = false, category = "Social"),
                HubApp("org.example.maps", "Maps", onList = false, usedMinutes = 0, limitMinutes = null, locked = false),
            ),
            usageAllowed = true,
            screenTime = "1 h 47 m",
        )
        show { UsageDashboard(UiSnapshot(hub = hub), actions) }
        compose.onNodeWithText("25 m left of 2 h 00 m").assertExists()
        compose.onNodeWithText("Maps").assertDoesNotExist()
        compose.onNodeWithText("Instagram").performClick()
        compose.onNodeWithText("Block Reels").performClick()
        // A typed or tapped limit is saved when the sheet closes, never on each digit.
        compose.onNodeWithText("1 h 30").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("setFeed INSTAGRAM_REELS true", "setLimit com.instagram.android 90"), actions.calls)
    }

    @Test
    fun aMonitorOnlySessionStartsWithoutStrictOrFeeds() {
        val focus = FocusModel.build(Instant.parse("2026-11-07T10:00:00Z"), ZoneId.of("UTC"), SessionPolicy.IDLE, emptyList(), emptySet(), 0)
        show { FocusCard(UiSnapshot(focus = focus), actions) {} }
        compose.onNodeWithText("Start a focus session").performClick()
        compose.onNodeWithText("Total lockdown").assertExists()
        compose.onNodeWithText("Monitor only").performClick()
        compose.onNodeWithText("Strict").assertDoesNotExist()
        compose.onNodeWithContentDescription("Session length").performTextReplacement("25")
        compose.onNodeWithText("Start 25 minutes").performScrollTo().performClick()
        assertEquals(listOf("startFocus 25 MONITOR"), actions.calls)
    }

    @Test
    fun theChatSendsTypedTextAndConfirmsAProposedChange() {
        val chat = ChatView(listOf(ChatTurn(true, "Log 30 minutes of reading"), ChatTurn(false, "I can do that.")))
        show { CopilotScreen(UiSnapshot(chat = chat, pendingCommand = "Heard: Log 30 minutes of reading"), actions) {} }
        compose.onNodeWithText("I can do that.").assertExists()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("How do I focus?")
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals(listOf("confirmCommand", "sendChat How do I focus?"), actions.calls)
    }

    @Test
    fun aSignedInAccountCanBeDeleted() {
        val account = AccountView(BackendKind.HOSTED, "https://abc.supabase.co", signedInAs = "sam@example.org", hostedAvailable = true)
        show { AccountPanel(account, actions) }
        compose.onNodeWithText("sam@example.org").assertExists()
        compose.onNodeWithText("Delete account & cloud data").performScrollTo().performClick()
        assertEquals(listOf("deleteAccount"), actions.calls)
    }
}
