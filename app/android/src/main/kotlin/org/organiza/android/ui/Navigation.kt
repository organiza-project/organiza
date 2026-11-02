package org.organiza.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val colors = Theme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.ground).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("Focus", "Habits", "Arena", "Vault").forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Space.target)
                    .background(if (active) colors.raised else colors.ground, RoundedCornerShape(Space.radiusControl))
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Txt(label, if (active) Type.bodyStrong else Type.body, if (active) colors.text else colors.muted)
            }
        }
    }
}
