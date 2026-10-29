package app.mochipiano.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mochipiano.audio.Sound
import app.mochipiano.course.Clef
import app.mochipiano.course.Course
import app.mochipiano.course.Mood
import app.mochipiano.course.Step
import app.mochipiano.course.Tint
import app.mochipiano.course.Vis
import app.mochipiano.data.LessonRec
import app.mochipiano.data.Store
import app.mochipiano.midi.Link
import app.mochipiano.midi.MidiHub
import app.mochipiano.midi.NoteEvent
import app.mochipiano.music.Hand
import app.mochipiano.music.Pitch
import app.mochipiano.ui.LessonSession
import app.mochipiano.ui.components.Bubble
import app.mochipiano.ui.components.Chip
import app.mochipiano.ui.components.Cover
import app.mochipiano.ui.components.HandView
import app.mochipiano.ui.components.Ic
import app.mochipiano.ui.components.MCard
import app.mochipiano.ui.components.Mochi
import app.mochipiano.ui.components.Page
import app.mochipiano.ui.components.PianoKeys
import app.mochipiano.ui.components.PrimaryButton
import app.mochipiano.ui.components.RhythmStrip
import app.mochipiano.ui.components.RoundIcon
import app.mochipiano.ui.components.SoftButton
import app.mochipiano.ui.components.StaffNote
import app.mochipiano.ui.components.StaffView
import app.mochipiano.ui.components.Stars
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private fun tintColor(t: Tint) = when (t) {
    Tint.Right -> MC.RightSoft; Tint.Left -> MC.LeftSoft; Tint.Twin -> MC.Right; Tint.Triplet -> MC.Butter; Tint.Soft -> MC.ButterSoft
}

/** Run [onOn] for every key she presses while this composable is on screen. */
@Composable
private fun OnKeys(key: Any, onOn: (NoteEvent) -> Unit) {
    val cb by rememberUpdatedState(onOn)
    LaunchedEffect(key) { MidiHub.events.collect { if (it.on) cb(it) } }
}

/** On-screen keys act like the piano: inject the note, and sound it when no piano is there to do it. */
private fun tapKey(p: Int, down: Boolean) {
    if (down && !MidiHub.canSendToPiano) Sound.note(p, 500)
    MidiHub.inject(p, down)
}

@Composable
fun LessonScreen(s: LessonSession, onExit: () -> Unit, onPlaySong: (String) -> Unit, onNextLesson: (String) -> Unit) {
    val data = Store.data
    val solfege = data.settings.solfege
    val steps = s.lesson.steps
    val conn by MidiHub.state.collectAsStateWithLifecycle()

    if (s.finished || s.index >= steps.size) {
        Finish(s, onExit, onNextLesson)
        return
    }
    val step = steps[s.index]
    val next: () -> Unit = { if (s.index >= steps.size - 1) s.finished = true else s.index++ }

    Page {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIcon(Ic.Close, "Close lesson", onExit)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                steps.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(99.dp)).background(
                        when { i < s.index -> MC.Ink; i == s.index -> MC.Right; else -> MC.KeyLine }
                    ))
                }
            }
            Text("${s.index + 1}/${steps.size}", style = T.mono, color = MC.InkSoft)
        }
        when (step) {
            is Step.Info -> InfoStep(step, s.index == 0, solfege, next)
            is Step.Choice -> ChoiceStep(s, step, next)
            is Step.Play -> PlayStep(s, step, solfege, next)
            is Step.Find -> FindStep(s, step, solfege, next)
            is Step.Read -> ReadStep(s, step, solfege, next)
            is Step.Rhythm -> RhythmStep(s, step, next)
            is Step.Song -> SongStep(s, step, onPlaySong, next)
            is Step.Echo -> EchoStep(s, step, solfege, next)
        }
        if (step !is Step.Info && step !is Step.Choice && step !is Step.Song) ListeningLine(conn.link == Link.Connected)
    }
}

private fun name(t: String) = t.replace("{name}", Store.data.name)

