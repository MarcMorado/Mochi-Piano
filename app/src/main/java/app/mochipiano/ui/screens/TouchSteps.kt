package app.mochipiano.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mochipiano.course.Dyn
import app.mochipiano.course.Mood
import app.mochipiano.course.Step
import app.mochipiano.data.Store
import app.mochipiano.midi.MidiHub
import app.mochipiano.music.Pitch
import app.mochipiano.ui.LessonSession
import app.mochipiano.ui.components.Bubble
import app.mochipiano.ui.components.Chip
import app.mochipiano.ui.components.MCard
import app.mochipiano.ui.components.PianoKeys
import app.mochipiano.ui.components.PrimaryButton
import app.mochipiano.ui.components.SoftButton
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ------------------------------------------------------------------ dynamics helpers

/** Zone edges from her own soft and loud, a third of the way and two thirds of the way up. */
private fun zones(): Pair<Int, Int> {
    val s = Store.data.settings
    val lo = s.dynSoft.coerceIn(1, 120)
    val hi = s.dynLoud.coerceIn(lo + 10, 127)
    return (lo + (hi - lo) / 3) to (lo + (hi - lo) * 2 / 3)
}

private fun zoneOf(v: Int): Dyn { val (a, b) = zones(); return if (v < a) Dyn.P else if (v < b) Dyn.MF else Dyn.F }

/** A bar showing the three zones with a marker at the last velocity. */
@Composable
private fun VelocityMeter(last: Int?, highlight: Dyn? = null) {
    val (a, b) = zones()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(34.dp)) {
            val w = maxWidth
            Row(Modifier.fillMaxWidth().height(22.dp).align(Alignment.Center).clip(RoundedCornerShape(11.dp))) {
                listOf(Dyn.P to a, Dyn.MF to (b - a), Dyn.F to (127 - b)).forEach { (z, span) ->
                    val on = highlight == null || highlight == z
                    Box(Modifier.weight(span.coerceAtLeast(1).toFloat()).fillMaxHeight().background(
                        when (z) { Dyn.P -> MC.LeftSoft; Dyn.MF -> MC.ButterSoft; Dyn.F -> MC.RightSoft }.copy(alpha = if (on) 1f else 0.35f)
                    ), contentAlignment = Alignment.Center) { Text(z.mark, style = T.h3, color = MC.Ink.copy(alpha = if (on) 1f else 0.4f)) }
                }
            }
            if (last != null) Box(Modifier.offset(x = w * (last / 127f) - 3.dp).width(6.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(MC.Ink))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("softer", style = T.small, color = MC.InkSoft)
            Text(last?.let { "velocity $it" } ?: "", style = T.mono, color = MC.Ink)
            Text("louder", style = T.small, color = MC.InkSoft)
        }
    }
}

// ------------------------------------------------------------------ touch range

@Composable
internal fun TouchRangeStep(st: Step.TouchRange, next: () -> Unit) {
    val soft = remember(st) { mutableStateListOf<Int>() }
    val loud = remember(st) { mutableStateListOf<Int>() }
    var last by remember(st) { mutableStateOf<Int?>(null) }
    var warn by remember(st) { mutableStateOf<String?>(null) }
    val phase = when { soft.size < 3 -> 0; loud.size < 3 -> 1; else -> 2 }

    OnKeys(st) { e ->
        last = e.velocity
        when {
            soft.size < 3 -> soft += e.velocity
            loud.size < 3 -> {
                loud += e.velocity
                if (loud.size == 3) {
                    val s = soft.sorted()[1]; val l = loud.sorted()[1]
                    if (l - s < 20) { warn = "Those were quite close. Let's try again with a bigger difference."; soft.clear(); loud.clear() }
                    else Store.update { it.copy(settings = it.settings.copy(dynSoft = s, dynLoud = l)) }
                }
            }
        }
    }

    Bubble(st.title, st.text, mood = Mood.Listening)
    MCard {
        Text(
            when (phase) { 0 -> "Play any key as softly as you can"; 1 -> "Now as loudly as you can"; else -> "Got it!" },
            style = T.title, color = MC.Ink,
        )
        Text(
            when (phase) { 0 -> "${soft.size} of 3"; 1 -> "${loud.size} of 3"; else -> "Your soft is about ${Store.data.settings.dynSoft}, your loud about ${Store.data.settings.dynLoud}." },
            style = T.body, color = MC.InkSoft,
        )
        if (phase < 2) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i -> Box(Modifier.size(16.dp).clip(CircleShape).background(if (i < (if (phase == 0) soft.size else loud.size)) (if (phase == 0) MC.Left else MC.Right) else MC.Mist)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("velocity", style = T.small, color = MC.InkSoft)
            Text(last?.toString() ?: "–", style = T.monoBig, color = MC.Ink)
        }
    }
    warn?.let { Text(it, style = T.bodyStrong, color = MC.Right) }
    if (phase == 2) PrimaryButton("Continue", next, Modifier.fillMaxWidth())
    if (!MidiHub.canSendToPiano) Text("Speed matters more than force on screen keys, so this works best on the piano.", style = T.small, color = MC.InkSoft)
}

