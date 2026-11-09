package org.organiza.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.organiza.feature.onboarding.OnboardingScreen
import org.organiza.ui.AccountView
import org.organiza.ui.OrganizaTheme
import org.organiza.ui.UiSnapshot
import org.organiza.presentation.Permission
import org.organiza.sync.BackendKind

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()
    private val actions = RecordingActions()

    // Scrolls like the app's root column does.
    private fun show(ui: UiSnapshot) = compose.setContent {
        OrganizaTheme { Column(Modifier.verticalScroll(rememberScrollState())) { OnboardingScreen(ui, actions) } }
    }

    @Test
    fun walksEveryStepAndFinishes() {
        show(UiSnapshot(onboarding = true))
        compose.onNodeWithText("Mindful friction").assertExists()
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("App blocking").assertExists()
        compose.onNodeWithText("Continue without blocking").performScrollTo().performClick()
        compose.onNodeWithText("Where your data lives").assertExists()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Groq key").assertExists()
        compose.onNodeWithText("Skip for now").performScrollTo().performClick()
        assertEquals(listOf("finishOnboarding"), actions.calls)
    }

    @Test
    fun eachPermissionCanBeAskedForAndExplained() {
        show(UiSnapshot(onboarding = true, permissions = setOf(Permission.ACCESSIBILITY)))
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("Continue").assertExists()
        compose.onNodeWithText("With app blocking").assertDoesNotExist()
        compose.onNodeWithText("Keep running · optional").assertExists()
        compose.onAllNodesWithText("Allow")[0].performScrollTo().performClick()
        assertTrue(actions.calls.single().startsWith("grant "))
    }

    @Test
    fun appBlockingShowsTheRestrictedSettingsStepsAndOpensAppInfo() {
        show(UiSnapshot(onboarding = true))
        compose.onNodeWithText("Start").performClick()
        // App blocking is off, so its steps are open without a tap; the other permissions keep theirs folded.
        compose.onNodeWithText("Allow restricted settings", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Open App info").performScrollTo().performClick()
        assertEquals(listOf("openAppInfo"), actions.calls)
    }

    @Test
    fun theHostedBackendIsOfferedOnlyWhenTheBuildHasOne() {
        show(UiSnapshot(onboarding = true, account = AccountView(hostedAvailable = false)))
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("Continue without blocking").performScrollTo().performClick()
        compose.onNodeWithText("Not available in this build").assertExists()
        compose.onNodeWithText("Use this").assertIsNotEnabled()
    }

    @Test
    fun aSelfHostedProjectIsEnteredAndSaved() {
        show(UiSnapshot(onboarding = true))
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("Continue without blocking").performScrollTo().performClick()
        compose.onNodeWithText("My own Supabase").performClick()
        compose.onNodeWithText("Project URL (https)").performScrollTo()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("https://example.supabase.co")
        compose.onNodeWithText("Use this").performScrollTo().performClick()
        assertEquals(listOf("chooseBackend ${BackendKind.SELF_HOSTED} https://example.supabase.co"), actions.calls)
    }
}