@Composable
private fun ListeningLine(connected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(6, 14, 20, 12, 6).forEach { h -> Box(Modifier.size(4.dp, h.dp).clip(RoundedCornerShape(2.dp)).background(if (connected) MC.Left else MC.KeyLine)) }
        }
        Text(if (connected) "Listening to your piano" else "No piano connected. Tap the keys on screen to try.", style = T.smallStrong, color = MC.InkSoft)
    }
}

// ------------------------------------------------------------------ info

@Composable
private fun InfoStep(st: Step.Info, first: Boolean, solfege: Boolean, next: () -> Unit) {
    Bubble(name(st.title), name(st.text), mood = st.mood)
    VisBlock(st.vis, solfege)
    PrimaryButton(if (first) "Let's go" else "Got it", next, Modifier.fillMaxWidth())
}

@Composable
private fun VisBlock(v: Vis, solfege: Boolean) {
    when (v) {
        Vis.None -> {}
        is Vis.Keys -> MCard(pad = 12.dp) {
            PianoKeys(
                v.range, fill = v.tint.mapValues { tintColor(it.value) }, fingers = v.fingers,
                labels = if (v.labels) v.tint.keys.filter { !Pitch.isBlack(it) }.toSet() else emptySet(),
                solfege = solfege, middleC = v.middleC, onKey = ::tapKey,
            )
        }
        is Vis.Staff -> MCard(pad = 12.dp) { StaffView(v.notes.map { StaffNote(it) }, v.clef, letters = v.letters && v.notes.isNotEmpty(), solfege = solfege) }
        Vis.Hands -> MCard { HandView(Modifier.fillMaxWidth(0.62f).align(Alignment.CenterHorizontally)) }
        is Vis.Rhythm -> MCard(pad = 12.dp) { RhythmStrip(v.pattern) }
        Vis.Alphabet -> MCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                listOf('A', 'B', 'C', 'D', 'E', 'F', 'G').forEachIndexed { i, c ->
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (i == 0) MC.RightSoft else MC.ButterSoft), contentAlignment = Alignment.Center) {
                        Text(Pitch.display(c, solfege), style = T.h3, color = MC.Ink)
                    }
                }
            }
            Text("…then A B C again, all the way up the piano.", style = T.small, color = MC.InkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        is Vis.Checklist -> {
            val ticked = remember { mutableStateListOf<Int>() }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                v.items.forEachIndexed { i, item ->
                    val on = i in ticked
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) MC.MintSoft else MC.Surface)
                            .clickable(role = Role.Checkbox) { if (on) ticked.remove(i) else ticked.add(i) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(if (on) MC.Mint else MC.Mist), contentAlignment = Alignment.Center) {
                            if (on) Icon(Ic.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Text(item, style = T.bodyStrong, color = MC.Ink)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ choice

@Composable
private fun ChoiceStep(s: LessonSession, st: Step.Choice, next: () -> Unit) {
    var picked by remember(st) { mutableStateOf<String?>(null) }
    LaunchedEffect(picked) {
        val p = picked ?: return@LaunchedEffect
        delay(if (p == st.answer) 700 else 1500)
        next()
    }
    Bubble(name(st.title), null, mood = Mood.Listening)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        st.options.forEach { o ->
            val bg = when { picked == null -> MC.Surface; o == st.answer -> MC.Mint; o == picked -> MC.Right; else -> MC.Surface }
            val fg = if (picked != null && (o == st.answer || o == picked)) Color.White else MC.Ink
            Box(
                Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                    .clickable(enabled = picked == null, role = Role.Button) { picked = o; s.total++; if (o == st.answer) s.correct++ },
                contentAlignment = Alignment.Center,
            ) { Text(o, style = T.title, color = fg) }
        }
    }
    picked?.let { Text(if (it == st.answer) "Yes!" else "It's ${st.answer}.", style = T.bodyStrong, color = if (it == st.answer) MC.MintText else MC.Right) }
}

// ------------------------------------------------------------------ play a sequence

@Composable
private fun PlayStep(s: LessonSession, st: Step.Play, solfege: Boolean, next: () -> Unit) {
    val total = st.seq.size * st.rounds
    var idx by remember(st) { mutableIntStateOf(0) }
    var mistakes by remember(st) { mutableIntStateOf(0) }
    var msg by remember(st) { mutableStateOf<String?>(null) }
    var done by remember(st) { mutableStateOf(false) }
    val held by MidiHub.held.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val target = if (idx < total) st.seq[idx % st.seq.size] else emptyList()

    OnKeys(st) { e ->
        if (done || target.isEmpty()) return@OnKeys
        if (e.pitch !in target) {
            mistakes++
            msg = "That was ${Pitch.displayPitch(e.pitch, solfege)}. Look for the lit key."
            return@OnKeys
        }
        val complete = target.size == 1 || MidiHub.held.value.containsAll(target)
        if (complete) {
            msg = null
            idx++
            if (idx >= total) {
                done = true
                s.correct += total; s.total += total + mistakes
            }
        }
    }

    Bubble(name(st.title), st.text, mood = if (done) Mood.Cheer else Mood.Listening)
    if (st.staff != null) {
        val notes = st.seq.map { chord -> StaffNote(chord.last()) }
        val cur = idx % st.seq.size
        MCard(pad = 12.dp) {
            StaffView(
                notes.mapIndexed { i, n -> n.copy(color = if (!done && i < cur) MC.Mint else if (!done && i == cur) (if (st.hand == Hand.R) MC.Right else MC.Left) else MC.Ink, mark = if (!done && i < cur) MC.MintSoft else null) },
                st.staff, highlight = if (done) null else cur, highlightColor = if (st.hand == Hand.R) MC.RightSoft else MC.LeftSoft,
            )
        }
    }
    val soft = if (st.hand == Hand.R) MC.RightSoft else MC.LeftSoft
    val strong = if (st.hand == Hand.R) MC.Right else MC.Left
    MCard(pad = 12.dp) {
        PianoKeys(
            st.range,
            fill = if (done) emptyMap() else target.associateWith { soft },
            outline = if (done) emptyMap() else target.associateWith { strong },
            fingers = st.fingers.filterKeys { it in st.seq.flatten() },
            fingerBg = soft, fingerFg = if (st.hand == Hand.R) MC.RightText else MC.LeftText,
            labels = if (Store.data.settings.letters) st.seq.flatten().toSet() else emptySet(), solfege = solfege,
            held = held, heldColor = if (held.all { it in target }) MC.Mint else MC.Right,
            onKey = ::tapKey,
        )
    }
    if (!done) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val t = target.joinToString(" + ") { Pitch.displayPitch(it, solfege) }
            val f = target.mapNotNull { st.fingers[it] }.joinToString(" ")
            Chip(if (target.size > 1) "Together: $t" else "Next: $t" + if (f.isNotEmpty()) " · finger $f" else "", bg = soft, fg = MC.Ink)
            Text("${idx.coerceAtMost(total)} / $total", style = T.mono, color = MC.InkSoft)
        }
        msg?.let { Text(it, style = T.bodyStrong, color = MC.Right) }
        SoftButton("Hear it", {
            scope.launch {
                st.seq.forEach { chord -> chord.forEach { Sound.note(it, 480) }; delay(560) }
            }
        }, icon = Ic.Ear)
    } else {
        val acc = total.toDouble() / (total + mistakes)
        MCard(color = MC.MintSoft) {
            Text(if (mistakes == 0) "Clean, no wrong notes!" else "Done! ${(acc * 100).roundToInt()}% on target.", style = T.bodyStrong, color = MC.MintText)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Again", { idx = 0; mistakes = 0; done = false; msg = null }, Modifier.weight(1f))
            PrimaryButton("Continue", next, Modifier.weight(1f))
        }
    }
}