// ------------------------------------------------------------------ p / mf / f

@Composable
internal fun DynamicsStep(s: LessonSession, st: Step.Dynamics, next: () -> Unit) {
    var i by remember(st) { mutableIntStateOf(0) }
    var right by remember(st) { mutableIntStateOf(0) }
    var last by remember(st) { mutableStateOf<Int?>(null) }
    var fb by remember(st) { mutableStateOf<Pair<Boolean, String>?>(null) }
    val held by MidiHub.held.collectAsStateWithLifecycle()
    val done = i >= st.targets.size

    OnKeys(st) { e ->
        if (done || fb != null) return@OnKeys
        last = e.velocity
        val want = st.targets[i]
        val got = zoneOf(e.velocity)
        val ok = got == want
        s.total++; if (ok) { right++; s.correct++ }
        fb = ok to when {
            ok -> "Yes, that's ${want.mark}!"
            got.ordinal < want.ordinal -> "A bit softer than ${want.mark}. Press with more weight."
            else -> "A bit louder than ${want.mark}. Let the key down more gently."
        }
    }
    LaunchedEffect(fb) {
        val f = fb ?: return@LaunchedEffect
        delay(if (f.first) 800 else 1500)
        i++; fb = null
    }

    if (done) { DoneCard(right, st.targets.size, next); return }
    Bubble(st.title, st.text, mood = Mood.Listening)
    val want = st.targets[i]
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(want.mark, style = T.display.copy(fontSize = T.display.fontSize * 1.6f), color = when (want) { Dyn.P -> MC.Left; Dyn.MF -> MC.ButterText; Dyn.F -> MC.Right })
            Text("Play ${Pitch.name(st.pitch)} ${want.word}", style = T.title, color = MC.Ink)
        }
        Text("${i + 1} / ${st.targets.size}", style = T.mono, color = MC.InkSoft)
    }
    MCard { VelocityMeter(last, highlight = want) }
    MCard(pad = 12.dp) { PianoKeys(60..71, held = held, fill = mapOf(st.pitch to MC.RightSoft), onKey = ::tapKey) }
    fb?.let { (ok, m) -> Text(m, style = T.bodyStrong, color = if (ok) MC.MintText else MC.Right) }
}

// ------------------------------------------------------------------ crescendo / diminuendo

