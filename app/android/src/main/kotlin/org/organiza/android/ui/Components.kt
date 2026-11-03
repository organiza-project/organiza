package org.organiza.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun Txt(
    text: String,
    style: TextStyle = Type.body,
    color: Color = Theme.colors.text,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(text, modifier, style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** The screen's title row: a noun, and one line on the data (app-screens §4.3). */
@Composable
fun TitleBlock(title: String, subline: String?) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp)) {
        Txt(title, Type.title, modifier = Modifier.semantics { heading() })
        if (subline != null) Txt(subline, Type.body, Theme.colors.muted, Modifier.padding(top = 4.dp))
    }
}

/** A white card drawn with an ink line, never a shadow (ADR-0018). */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = Theme.colors
    val shape = RoundedCornerShape(Space.radiusCard)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(Space.stroke, colors.border, shape)
            .padding(Space.pad),
        content = content,
    )
}

@Composable
fun PanelHeader(title: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, Type.section, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke(this)
    }
}

enum class ButtonKind { PRIMARY, SECONDARY, TEXT, DANGER }

@Composable
fun Action(
    label: String,
    kind: ButtonKind = ButtonKind.SECONDARY,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Primary is filled ink; secondary is an ink outline, as the wireframe draws its buttons.
    val (fill, ink) = when (kind) {
        ButtonKind.PRIMARY -> colors.primary to colors.onPrimary
        ButtonKind.SECONDARY -> colors.surface to colors.text
        ButtonKind.TEXT -> Color.Transparent to colors.text
        ButtonKind.DANGER -> Color.Transparent to colors.negative
    }
    val shape = RoundedCornerShape(Space.radiusControl)
    Box(
        modifier
            .defaultMinSize(minHeight = Space.target)
            .alpha(if (!enabled) 0.55f else if (pressed) 0.8f else 1f)
            .background(fill, shape)
            .then(
                when (kind) {
                    ButtonKind.DANGER -> Modifier.border(Space.stroke, colors.negative, shape)
                    ButtonKind.SECONDARY -> Modifier.border(Space.stroke, colors.border, shape)
                    else -> Modifier
                },
            )
            .clickable(interaction, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (kind == ButtonKind.TEXT) 4.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, Type.bodyStrong, ink, maxLines = 1)
    }
}

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    number: Boolean = false,
) {
    val colors = Theme.colors
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Txt(label, Type.bodyStrong, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value,
            onValueChange,
            singleLine = true,
            textStyle = Type.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.text),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Space.target)
                .background(colors.surface, RoundedCornerShape(Space.radiusControl))
                .border(Space.stroke, colors.control, RoundedCornerShape(Space.radiusControl))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

/** A switch row: title, description, and the toggle on the right (app-screens §8.2). */
@Composable
fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Theme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.target)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Txt(title, Type.bodyStrong)
            Txt(description, Type.small, colors.muted)
        }
        val track = if (checked) colors.primary else colors.control
        Box(
            Modifier.padding(start = 8.dp).background(track, RoundedCornerShape(999.dp)).padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(start = if (checked) 14.dp else 0.dp, end = if (checked) 0.dp else 14.dp)) {
                Box(Modifier.background(colors.surface, RoundedCornerShape(999.dp)).padding(7.dp))
            }
        }
    }
}

@Composable
fun Pill(text: String, background: Color, ink: Color) {
    Box(Modifier.background(background, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Txt(text, Type.small, ink, maxLines = 1)
    }
}

@Composable
fun Rule() {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Theme.colors.rule).heightIn(min = 1.dp, max = 1.dp))
}

@Composable
fun KeyValue(label: String, value: String, valueColor: Color = Theme.colors.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Txt(label, Type.body, Theme.colors.muted, Modifier.weight(1f))
        Txt(value, Type.body, valueColor)
    }
}

/** One line of transient feedback, shown where the person is looking. */
@Composable
fun MessagePanel(text: String?) {
    if (text == null) return
    Panel { Txt(text, Type.body) }
}

/** A choice in a short list: one row per option, with the chosen one marked. */
@Composable
fun ChoiceRow(title: String, detail: String?, chosen: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = Theme.colors
    val shape = RoundedCornerShape(Space.radiusControl)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.target)
            .alpha(if (enabled) 1f else 0.55f)
            .border(if (chosen) 2.5.dp else Space.stroke, if (chosen) colors.text else colors.rule, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { stateDescription = if (chosen) "Selected" else "Not selected" }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, Type.bodyStrong)
            if (detail != null) Txt(detail, Type.small, colors.muted)
        }
    }
}
