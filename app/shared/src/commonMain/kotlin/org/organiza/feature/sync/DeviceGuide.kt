package org.organiza.feature.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.organiza.presentation.DeviceSetup
import org.organiza.ui.*

/**
 * How to see every device's screen time in one place: three numbered steps, ticked as they are done, with
 * the next one in the data hue (ADR-0028, ADR-0037). The controls for each step sit in the panels below it.
 */
@Composable
fun DeviceGuide(account: AccountView) {
    val colors = Theme.colors
    val steps = DeviceSetup.steps(account.kind != null, account.signedInAs != null, account.devicesLinked)
    Panel {
        PanelHeader("Your devices") { if (steps.all { it.done }) Txt("Linked", Type.small, colors.positive) }
        Txt("Check one device for the screen time of all of them. Each sends only sealed reports your own devices can open.", Type.small, colors.muted)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            steps.forEachIndexed { index, step ->
                Row(
                    Modifier.fillMaxWidth().semantics { stateDescription = if (step.done) "Done" else if (step.current) "Next" else "Later" },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val ink = if (step.current) colors.data else colors.text
                    Box(
                        Modifier
                            .size(28.dp)
                            .then(if (step.done) Modifier.background(colors.text, CircleShape) else Modifier.border(Space.stroke, ink, CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (step.done) Icon(Glyph.CHECK, colors.ground, 16.dp) else Txt("${index + 1}", Type.small, ink)
                    }
                    Column(Modifier.weight(1f)) {
                        Txt(step.title, Type.bodyStrong, if (step.done) colors.muted else ink)
                        if (!step.done) Txt(step.detail, Type.small, colors.muted)
                    }
                }
            }
        }
    }
}