// ------------------------------------------------------------------ find keys

@Composable
private fun FindStep(s: LessonSession, st: Step.Find, solfege: Boolean, next: () -> Unit) {
    var count by remember(st) { mutableIntStateOf(st.count) }
    var asked by remember(st) { mutableIntStateOf(0) }
    var right by remember(st) { mutableIntStateOf(0) }
    var extra by remember(st) { mutableStateOf(false) }
    var current by remember(st) { mutableIntStateOf(pick(st, -1)) }
    var feedback by remember(st) { mutableStateOf<Pair<Boolean, String>?>(null) }
    var lastHit by remember(st) { mutableIntStateOf(-1) }
    val done = asked >= count
    val held by MidiHub.held.collectAsStateWithLifecycle()

    OnKeys(st) { e ->
        if (done || feedback != null) return@OnKeys
        val t = st.targets[current]
        val ok = t.accepts(e.pitch)
        Store.update { a -> a.copy(keys = a.keys + (t.prompt to (a.keys[t.prompt] ?: app.mochipiano.data.Box()).bump(ok, true))) }
        asked++; s.total++
        if (ok) { right++; s.correct++ }
        lastHit = e.pitch
        feedback = ok to (if (ok) "Yes! ${Pitch.displayPitch(e.pitch, solfege)}" else "That's ${Pitch.displayPitch(e.pitch, solfege)}")
    }
    LaunchedEffect(feedback) {
        if (feedback == null) return@LaunchedEffect
        delay(if (feedback!!.first) 650 else 1300)
        if (asked >= count && !extra && right.toDouble() / asked < 0.6) { extra = true; count += 3 }
        current = pick(st, current)
        feedback = null
    }

    if (done) {
        DoneCard(right, asked, next)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(st.targets[current].prompt.replace(Regex("any ([A-G])$")) { "any " + Pitch.display(it.groupValues[1][0], solfege) }, style = T.displayS, color = MC.Ink, modifier = Modifier.weight(1f))
        Text("${asked + 1} / $count", style = T.mono, color = MC.InkSoft)
    }
    if (extra) Text("A few bonus ones together. You're getting it.", style = T.smallStrong, color = MC.ButterText)
    MCard(pad = 12.dp) {
        val fb = feedback
        PianoKeys(
            st.range, held = held, heldColor = if (fb?.first == false) MC.Right else MC.Mint,
            fill = if (fb != null) st.range.filter { st.targets[current].accepts(it) }.associateWith { MC.MintSoft } else emptyMap(),
            labels = if (fb != null) setOf(lastHit).filter { !Pitch.isBlack(it) }.toSet() else emptySet(), solfege = solfege, middleC = true,
            onKey = ::tapKey,
        )
    }
    feedback?.let { (ok, m) -> Text(m, style = T.title, color = if (ok) MC.MintText else MC.Right) }
}

