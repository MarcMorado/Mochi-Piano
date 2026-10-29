package app.mochipiano.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mochipiano.audio.Sound
import app.mochipiano.course.Clef
import app.mochipiano.course.Course
import app.mochipiano.course.Lesson
import app.mochipiano.course.Practice
import app.mochipiano.data.Store
import app.mochipiano.ui.components.Chip
import app.mochipiano.ui.components.Ic
import app.mochipiano.ui.components.Page
import app.mochipiano.ui.components.PianoKeys
import app.mochipiano.ui.components.PrimaryButton
import app.mochipiano.ui.components.RhythmStrip
import app.mochipiano.ui.components.RoundIcon
import app.mochipiano.ui.components.StaffNote
import app.mochipiano.ui.components.StaffView
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun GamesScreen(onStart: (Lesson, String) -> Unit) {
    val d = Store.data
    val t = Practice.treblePool(d)
    val b = Practice.bassPool(d)
    Page(bottomPad = 32.dp) {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Games", style = T.display, color = MC.Ink)
            Text("Quick games on the real piano. The ones you find hard come back more often.", style = T.body, color = MC.InkSoft)
        }
        GameCard("Note Ninja", "A note appears on the staff. Play it on the piano before it gets away.", MC.RightSoft, locked = if (t.isEmpty()) "Opens after lesson ${Course.byId("staff")?.number}, Meet the staff" else null,
            art = { StaffView(listOf(StaffNote(67)), Clef.Treble, Modifier.fillMaxWidth(0.55f), aspect = 2.2f) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Treble", bg = MC.Surface, onClick = { onStart(Practice.noteNinja(d, "treble"), "ninja") })
                if (b.isNotEmpty()) {
                    Chip("Bass", bg = MC.Surface, onClick = { onStart(Practice.noteNinja(d, "bass"), "ninja") })
                    Chip("Both", bg = MC.Surface, onClick = { onStart(Practice.noteNinja(d, "mix"), "ninja") })
                }
            }
        }
        GameCard("Key Finder", "Find any C, any F… as fast as you can.", MC.ButterSoft, locked = if (Practice.letterPool(d).isEmpty()) "Opens after lesson ${Course.byId("findc")?.number}, Meet C" else null,
            art = { PianoKeys(60..71, Modifier.fillMaxWidth(0.6f), fill = mapOf(64 to MC.Butter), aspect = 2.6f) },
            onClick = { onStart(Practice.keyFinder(d), "keys") })
        GameCard("Echo", "Mochi plays a little tune. Play it back by ear. Tunes get longer as you get them right.", MC.LeftSoft,
            locked = if (!Practice.done(d, "cde")) "Opens after lesson ${Course.byId("cde")?.number}, C, D and E" else null,
            art = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { i -> Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < 2) MC.Left else MC.Surface)) } } },
            onClick = { onStart(Practice.echo(d), "echo") })
        GameCard("Rhythm Tap", "A fresh rhythm each time, at ${d.rhythmBpm} bpm. Play any key on each note.", MC.MintSoft,
            locked = if (Practice.rhythmLevel(d) == 0) "Opens after lesson ${Course.byId("beat")?.number}, Feel the beat" else null,
            art = { RhythmStrip(listOf(1.0, 1.0, 2.0), Modifier.fillMaxWidth(0.55f)) },
            onClick = { onStart(Practice.rhythm(d), "rhythm") })
        Metronome()
    }
}

@Composable
private fun GameCard(
    title: String, text: String, tint: Color, locked: String?, art: @Composable () -> Unit,
    onClick: (() -> Unit)? = null, extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MC.Surface)
            .then(if (onClick != null && locked == null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp).alpha(if (locked != null) 0.55f else 1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(16.dp)).background(tint), contentAlignment = Alignment.Center) { art() }
        Text(title, style = T.title, color = MC.Ink)
        Text(locked ?: text, style = T.small, color = MC.InkSoft)
        if (locked == null) extra?.invoke(this)
    }
}

@Composable
private fun Metronome() {
    var on by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(72) }
    var beats by remember { mutableIntStateOf(4) }
    var beat by remember { mutableIntStateOf(-1) }
    LaunchedEffect(on, bpm, beats) {
        if (!on) { beat = -1; return@LaunchedEffect }
        var k = 0
        val start = System.nanoTime()
        val beatNs = 60_000_000_000L / bpm
        while (isActive) {
            val at = start + k * beatNs
            delay(((at - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
            Sound.click(k % beats == 0)
            beat = k % beats
            k++
        }
    }
    DisposableEffect(Unit) { onDispose { on = false } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MC.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Metronome", style = T.title, color = MC.Ink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            repeat(beats) { i -> Box(Modifier.size(if (i == beat) 20.dp else 16.dp).clip(CircleShape).background(if (i == beat) (if (i == 0) MC.Butter else MC.Right) else MC.Mist)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIcon(Ic.Back, "Slower", { bpm = (bpm - 4).coerceAtLeast(40) }, bg = MC.Porcelain)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$bpm", style = T.monoBig, color = MC.Ink)
                Text("BPM", style = T.monoS, color = MC.InkSoft)
            }
            RoundIcon(Ic.Next, "Faster", { bpm = (bpm + 4).coerceAtMost(180) }, bg = MC.Porcelain)
            Chip("$beats beats", bg = MC.Porcelain, onClick = { beats = if (beats == 4) 3 else 4 })
            PrimaryButton(if (on) "Stop" else "Start", { on = !on }, Modifier.weight(1f), icon = if (on) Ic.Stop else Ic.Play, color = if (on) MC.Ink else MC.Mint, height = 48.dp)
        }
    }
}
