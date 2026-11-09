package org.organiza.feature.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import org.organiza.ui.*
import org.organiza.presentation.AgendaModel
import org.organiza.presentation.BarKind

/** One day on an hour grid from 06:00, with a line at now (wireframe screen 4). */
@Composable
internal fun AgendaGrid(agenda: AgendaModel) {
    val colors = Theme.colors
    val hourHeight = 44.dp
    val hours = agenda.toHour - agenda.fromHour
    val total = hourHeight * hours
    if (agenda.blocks.isEmpty()) Txt("Nothing scheduled ${agenda.dayLabel.lowercase()}.", Type.body, colors.muted, Modifier.padding(bottom = 8.dp))
    Box(Modifier.fillMaxWidth().height(total)) {
        (0..hours).forEach { index ->
            Row(Modifier.offset(y = hourHeight * index - 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("%02d:00".format(agenda.fromHour + index), Type.small, colors.muted, Modifier.width(48.dp))
                Box(Modifier.weight(1f).height(1.dp).background(colors.rule))
            }
        }
        agenda.blocks.forEach { block ->
            val accent = when (block.kind) {
                BarKind.NOW -> colors.data
                BarKind.LATER -> colors.text
                BarKind.PAST -> colors.control.copy(alpha = 0.4f)
            }
            Row(
                Modifier
                    .padding(start = 56.dp)
                    .offset(y = total * block.top.toFloat())
                    .fillMaxWidth()
                    .height(maxOf(total * (block.bottom - block.top).toFloat(), 28.dp))
                    .background(colors.surface, RoundedCornerShape(8.dp))
                    .border(Space.stroke, colors.border, RoundedCornerShape(8.dp))
                    .semantics { contentDescription = "${block.label}, ${block.time}" },
            ) {
                Box(Modifier.width(5.dp).fillMaxWidth().height(maxOf(total * (block.bottom - block.top).toFloat(), 28.dp)).background(accent, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)))
                Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Txt(block.label, Type.bodyStrong, maxLines = 1)
                    Txt(block.time, Type.small, colors.muted, maxLines = 1)
                }
            }
        }
        agenda.now?.let { now ->
            Row(Modifier.offset(y = total * now.toFloat() - 4.dp).padding(start = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(colors.data, CircleShape))
                Box(Modifier.fillMaxWidth().height(2.dp).background(colors.data))
            }
        }
    }
    Txt("Blue line: now. Blue edge: running. Ink edge: later.", Type.small, colors.muted, Modifier.padding(top = 8.dp))
}

/** The import hub (wireframe screen 5): a timetable photo read on the phone, a dictation, or typed text. */
@Composable
fun ImportScreen(ui: UiSnapshot, actions: Actions, back: () -> Unit, open: (Route) -> Unit) {
    val colors = Theme.colors
    val state = ui.schedule
    TopBar("Import schedule", back)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .clickable(role = Role.Button) { actions.pickTimetablePhoto() }
            .semantics { contentDescription = "Upload a timetable photo" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                colors.border,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Glyph.CAMERA, size = 32.dp)
                Icon(Glyph.DOCUMENT, size = 32.dp)
            }
            Spacer(Modifier.height(10.dp))
            Txt("Upload timetable photo", Type.section)
            Txt("Read on this device. The photo is never sent.", Type.small, colors.muted)
        }
    }
    Action("Dictate schedule", ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth()) { open(Route.DICTATION) }
    Panel {
        PanelHeader("Text to extract") { if (state.source.isNotEmpty()) Action("Clear", ButtonKind.TEXT) { actions.clearImport() } }
        BasicTextField(
            state.source,
            actions::setImportText,
            textStyle = Type.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .border(Space.stroke, colors.control, RoundedCornerShape(Space.radiusControl))
                .padding(12.dp)
                .semantics { contentDescription = "Timetable text" },
        )
        Txt("Names, emails and numbers are removed before it is sent to your AI key.", Type.small, colors.muted, Modifier.padding(top = 6.dp))
    }
    ImportResult(ui, actions)
}

@Composable
private fun ImportResult(ui: UiSnapshot, actions: Actions) {
    val colors = Theme.colors
    val state = ui.schedule
    state.busy?.let { Panel { Txt(it, Type.body, colors.muted) } }
    state.status?.let { Panel { Txt(it, Type.body) } }
    if (state.blocks.isNotEmpty()) {
        Panel {
            PanelHeader("Found ${state.blocks.size}")
            state.blocks.forEachIndexed { index, block ->
                if (index > 0) Rule()
                val day = block.day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Txt("$day %02d:%02d".format(block.startMinute / 60, block.startMinute % 60), Type.body, colors.muted, Modifier.width(96.dp))
                    Txt(block.label, Type.body, modifier = Modifier.weight(1f), maxLines = 1)
                    Txt("${block.minutes} min", Type.small, colors.muted)
                }
            }
            Rule()
            Txt("Each becomes a session on your focus list at its next weekly time. Only the times are kept.", Type.small, colors.muted)
            Spacer(Modifier.height(8.dp))
            Action("Add to agenda", ButtonKind.PRIMARY) { actions.addScheduleBlocks() }
        }
    }
    val ready = state.source.isNotBlank() && state.busy == null
    Action(if (ui.hasApiKey) "Extract with AI" else "Add a Groq key in Profile to extract", ButtonKind.PRIMARY, enabled = ready && ui.hasApiKey, modifier = Modifier.fillMaxWidth()) {
        actions.extractSchedule()
    }
}

/** Dictation (wireframe screen 6): hold to talk, a live waveform, then the text to extract. */
@Composable
fun DictationScreen(ui: UiSnapshot, actions: Actions, back: () -> Unit) {
    val colors = Theme.colors
    var held by remember { mutableStateOf(false) }
    val levels = remember { mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } } }
    LaunchedEffect(held) {
        while (held) {
            levels.removeAt(0)
            levels.add(actions.dictationLevel())
            delay(60)
        }
    }
    TopBar("Dictate schedule", back)
    Panel {
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Recording level" }) {
            val step = size.width / BARS
            levels.forEachIndexed { index, level ->
                val height = maxOf(3.dp.toPx(), size.height * level)
                val x = index * step + step / 2
                drawLine(colors.text, Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2), 3.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(72.dp)
                    .background(if (held) colors.data else colors.primary, CircleShape)
                    .semantics { contentDescription = "Hold to dictate" }
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            held = true
                            actions.startDictation()
                            tryAwaitRelease()
                            held = false
                            actions.stopDictation()
                        })
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Glyph.MIC, colors.onPrimary, 30.dp) }
        }
        Txt(if (held) "Listening. Release to transcribe" else "Hold and say your week, for example \"Monday nine to eleven, maths\".", Type.small, colors.muted)
    }
    Panel {
        PanelHeader("Preview")
        val state = ui.schedule
        if (state.source.isBlank()) Txt("Your words appear here. Extract them to find the times.", Type.body, colors.muted)
        else Txt(state.source, Type.body)
    }
    ImportResult(ui, actions)
}

private const val BARS = 48