private fun pick(st: Step.Find, avoid: Int): Int {
    val w = st.targets.mapIndexed { i, t -> val b = Store.data.keys[t.prompt]?.b ?: 0; ((6 - b) * (6 - b)).toDouble() * (if (i == avoid && st.targets.size > 1) 0.1 else 1.0) }
    var r = Math.random() * w.sum()
    w.forEachIndexed { i, x -> r -= x; if (r <= 0) return i }
    return st.targets.lastIndex
}

// ------------------------------------------------------------------ read the staff

@Composable
private fun ReadStep(s: LessonSession, st: Step.Read, solfege: Boolean, next: () -> Unit) {
    val prefix = if (st.clef == Clef.Treble) "t:" else "b:"
    var count by remember(st) { mutableIntStateOf(st.count) }
    var asked by remember(st) { mutableIntStateOf(0) }
    var right by remember(st) { mutableIntStateOf(0) }
    var extra by remember(st) { mutableStateOf(false) }
    var note by remember(st) { mutableIntStateOf(pickNote(st.pool, prefix, -1)) }
    var feedback by remember(st) { mutableStateOf<Pair<Boolean, String>?>(null) }
    var shownAt by remember(st) { mutableStateOf(System.currentTimeMillis()) }
    val held by MidiHub.held.collectAsStateWithLifecycle()
    val done = asked >= count

    OnKeys(st) { e ->
        if (done || feedback != null) return@OnKeys
        val ok = e.pitch == note
        val fast = System.currentTimeMillis() - shownAt < 5000
        Store.update { a -> a.copy(notes = a.notes + (prefix + note to (a.notes[prefix + note] ?: app.mochipiano.data.Box()).bump(ok, fast))) }
        asked++; s.total++
        if (ok) { right++; s.correct++ }
        feedback = ok to when {
            ok -> "Yes! ${Pitch.displayPitch(note, solfege)}"
            Pitch.pc(e.pitch) == Pitch.pc(note) -> "Right letter, wrong octave. This ${Pitch.displayPitch(note, solfege)} is ${if (e.pitch < note) "higher" else "lower"}."
            else -> "That was ${Pitch.displayPitch(e.pitch, solfege)}. This one is ${Pitch.displayPitch(note, solfege)}."
        }
    }
    LaunchedEffect(feedback) {
        val fb = feedback ?: return@LaunchedEffect
        delay(if (fb.first) 700 else 1800)
        if (asked >= count && !extra && right.toDouble() / asked < 0.6) { extra = true; count += 3 }
        note = pickNote(st.pool, prefix, note)
        shownAt = System.currentTimeMillis()
        feedback = null
    }

    if (done) { DoneCard(right, asked, next); return }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text("Play this note", style = T.displayS, color = MC.Ink)
        Text("${asked + 1} / $count", style = T.mono, color = MC.InkSoft)
    }
    if (extra) Text("A few bonus ones together. You're getting it.", style = T.smallStrong, color = MC.ButterText)
    val fb = feedback
    MCard(pad = 12.dp) {
        StaffView(listOf(StaffNote(note, 4.0, color = if (fb == null) MC.Ink else if (fb.first) MC.Mint else MC.Right)), st.clef, aspect = 2.2f,
            letters = fb != null, solfege = solfege, highlight = if (fb != null) 0 else null, highlightColor = if (fb?.first == true) MC.MintSoft else MC.RightSoft)
    }
    MCard(pad = 12.dp) {
        val range = if (st.clef == Clef.Treble) 60..83 else 48..60
        PianoKeys(range, held = held, heldColor = if (fb?.first == false) MC.Right else MC.Mint,
            fill = if (fb != null && !fb.first) mapOf(note to MC.MintSoft) else emptyMap(), middleC = true, onKey = ::tapKey)
    }
    fb?.let { (ok, m) -> Text(m, style = T.bodyStrong, color = if (ok) MC.MintText else MC.Right) }
}

