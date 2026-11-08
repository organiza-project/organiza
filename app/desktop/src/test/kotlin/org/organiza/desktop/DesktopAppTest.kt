package org.organiza.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import org.organiza.ui.OrganizaRoot
import org.organiza.ui.OrganizaTheme

@OptIn(ExperimentalTestApi::class)
class DesktopAppTest {
    @Test
    fun `the shared screens render and switch tabs on the desktop`() = runComposeUiTest {
        val app = DesktopApp()
        setContent { OrganizaTheme { OrganizaRoot(app.ui.value, app) } }
        onNodeWithText("Voice").assertDoesNotExist()
        onNodeWithContentDescription("Profile").performClick()
        onNodeWithText("Voice").assertExists()
    }

    @Test
    fun `an action Linux cannot do yet says so on screen`() = runComposeUiTest {
        val app = DesktopApp()
        setContent { OrganizaTheme { OrganizaRoot(app.ui.value, app) } }
        onNodeWithText("Not on Linux yet", substring = true).assertDoesNotExist()
        app.syncArena()
        onNodeWithText("Not on Linux yet", substring = true).assertExists()
    }
}
