package app.mochipiano.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mochipiano.audio.Sound
import app.mochipiano.data.Store
import app.mochipiano.game.GameConfig
import app.mochipiano.music.Hand
import app.mochipiano.music.Section
import app.mochipiano.ui.components.Chip
import app.mochipiano.ui.components.Cover
import app.mochipiano.ui.components.Ic
import app.mochipiano.ui.components.Label
import app.mochipiano.ui.components.MCard
import app.mochipiano.ui.components.Page
import app.mochipiano.ui.components.PrimaryButton
import app.mochipiano.ui.components.RoundIcon
import app.mochipiano.ui.components.Segmented
import app.mochipiano.ui.components.Toggle
import app.mochipiano.ui.components.TopBar
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SongSetupScreen(songId: String, fromLesson: Boolean, onBack: () -> Unit, onPlay: (GameConfig) -> Unit, onDeleted: () -> Unit) {
    val song = Store.song(songId) ?: run { onBack(); return }
    val st = Store.songStat(songId)
    val first = st.plays == 0
    val available = song.hands
    val initialHands = st.hands.takeIf { it.isNotEmpty() }?.let { h -> h.map { if (it == 'L') Hand.L else Hand.R }.toSet().filter { it in available }.toSet() }?.takeIf { it.isNotEmpty() }
        ?: if (Hand.R in available) setOf(Hand.R) else setOf(Hand.L)
    var hands by remember { mutableStateOf(initialHands) }
    var speed by remember { mutableStateOf(if (first) 0.7f else st.speed) }
    var autoSpeed by remember { mutableStateOf(true) }
    var wait by remember { mutableStateOf(first || fromLesson) }
    var dynamic by remember { mutableStateOf(!fromLesson && song.difficulty >= 3) }
    var clicks by remember { mutableStateOf(false) }
    var loop by remember { mutableStateOf<Section?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { preview?.cancel(); Sound.stopAll() } }

    Page {
        TopBar(onBack, trailing = {
            if (song.imported) RoundIcon(Ic.Trash, "Delete song", {
                if (confirmDelete) { Store.deleteSong(song.id); onDeleted() } else confirmDelete = true
            }, bg = if (confirmDelete) MC.Right else MC.Surface, tint = if (confirmDelete) MC.Surface else MC.Ink)
        })
        if (confirmDelete) Text("Tap the bin again to delete this song.", style = T.smallStrong, color = MC.Right)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Cover(song.coverSeed, size = 92.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(song.title, style = T.displayS, color = MC.Ink)
                if (song.composer.isNotBlank()) Text(song.composer, style = T.body, color = MC.InkSoft)
                Text("${song.timeSig} · ${song.bars} BARS · ♩ = ${song.bpm.roundToInt()}" + if (song.keyName.isNotBlank()) " · ${song.keyName.uppercase()}" else "", style = T.monoS, color = MC.InkSoft)
            }
        }
        if (st.plays > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Mastery ${(st.mastery * 100).roundToInt()}%", bg = MC.MintSoft, fg = MC.MintText)
            Chip("Played ${st.plays}×", bg = MC.Surface)
            Chip("Level ${st.level}", bg = MC.ButterSoft, fg = MC.ButterText)
        }

        if (available.size > 1) {
            Label("Hands")
            val opts = listOf(setOf(Hand.L) to "Left", setOf(Hand.R) to "Right", setOf(Hand.L, Hand.R) to "Both")
            Segmented(opts, hands, { hands = it }, dots = mapOf(setOf(Hand.L) to listOf(MC.Left), setOf(Hand.R) to listOf(MC.Right), setOf(Hand.L, Hand.R) to listOf(MC.Left, MC.Right)))
        }

        MCard(radius = 22.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Speed", style = T.bodyStrong, color = MC.Ink)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(speed * 100).roundToInt()}%", style = T.monoBig, color = MC.Ink)
                    Text(" · ${(song.bpm * speed).roundToInt()} bpm", style = T.mono, color = MC.InkSoft)
                }
            }
            Slider(
                value = speed, onValueChange = { speed = (Math.round(it * 20) / 20f).coerceIn(0.25f, 1f) }, valueRange = 0.25f..1f, steps = 14,
                colors = SliderDefaults.colors(thumbColor = MC.Right, activeTrackColor = MC.Right, inactiveTrackColor = MC.Line, activeTickColor = MC.RightSoft, inactiveTickColor = MC.KeyLine),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto speed-up", style = T.bodyStrong, color = MC.Ink)
                    Text("+5% after each clean loop", style = T.small, color = MC.InkSoft)
                }
                Toggle(autoSpeed, { autoSpeed = it }, "Auto speed-up")
            }
        }

        Label("How to play")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeCard("Note highway", "Notes fall in time", !wait, Modifier.weight(1f)) { wait = false }
            ModeCard("Wait for me", "Stops until you play", wait, Modifier.weight(1f)) { wait = true }
        }

        MCard(radius = 22.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dynamic difficulty", style = T.bodyStrong, color = MC.Ink)
                    Text("Starts with the main notes and adds more as you nail each section. Level ${st.level} of 10.", style = T.small, color = MC.InkSoft)
                }
                Toggle(dynamic, { dynamic = it }, "Dynamic difficulty")
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Metronome clicks", style = T.bodyStrong, color = MC.Ink)
                    Text("Clicks on every beat, not just the count-in", style = T.small, color = MC.InkSoft)
                }
                Toggle(clicks, { clicks = it }, "Metronome clicks")
            }
        }

        if (song.sections.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label("Sections · tap one to loop it")
                if (loop != null) Text("Looping ${loop!!.name}", style = T.smallStrong, color = MC.Right)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(song.sections) { i, sec ->
                    val acc = st.sectionAcc.entries.firstOrNull { it.key.substringAfter('#').toIntOrNull() == i }?.value
                    val bg = when { acc == null -> MC.Surface; acc >= 0.85 -> MC.MintSoft; acc >= 0.6 -> MC.ButterSoft; else -> MC.RightSoft }
                    val sel = loop == sec
                    Column(
                        Modifier.width(84.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                            .then(if (sel) Modifier.border(2.dp, MC.Right, RoundedCornerShape(14.dp)) else Modifier)
                            .clickable(role = Role.Button) { loop = if (sel) null else sec }.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(sec.name, style = T.h3, color = MC.Ink)
                        Text("${song.barOf(sec.start)}–${song.barOf(sec.end - 0.01)}" + (acc?.let { " · ${(it * 100).roundToInt()}%" } ?: ""), style = T.monoS, color = MC.InkSoft)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(if (preview?.isActive == true) Ic.Stop else Ic.Ear, "Listen first", {
                if (preview?.isActive == true) { preview?.cancel(); Sound.stopAll() } else preview = scope.launch {
                    val spb = 60.0 / (song.bpm * speed)
                    val start = loop?.start ?: 0.0
                    val end = loop?.end ?: minOf(song.length, start + song.barBeats * 8)
                    val ns = song.notes.filter { it.start >= start && it.start < end && it.hand in hands }
                    var t = start
                    ns.forEach { n ->
                        delay(((n.start - t) * spb * 1000).toLong())
                        t = n.start
                        Sound.note(n.pitch, (n.dur * spb * 950).toLong(), 72)
                    }
                }
            }, size = 56.dp)
            PrimaryButton("Play", {
                preview?.cancel()
                val h = hands.joinToString("") { it.name }
                Store.update { a -> a.copy(songs = a.songs + (song.id to Store.songStat(song.id).copy(hands = h, speed = speed))) }
                onPlay(GameConfig(song, hands, speed, wait, dynamic, if (dynamic) st.level else 10, loop, autoSpeed, Store.data.settings.otherHandForMe, clicks))
            }, Modifier.weight(1f), icon = Ic.Play)
        }
        if (fromLesson) Text("Tip: Wait for me is on, so the song waits for each note.", style = T.small, color = MC.InkSoft, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun ModeCard(title: String, sub: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(84.dp).clip(RoundedCornerShape(18.dp)).background(if (selected) MC.Ink else MC.Surface).clickable(role = Role.RadioButton, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = T.bodyStrong, color = if (selected) MC.Surface else MC.Ink)
        Text(sub, style = T.small, color = if (selected) MC.InkFaint else MC.InkSoft)
    }
}