private fun pickNote(pool: List<Int>, prefix: String, avoid: Int): Int {
    val w = pool.map { p -> val b = Store.data.notes[prefix + p]?.b ?: 0; ((6 - b) * (6 - b)).toDouble() * (if (p == avoid && pool.size > 1) 0.08 else 1.0) }
    var r = Math.random() * w.sum()
    pool.forEachIndexed { i, p -> r -= w[i]; if (r <= 0) return p }
    return pool.last()
}

// ------------------------------------------------------------------ rhythm

private enum class RState { Ready, Count, Play, Done }

@Composable
private fun RhythmStep(s: LessonSession, st: Step.Rhythm, next: () -> Unit) {
    val practice = s.practiceKey == "rhythm"
    var bpm by remember(st) { mutableIntStateOf(if (practice) Store.data.rhythmBpm else st.bpm) }
    var state by remember(st) { mutableStateOf(RState.Ready) }
    var cursor by remember(st) { mutableIntStateOf(-1) }
    var results by remember(st) { mutableStateOf<List<Color?>>(emptyList()) }
    var score by remember(st) { mutableStateOf<Double?>(null) }
    var tip by remember(st) { mutableStateOf("") }
    var countNum by remember(st) { mutableIntStateOf(0) }
    val taps = remember(st) { ArrayList<Long>() }
    val scope = rememberCoroutineScope()
    var recorded by remember(st) { mutableStateOf(false) }

    OnKeys(st) { e -> if (state == RState.Count || state == RState.Play) synchronized(taps) { taps += e.nanos } }

    fun start() = scope.launch { runRhythm(st.pattern, bpm, taps, { state = it }, { cursor = it }, { countNum = it }) { res, sc ->
        results = res; score = sc; state = RState.Done
        tip = when { sc >= 0.85 -> "Right on the beat!"; sc >= 0.6 -> "Nice and steady."; else -> "Keep going, it gets easier. Listen to the clicks." }
        if (!recorded) { recorded = true; s.total++; if (sc >= 0.6) s.correct++ }
        if (practice) {
            val nb = when { sc >= 0.85 -> (bpm + 6).coerceAtMost(116); sc < 0.55 -> (bpm - 6).coerceAtLeast(50); else -> bpm }
            if (nb != bpm) { tip += if (nb > bpm) " Speeding up to $nb bpm." else " Slowing to $nb bpm."; Store.update { it.copy(rhythmBpm = nb) }; bpm = nb }
        }
    } }

    Bubble(st.title, st.text, mood = if (state == RState.Done && (score ?: 0.0) >= 0.85) Mood.Cheer else Mood.Listening)
    MCard(pad = 12.dp) { RhythmStrip(st.pattern, current = cursor, results = results) }
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        when (state) {
            RState.Count -> Text("$countNum", style = T.display.copy(fontSize = T.display.fontSize * 2), color = MC.Right)
            RState.Play -> Mochi(Mood.Listening, Modifier.size(96.dp))
            else -> Text(if (state == RState.Done) "${((score ?: 0.0) * 100).roundToInt()}% in time" else "$bpm bpm", style = T.monoBig, color = MC.Ink)
        }
    }
    if (state == RState.Done) {
        Text(tip, style = T.bodyStrong, color = MC.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Perfect" to MC.Mint, "Good" to MC.Good, "Close" to MC.Butter, "Missed" to MC.Right).forEach { (l, c) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Text(l, style = T.small, color = MC.InkSoft) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Try again", { start() }, Modifier.weight(1f))
            PrimaryButton("Continue", next, Modifier.weight(1f))
        }
    } else if (state == RState.Ready) {
        PrimaryButton("Start at $bpm bpm", { start() }, Modifier.fillMaxWidth(), icon = Ic.Play)
        Text("Play any key on each note. Any key works.", style = T.small, color = MC.InkSoft)
    }
}