@Composable
internal fun SwellStep(s: LessonSession, st: Step.Swell, solfege: Boolean, next: () -> Unit) {
    val vels = remember(st) { mutableStateListOf<Int>() }
    var tries by remember(st) { mutableIntStateOf(0) }
    var msg by remember(st) { mutableStateOf<String?>(null) }
    var done by remember(st) { mutableStateOf(false) }
    val held by MidiHub.held.collectAsStateWithLifecycle()

    OnKeys(st) { e ->
        if (done) return@OnKeys
        val idx = vels.size
        if (e.pitch != st.seq[idx]) { msg = "That was ${Pitch.displayPitch(e.pitch, solfege)}. Start again from ${Pitch.displayPitch(st.seq[0], solfege)}."; vels.clear(); tries++; return@OnKeys }
        if (idx > 0) {
            val prev = vels[idx - 1]
            val ok = if (st.up) e.velocity > prev + 2 else e.velocity < prev - 2
            if (!ok) {
                msg = if (st.up) "That one wasn't louder (${e.velocity} after $prev). Start again, softer at the beginning." else "That one wasn't softer (${e.velocity} after $prev). Start again, louder at the beginning."
                vels.clear(); tries++; return@OnKeys
            }
        }
        vels += e.velocity
        msg = null
        if (vels.size == st.seq.size) { done = true; s.total++; s.correct++; if (tries > 2) s.total++ }
    }

    Bubble(st.title, st.text, mood = if (done) Mood.Cheer else Mood.Listening)
    MCard {
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            st.seq.forEachIndexed { k, p ->
                val v = vels.getOrNull(k)
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)) {
                    Text(v?.toString() ?: "", style = T.monoS, color = MC.Ink)
                    Box(Modifier.fillMaxWidth().fillMaxHeight(((v ?: 6) / 127f * 0.75f).coerceAtLeast(0.04f)).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(if (v == null) MC.Mist else if (st.up) MC.Right else MC.Left))
                    Text(Pitch.displayPitch(p, solfege), style = T.bodyStrong, color = if (k == vels.size && !done) MC.Right else MC.Ink)
                }
            }
        }
    }
    MCard(pad = 12.dp) { PianoKeys(60..71, held = held, fill = if (done) emptyMap() else mapOf(st.seq[vels.size.coerceAtMost(st.seq.lastIndex)] to MC.RightSoft), labels = st.seq.toSet(), solfege = solfege, onKey = ::tapKey) }
    msg?.let { Text(it, style = T.bodyStrong, color = MC.Right) }
    if (done) {
        Text("Beautiful, you can hear it ${if (st.up) "grow" else "fade"}.", style = T.bodyStrong, color = MC.MintText)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Again", { vels.clear(); done = false; msg = null }, Modifier.weight(1f))
            PrimaryButton("Continue", next, Modifier.weight(1f))
        }
    }
}

// ------------------------------------------------------------------ sustain pedal

/** On-screen pedal for trying pedal steps without a piano: hold to press. */
@Composable
private fun ScreenPedal() {
    val down by MidiHub.sustain.collectAsStateWithLifecycle()
    Box(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(18.dp)).background(if (down) MC.Ink else MC.Mist)
            .pointerInput(Unit) { detectTapGestures(onPress = { MidiHub.injectPedal(true); tryAwaitRelease(); MidiHub.injectPedal(false) }) },
        contentAlignment = Alignment.Center,
    ) { Text(if (down) "Pedal down" else "Hold here for the pedal", style = T.bodyStrong, color = if (down) MC.Surface else MC.Ink) }
}

@Composable
private fun PedalBadge(down: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(if (down) MC.Mint else MC.KeyLine))
        Text(if (down) "Pedal is down" else "Pedal is up", style = T.bodyStrong, color = MC.Ink)
    }
}

@Composable
internal fun PedalStep(s: LessonSession, st: Step.Pedal, connected: Boolean, next: () -> Unit) {
    // 0 pedal down · 1 play chord · 2 hands off · 3 listen · 4 pedal up · 5 done
    var phase by remember(st) { mutableIntStateOf(0) }
    var heldAt by remember(st) { mutableLongStateOf(0L) }
    val held by MidiHub.held.collectAsStateWithLifecycle()
    val pedal by MidiHub.sustain.collectAsStateWithLifecycle()
    var msg by remember(st) { mutableStateOf<String?>(null) }
    val p by rememberUpdatedState(phase)

    LaunchedEffect(st) {
        MidiHub.pedalEvents.collect { (down, _) ->
            when {
                p == 0 && down -> phase = 1
                p in 1..3 && !down -> { msg = "The pedal came up early. Press it again and keep it down."; phase = 0 }
                p == 4 && !down -> { phase = 5; s.total++; s.correct++ }
            }
        }
    }
    LaunchedEffect(held, pedal, phase) {
        if (phase == 0 && pedal) phase = 1
        if (phase == 1 && held.containsAll(st.chord)) { phase = 2; msg = null }
        if (phase == 2 && held.none { it in st.chord }) { phase = 3; heldAt = System.nanoTime() }
        if (phase == 3) { delay(1600); if (phase == 3) phase = 4 }
    }

    Bubble(st.title, st.text, mood = if (phase == 5) Mood.Cheer else Mood.Listening)
    MCard {
        PedalBadge(pedal)
        val steps = listOf("Press the right pedal and keep it down", "Play the chord: " + st.chord.joinToString(" ") { Pitch.name(it) }, "Lift your hands off the keys", "Listen… the notes keep ringing", "Now lift the pedal: the sound stops")
        steps.forEachIndexed { k, t ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(if (k < phase) MC.Mint else if (k == phase) MC.Right else MC.Mist), contentAlignment = Alignment.Center) {
                    Text("${k + 1}", style = T.monoS, color = if (k <= phase) Color.White else MC.InkSoft)
                }
                Text(t, style = if (k == phase) T.bodyStrong else T.body, color = if (k <= phase) MC.Ink else MC.InkSoft)
            }
        }
    }
    MCard(pad = 12.dp) { PianoKeys(48..71, held = held, fill = if (phase == 1) st.chord.associateWith { MC.RightSoft } else emptyMap(), aspect = 2.4f, onKey = ::tapKey) }
    if (!connected) ScreenPedal()
    msg?.let { Text(it, style = T.bodyStrong, color = MC.Right) }
    if (phase == 5) PrimaryButton("Continue", next, Modifier.fillMaxWidth())
}

