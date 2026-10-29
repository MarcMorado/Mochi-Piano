package app.mochipiano.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mochipiano.audio.Sound
import app.mochipiano.course.Mood
import app.mochipiano.data.Store
import app.mochipiano.midi.Link
import app.mochipiano.midi.MidiHub
import app.mochipiano.ui.components.Bubble
import app.mochipiano.ui.components.MCard
import app.mochipiano.ui.components.Page
import app.mochipiano.ui.components.PianoKeys
import app.mochipiano.ui.components.PrimaryButton
import app.mochipiano.ui.components.SoftButton
import app.mochipiano.ui.components.TopBar
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class CalPhase { Intro, Listen, Watch, Result }

/**
 * Two short tap-alongs: once to clicks (measures speaker + Bluetooth delay, used for rhythm exercises)
 * and once to a flashing circle with no sound (measures screen + Bluetooth delay, used in songs).
 */
@Composable
fun CalibrateScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val conn by MidiHub.state.collectAsStateWithLifecycle()
    val held by MidiHub.held.collectAsStateWithLifecycle()
    var phase by remember { mutableStateOf(CalPhase.Intro) }
    var beatOn by remember { mutableIntStateOf(-1) }
    var countIn by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf(false) }
    var audioMs by remember { mutableStateOf<Int?>(null) }
    var visualMs by remember { mutableStateOf<Int?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val taps = remember { ArrayList<Long>() }

    OnKeys("calibrate") { e -> if (phase == CalPhase.Listen || phase == CalPhase.Watch) synchronized(taps) { taps += e.nanos } }

    suspend fun measure(sound: Boolean): Int? {
        synchronized(taps) { taps.clear() }
        val beatNs = 60_000_000_000L / 90
        val t0 = System.nanoTime() + 600_000_000L
        val beats = mutableListOf<Long>()
        for (k in 0 until 16) {
            val at = t0 + k * beatNs
            delay(((at - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
            if (sound) Sound.click(k % 4 == 0)
            if (k < 4) countIn = 4 - k else { countIn = 0; beats += at }
            beatOn = k
            flash = true
            delay(110)
            flash = false
        }
        delay(500)
        beatOn = -1
        val t = synchronized(taps) { ArrayList(taps) }
        val diffs = beats.mapNotNull { b -> t.minByOrNull { abs(it - b) }?.let { (it - b) / 1_000_000.0 }?.takeIf { abs(it) < 350 } }
        if (diffs.size < 7) return null
        return diffs.sorted()[diffs.size / 2].toInt().coerceIn(0, 300)
    }

    Page {
        TopBar(onDone, center = { Text("Timing check", style = T.title, color = MC.Ink, modifier = Modifier.weight(1f)) })
        Bubble(
            when (phase) {
                CalPhase.Intro -> "Let's tune your timing"
                CalPhase.Listen -> "Play with the clicks"
                CalPhase.Watch -> "Now play with the circle"
                CalPhase.Result -> if (problem == null) "All set!" else "Let's try once more"
            },
            when (phase) {
                CalPhase.Intro -> "Bluetooth and the phone's speaker add a small delay. Play any key along with 12 beats, twice. I'll measure the delay and allow for it."
                CalPhase.Listen -> "Any key, right on each click. Four clicks count you in."
                CalPhase.Watch -> "No sound this time. Play the moment the circle lights up."
                CalPhase.Result -> problem ?: "I'll take these delays off when I judge your timing."
            },
            mood = if (phase == CalPhase.Result && problem == null) Mood.Cheer else Mood.Listening,
        )

        if (phase == CalPhase.Listen || phase == CalPhase.Watch) {
            val lit = beatOn >= 0
            val scale by animateFloatAsState(if (lit && flash) 1.12f else 1f, tween(40), label = "pulse")
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(170.dp).scale(scale).clip(CircleShape).background(
                        if (phase == CalPhase.Watch) (if (flash) MC.Right else MC.RightSoft) else MC.Surface
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (countIn > 0) "$countIn" else if (beatOn >= 4) "${beatOn - 3}" else "", style = T.display, color = if (phase == CalPhase.Watch && flash) MC.Surface else MC.Ink)
                }
            }
            MCard(pad = 12.dp) { PianoKeys(60..71, held = held, aspect = 2.6f, onKey = ::tapKey) }
        }

        if (phase == CalPhase.Result) {
            MCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("With sound", style = T.body, color = MC.InkSoft); Text(audioMs?.let { "$it ms" } ?: "–", style = T.monoBig, color = MC.Ink)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("With the screen", style = T.body, color = MC.InkSoft); Text(visualMs?.let { "$it ms" } ?: "–", style = T.monoBig, color = MC.Ink)
                }
                Text("Under 40 ms is great. Over 150 ms, a USB cable will feel tighter than Bluetooth.", style = T.small, color = MC.InkSoft)
            }
        }

        if (conn.link != Link.Connected) Text("Connect the piano first for a real measurement. Screen keys have their own delay.", style = T.smallStrong, color = MC.Right)

        when (phase) {
            CalPhase.Intro -> PrimaryButton("Start", {
                scope.launch {
                    problem = null
                    phase = CalPhase.Listen
                    audioMs = measure(true)
                    phase = CalPhase.Watch
                    delay(800)
                    visualMs = measure(false)
                    problem = if (audioMs == null || visualMs == null) "I didn't catch enough notes. Play one key on every beat, and keep going until the end." else null
                    phase = CalPhase.Result
                }
            }, Modifier.fillMaxWidth())
            CalPhase.Result -> {
                if (problem == null) PrimaryButton("Save", {
                    Store.update { it.copy(settings = it.settings.copy(latencyAudioMs = audioMs ?: 0, latencyMs = visualMs ?: 0, calibrated = true)) }
                    onDone()
                }, Modifier.fillMaxWidth())
                SoftButton("Try again", { phase = CalPhase.Intro }, Modifier.fillMaxWidth())
            }
            else -> {}
        }
        if (Store.data.settings.calibrated && phase == CalPhase.Intro) {
            Text("Saved now: ${Store.data.settings.latencyAudioMs} ms with sound, ${Store.data.settings.latencyMs} ms with the screen.", style = T.small, color = MC.InkSoft)
        }
    }
}