private suspend fun runRhythm(
    pattern: List<Double>, bpm: Int, taps: ArrayList<Long>,
    setState: (RState) -> Unit, setCursor: (Int) -> Unit, setCount: (Int) -> Unit,
    done: (List<Color?>, Double) -> Unit,
) {
    synchronized(taps) { taps.clear() }
    val beatNs = 60e9 / bpm
    val lat = 0L
    val t0 = System.nanoTime() + 350_000_000L
    val total = pattern.sumOf { abs(it) }
    val onsets = ArrayList<Pair<Int, Long>>()
    var acc = 0.0
    pattern.forEachIndexed { i, d -> if (d > 0) onsets += i to (t0 + ((4 + acc) * beatNs).toLong()); acc += abs(d) }
    setState(RState.Count)
    val beats = 4 + ceil(total).toInt()
    for (k in 0 until beats) {
        val at = t0 + (k * beatNs).toLong()
        delay(((at - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        Sound.click(k % 4 == 0)
        if (k < 4) setCount(4 - k)
        if (k == 4) setState(RState.Play)
        if (k >= 4) {
            val b = (k - 4).toDouble()
            var a = 0.0
            var cur = 0
            pattern.forEachIndexed { i, d -> if (b >= a - 1e-6) cur = i; a += abs(d) }
            setCursor(cur)
        }
    }
    delay(((t0 + ((4 + total) * beatNs).toLong() - System.nanoTime()) / 1_000_000 + 400).coerceAtLeast(0))
    val t = synchronized(taps) { ArrayList(taps) }
    val used = BooleanArray(t.size)
    val res = arrayOfNulls<Color>(pattern.size).toMutableList()
    var sc = 0.0
    onsets.forEach { (i, on) ->
        var best = -1; var be = Long.MAX_VALUE
        t.forEachIndexed { k, tp -> if (!used[k]) { val e = abs(tp - lat - on); if (e < be) { be = e; best = k } } }
        val ms = be / 1_000_000.0
        if (best >= 0 && ms < 280) {
            used[best] = true
            when { ms < 75 -> { res[i] = MC.Mint; sc += 1.0 }; ms < 150 -> { res[i] = MC.Good; sc += 0.75 }; else -> { res[i] = MC.Butter; sc += 0.4 } }
        } else res[i] = MC.Right
    }
    val extra = used.count { !it }
    setCursor(-1)
    done(res, (sc / onsets.size.coerceAtLeast(1) - extra * 0.07).coerceIn(0.0, 1.0))
}

// ------------------------------------------------------------------ song

@Composable
private fun SongStep(s: LessonSession, st: Step.Song, onPlaySong: (String) -> Unit, next: () -> Unit) {
    val song = Store.song(st.songId)
    Bubble(st.title, st.text, mood = if (s.songDone) Mood.Cheer else Mood.Calm)
    if (song != null) {
        MCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Cover(song.coverSeed, size = 64.dp)
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = T.h3, color = MC.Ink)
                    Text("${song.composer} · ${song.timeSig} · ${song.bars} bars", style = T.small, color = MC.InkSoft)
                }
            }
            Text("The first time, I'll wait for every note. Speed and hands can be changed on the next screen.", style = T.small, color = MC.InkSoft)
        }
    }
    if (s.songDone) PrimaryButton("Continue", next, Modifier.fillMaxWidth())
    PrimaryButton(if (s.songDone) "Play it again" else "Play it", { onPlaySong(st.songId) }, Modifier.fillMaxWidth(), icon = Ic.Play, color = if (s.songDone) MC.Ink else MC.Right)
    if (!s.songDone) SoftButton("Skip for now", next, Modifier.fillMaxWidth())
}

