package org.organiza.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.organiza.voice.VoicePipeline

/**
 * What dictation is doing, so nobody waits without knowing why: listening, with a red dot, a running
 * clock against the voice pipeline's limit and the live sound level; then transcribing, with a spinner.
 * [level] reads the microphone's loudness, 0 to 1.
 */
@Composable
fun DictationStatus(listening: Boolean, transcribing: Boolean, level: () -> Float) {
    val colors = Theme.colors
    if (listening) {
        var seconds by remember { mutableIntStateOf(0) }
        val levels = remember { mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } } }
        LaunchedEffect(Unit) {
            var ticks = 0
            while (true) {
                levels.removeAt(0)
                levels.add(level())
                delay(TICK_MS)
                ticks++
                seconds = (ticks * TICK_MS / 1000).toInt()
            }
        }
        val pulse by rememberInfiniteTransition(label = "recording").animateFloat(
            1f,
            0.3f,
            infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "dot",
        )
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).alpha(pulse).background(colors.negative, CircleShape))
                Txt(
                    "Listening · ${clock(seconds)} of ${clock(LIMIT_S)}",
                    Type.bodyStrong,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Canvas(Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp).semantics { contentDescription = "Recording level" }) {
                val step = size.width / BARS
                levels.forEachIndexed { index, value ->
                    val height = maxOf(3.dp.toPx(), size.height * value)
                    val x = index * step + step / 2
                    drawLine(colors.data, Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2), 3.dp.toPx(), StrokeCap.Round)
                }
            }
            if (seconds >= LIMIT_S - WARN_S) Txt("Almost at the limit. Stop now to keep it", Type.small, colors.negative)
        }
    } else if (transcribing) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spinner()
                Txt("Turning your words into text…", Type.bodyStrong, modifier = Modifier.padding(start = 10.dp))
            }
            Txt("Sent to Groq on your own key. Usually a few seconds.", Type.small, colors.muted, Modifier.padding(top = 4.dp))
        }
    }
}

private fun clock(seconds: Int) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private const val BARS = 40
private const val TICK_MS = 100L
// Past this the pipeline sends nothing, so the clock shows the same limit.
private const val LIMIT_S = VoicePipeline.MAX_SECONDS
private const val WARN_S = 10
