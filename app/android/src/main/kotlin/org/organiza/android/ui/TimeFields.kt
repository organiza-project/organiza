package org.organiza.android.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.organiza.presentation.TimeInput

/**
 * A length typed straight on the number pad, with one-tap presets under it (ADR-0021). The
 * length is read back in words, or the range is shown when the number is outside it.
 */
@Composable
fun MinutesField(label: String, minutes: Int, range: IntRange, presets: List<Int>, onChange: (Int) -> Unit) {
    val colors = Theme.colors
    var text by rememberSaveable { mutableStateOf(minutes.toString()) }
    val typed = TimeInput.minutes(text, range)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Txt(label, Type.bodyStrong, modifier = Modifier.padding(bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DigitBox(text, label, 112.dp, typed != null, ImeAction.Done, 4) { raw ->
                text = raw
                TimeInput.minutes(raw, range)?.let(onChange)
            }
            Txt("minutes", Type.section, colors.muted, Modifier.padding(start = 12.dp))
        }
        // Under an hour the box already says it; from an hour up, the words read faster than "135".
        when {
            typed == null -> Txt("Type ${range.first} to ${range.last} minutes", Type.small, colors.negative, Modifier.padding(top = 6.dp))
            typed >= 60 -> Txt(TimeInput.length(typed), Type.small, colors.muted, Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Segmented(presets, typed ?: -1, ::presetLabel) { chosen ->
            text = chosen.toString()
            onChange(chosen)
        }
    }
}

/**
 * A time of day as two boxes on the number pad: two digits in the hour box move on to the
 * minutes by themselves. AM and PM appear when the phone keeps a 12 hour clock.
 */
@Composable
fun ClockField(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    val colors = Theme.colors
    val twelveHour = !DateFormat.is24HourFormat(LocalContext.current)
    val startHour = minuteOfDay / 60
    var hour by rememberSaveable {
        mutableStateOf(if (twelveHour) (if (startHour % 12 == 0) 12 else startHour % 12).toString() else "%02d".format(startHour))
    }
    var minute by rememberSaveable { mutableStateOf("%02d".format(minuteOfDay % 60)) }
    var pm by rememberSaveable { mutableStateOf(startHour >= 12) }
    val minutesBox = remember { FocusRequester() }
    val read = TimeInput.clock(hour, minute, pm.takeIf { twelveHour })
    fun update() = TimeInput.clock(hour, minute, pm.takeIf { twelveHour })?.let(onChange)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Txt(label, Type.bodyStrong, modifier = Modifier.padding(bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DigitBox(hour, "$label, hours", 72.dp, read != null, ImeAction.Next, 2) { raw ->
                hour = raw
                update()
                if (raw.length >= 2) minutesBox.requestFocus()
            }
            Txt(":", Type.figure)
            DigitBox(minute, "$label, minutes", 72.dp, read != null, ImeAction.Done, 2, Modifier.focusRequester(minutesBox)) { raw ->
                minute = raw
                update()
            }
            if (twelveHour) {
                Column(Modifier.width(112.dp).padding(start = 4.dp)) {
                    Segmented(listOf(false, true), pm, { if (it) "PM" else "AM" }) { pm = it; update() }
                }
            }
        }
        Txt(
            read?.let { "Set for ${TimeInput.label(it, twelveHour)}" } ?: if (twelveHour) "Hours 1 to 12, minutes 0 to 59" else "Hours 0 to 23, minutes 0 to 59",
            Type.small,
            if (read != null) colors.muted else colors.negative,
            Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * A large number box that only takes digits, up to [length]. The text is always [value]; the first
 * digit after a tap replaces it, wherever the tap left the cursor.
 */
@Composable
private fun DigitBox(
    value: String,
    description: String,
    width: Dp,
    valid: Boolean,
    ime: ImeAction,
    length: Int,
    modifier: Modifier = Modifier,
    onValue: (String) -> Unit,
) {
    val colors = Theme.colors
    val shape = RoundedCornerShape(Space.radiusControl)
    var selection by remember { mutableStateOf(TextRange(value.length)) }
    var fresh by remember { mutableStateOf(false) }
    BasicTextField(
        TextFieldValue(value, selection.coerceIn(value.length)),
        { raw ->
            if (raw.text == value) {
                selection = raw.selection
                return@BasicTextField
            }
            val typed = if (fresh) TimeInput.firstKeystroke(value, raw.text) else raw.text
            fresh = false
            val digits = typed.filter(Char::isDigit).take(length)
            selection = TextRange(digits.length)
            onValue(digits)
        },
        singleLine = true,
        textStyle = Type.figure.copy(color = colors.text, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(colors.text),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ime),
        modifier = modifier
            .width(width)
            .heightIn(min = 64.dp)
            .background(colors.surface, shape)
            .border(if (valid) Space.stroke else 2.dp, if (valid) colors.control else colors.negative, shape)
            .onFocusChanged { fresh = it.isFocused }
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics { contentDescription = description },
    )
}

private fun TextRange.coerceIn(length: Int) = TextRange(start.coerceAtMost(length), end.coerceAtMost(length))

private fun presetLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes m"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60}"
}