// ------------------------------------------------------------------ echo

@Composable
private fun EchoStep(s: LessonSession, st: Step.Echo, solfege: Boolean, next: () -> Unit) {
    var round by remember(st) { mutableIntStateOf(0) }
    var length by remember(st) { mutableIntStateOf(st.startLength) }
    var phrase by remember(st) { mutableStateOf(makePhrase(st.pool, st.startLength)) }
    var pos by remember(st) { mutableIntStateOf(0) }
    var listening by remember(st) { mutableStateOf(false) }
    var msg by remember(st) { mutableStateOf<String?>(null) }
    var right by remember(st) { mutableIntStateOf(0) }
    var streak by remember(st) { mutableIntStateOf(0) }
    val held by MidiHub.held.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    fun playPhrase(sc: CoroutineScope) = sc.launch {
        listening = true; pos = 0; msg = null
        delay(350)
        phrase.forEach { Sound.note(it, 450); delay(620) }
        listening = false
    }
    LaunchedEffect(phrase) { playPhrase(this) }

    OnKeys(st) { e ->
        if (listening || round >= st.rounds) return@OnKeys
        if (e.pitch == phrase[pos]) {
            pos++
            if (pos == phrase.size) {
                right++; s.correct++; s.total++; streak++
                round++
                msg = "Spot on!"
                if (streak >= 2 && length < 6) { length++; streak = 0 }
                if (round < st.rounds) scope.launch { delay(900); phrase = makePhrase(st.pool, length) }
            }
        } else {
            s.total++; streak = 0
            msg = "Not quite. Listen again."
            scope.launch { delay(700); playPhrase(this) }
        }
    }

    if (round >= st.rounds) { DoneCard(right, s.total.coerceAtLeast(right).coerceAtMost(right + st.rounds * 2), next); return }
    Bubble(st.title, if (listening) "Listen…" else "Now play it back. It starts on ${Pitch.displayPitch(phrase.first(), solfege)}.", mood = if (listening) Mood.Listening else Mood.Calm)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        phrase.indices.forEach { i -> Box(Modifier.size(18.dp).clip(CircleShape).background(if (i < pos) MC.Mint else MC.KeyLine)) }
    }
    MCard(pad = 12.dp) { PianoKeys(60..71, held = held, fill = mapOf(phrase.first() to MC.RightSoft), labels = st.pool.toSet(), solfege = solfege, onKey = ::tapKey) }
    msg?.let { Text(it, style = T.bodyStrong, color = if (it.startsWith("Spot")) MC.MintText else MC.Right) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SoftButton("Hear it again", { playPhrase(scope) }, Modifier.weight(1f), icon = Ic.Ear)
        Text("Round ${round + 1} of ${st.rounds}", style = T.mono, color = MC.InkSoft, modifier = Modifier.align(Alignment.CenterVertically))
    }
}