@Composable
internal fun PedalChangeStep(s: LessonSession, st: Step.PedalChange, connected: Boolean, next: () -> Unit) {
    var idx by remember(st) { mutableIntStateOf(0) }          // chord she should play next
    var chordAt by remember(st) { mutableLongStateOf(0L) }     // when the current chord was complete
    var lifted by remember(st) { mutableStateOf(false) }       // pedal lifted after this chord
    var changes by remember(st) { mutableIntStateOf(0) }
    var clean by remember(st) { mutableIntStateOf(0) }
    var msg by remember(st) { mutableStateOf<Pair<Boolean, String>?>(null) }
    val held by MidiHub.held.collectAsStateWithLifecycle()
    val pedal by MidiHub.sustain.collectAsStateWithLifecycle()
    val done = idx >= st.chords.size && changes >= st.chords.size - 1
    val state by rememberUpdatedState(Triple(idx, chordAt, lifted))

    LaunchedEffect(st) {
        MidiHub.pedalEvents.collect { (down, ts) ->
            val (i, at, l) = state
            if (i == 0) return@collect
            if (!down && !l) {
                if (at == 0L) return@collect
                val ms = (ts - at) / 1_000_000
                lifted = true
                msg = if (ms > 900) false to "A little late: change right after the chord." else true to "Nice change!"
            } else if (down && l) {
                lifted = false
                changes++
                if (msg?.first == true) clean++
                s.total++; if (msg?.first == true) s.correct++
            }
        }
    }
    LaunchedEffect(held) {
        if (idx >= st.chords.size) return@LaunchedEffect
        val c = st.chords[idx]
        if (held.containsAll(c)) {
            if (idx > 0 && lifted) msg = false to "Too early: play the new chord first, then change the pedal."
            chordAt = System.nanoTime()
            lifted = false
            idx++
        }
    }

    Bubble(st.title, st.text, mood = if (done) Mood.Cheer else Mood.Listening)
    MCard {
        PedalBadge(pedal)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            st.chords.forEachIndexed { k, c ->
                Chip(listOf("C", "F", "G", "C").getOrElse(k) { "${k + 1}" }, bg = if (k < idx) MC.MintSoft else if (k == idx) MC.RightSoft else MC.Mist, fg = MC.Ink)
            }
        }
        Text(
            when {
                done -> "All changes done: $clean of $changes clean."
                idx == 0 && !pedal -> "Press the pedal, then play the first chord."
                idx == 0 -> "Play the first chord."
                lifted -> "Pedal up… press it again now."
                else -> "Play the next chord, then lift and press the pedal."
            },
            style = T.bodyStrong, color = MC.Ink, textAlign = TextAlign.Start,
        )
    }
    MCard(pad = 12.dp) {
        PianoKeys(48..71, held = held, fill = if (idx < st.chords.size) st.chords[idx].associateWith { MC.RightSoft } else emptyMap(), aspect = 2.4f, onKey = ::tapKey)
    }
    if (!connected) ScreenPedal()
    msg?.let { (ok, m) -> Text(m, style = T.bodyStrong, color = if (ok) MC.MintText else MC.Right) }
    if (done) PrimaryButton("Continue", next, Modifier.fillMaxWidth())
    else if (idx >= st.chords.size) SoftButton("Finish", { s.total += (st.chords.size - 1 - changes).coerceAtLeast(0); next() }, Modifier.fillMaxWidth())
}

@Suppress("unused")
private fun pct(a: Int, b: Int) = if (b == 0) 0 else (a * 100.0 / b).roundToInt()