private fun makePhrase(pool: List<Int>, n: Int): List<Int> {
    val out = mutableListOf(pool.first())
    while (out.size < n) {
        val last = out.last()
        val near = pool.filter { abs(it - last) in 1..5 }
        out += (if (near.isNotEmpty()) near else pool).random()
    }
    return out
}

// ------------------------------------------------------------------ shared

@Composable
private fun DoneCard(right: Int, asked: Int, next: () -> Unit) {
    val a = if (asked == 0) 1.0 else right.toDouble() / asked
    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Mochi(if (a >= 0.8) Mood.Cheer else Mood.Calm, Modifier.size(110.dp))
        Text(if (a >= 0.9) "Brilliant!" else if (a >= 0.7) "Well done!" else "Good effort!", style = T.display, color = MC.Ink)
        Text("$right of $asked right", style = T.body, color = MC.InkSoft)
    }
    PrimaryButton("Continue", next, Modifier.fillMaxWidth())
}

@Composable
private fun Finish(s: LessonSession, onExit: () -> Unit, onNextLesson: (String) -> Unit) {
    val acc = if (s.total == 0) 1.0 else s.correct.toDouble() / s.total
    val stars = if (acc >= 0.9) 3 else if (acc >= 0.7) 2 else 1
    val practice = s.practiceKey != null
    val xp = if (practice) 12 else 25 + stars * 5
    LaunchedEffect(s) {
        val mins = ((System.currentTimeMillis() - s.startMs) / 60000).toInt()
        if (!practice) Store.update { a ->
            val prev = a.lessons[s.lesson.id]
            a.copy(lessons = a.lessons + (s.lesson.id to LessonRec(maxOf(prev?.stars ?: 0, stars), acc, (prev?.n ?: 0) + 1, Store.today())))
        }
        Store.markPractice(mins, xp)
        s.planKey?.let { Store.planMark(it) }
    }
    val nextLesson = if (practice) null else Course.lessons.getOrNull(Course.lessons.indexOf(s.lesson) + 1)
    Page {
        Spacer(Modifier.height(40.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Mochi(Mood.Cheer, Modifier.size(120.dp))
            Text(if (practice) "Nice practice!" else "Lesson complete!", style = T.display, color = MC.Ink)
            if (!practice) Stars(stars, 34.dp)
            Chip("+$xp XP", bg = MC.ButterSoft, fg = MC.ButterText)
            if (s.total > 0) Text("${s.correct} of ${s.total} right on the piano", style = T.body, color = MC.InkSoft)
        }
        Spacer(Modifier.height(12.dp))
        if (nextLesson != null) PrimaryButton("Next: ${nextLesson.title}", { onNextLesson(nextLesson.id) }, Modifier.fillMaxWidth())
        SoftButton("Back home", onExit, Modifier.fillMaxWidth())
    }
}
