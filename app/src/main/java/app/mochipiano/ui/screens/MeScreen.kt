package app.mochipiano.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mochipiano.course.Course
import app.mochipiano.course.Practice
import app.mochipiano.data.Store
import app.mochipiano.midi.Link
import app.mochipiano.midi.MidiHub
import app.mochipiano.music.Pitch
import app.mochipiano.ui.components.ConnPill
import app.mochipiano.ui.components.Chip
import app.mochipiano.ui.components.Hairline
import app.mochipiano.ui.components.Label
import app.mochipiano.ui.components.MCard
import app.mochipiano.ui.components.Page
import app.mochipiano.ui.components.Segmented
import app.mochipiano.ui.components.Toggle
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeScreen(onConnect: () -> Unit, onRecord: () -> Unit, onCalibrate: () -> Unit) {
    val d = Store.data
    val conn by MidiHub.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(d.name) }
    val today = LocalDate.now()
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val mins = week.map { d.days[it.toString()] ?: 0 }
    val max = (mins.maxOrNull() ?: 0).coerceAtLeast(10)
    val lv = d.xp / 120 + 1
    val titles = listOf("Tiny Sprout", "Key Explorer", "Beat Keeper", "Melody Maker", "Staff Reader", "Little Maestro", "Mochi Virtuoso")

    Page(bottomPad = 32.dp) {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${d.name}'s piano", style = T.display, color = MC.Ink)
            Text("${titles[(lv - 1).coerceIn(0, titles.lastIndex)]}, level $lv · ${d.xp} XP", style = T.body, color = MC.InkSoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("${d.streak}", "day streak", Modifier.weight(1f))
            Stat("${Course.lessons.count { Practice.done(d, it.id) }}", "lessons", Modifier.weight(1f))
            Stat("${mins.sum()}", "min this week", Modifier.weight(1f))
        }

        MCard {
            Text("This week", style = T.h3, color = MC.Ink)
            Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                week.forEachIndexed { i, day ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)) {
                        Text(if (mins[i] > 0) "${mins[i]}" else "", style = T.monoS, color = MC.InkSoft)
                        Box(Modifier.widthIn(max = 30.dp).fillMaxWidth().fillMaxHeight((mins[i].toFloat() / max * 0.72f).coerceAtLeast(0.03f))
                            .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 4.dp, bottomEnd = 4.dp)).background(if (i == 6) MC.Right else MC.RightSoft))
                        Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), style = T.small, color = MC.InkSoft, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        MCard {
            Text("Notes you can read", style = T.h3, color = MC.Ink)
            val t = Practice.treblePool(d)
            val b = Practice.bassPool(d)
            if (t.isEmpty()) Text("Notes appear here after \"Meet the staff\".", style = T.body, color = MC.InkSoft)
            if (t.isNotEmpty()) { Label("Treble"); NoteChips(t, "t:", d.settings.solfege) }
            if (b.isNotEmpty()) { Label("Bass"); NoteChips(b, "b:", d.settings.solfege) }
            if (t.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Learning" to MC.ButterSoft, "Getting there" to MC.LeftSoft, "Mastered" to MC.MintSoft).forEach { (l, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(c)); Text(l, style = T.small, color = MC.InkSoft) }
                }
            }
        }

        MCard {
            Text("Settings", style = T.h3, color = MC.Ink)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Name", style = T.body, color = MC.Ink, modifier = Modifier.weight(1f))
                BasicTextField(
                    name, { v -> name = v.take(20); if (v.isNotBlank()) Store.update { it.copy(name = v.trim().take(20)) } },
                    singleLine = true, textStyle = T.bodyStrong.copy(color = MC.Ink), cursorBrush = SolidColor(MC.Right),
                    modifier = Modifier.widthIn(min = 120.dp, max = 180.dp).clip(RoundedCornerShape(12.dp)).background(MC.Porcelain).border(2.dp, MC.Mist, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Hairline()
            Text("Note names", style = T.body, color = MC.Ink)
            Segmented(listOf(false to "C D E", true to "Do Re Mi"), d.settings.solfege, { v -> Store.update { it.copy(settings = it.settings.copy(solfege = v)) } })
            Hairline()
            SettingRow("Show letters on keys", "Turn off once you can find notes without them", d.settings.letters) { v -> Store.update { it.copy(settings = it.settings.copy(letters = v)) } }
            Hairline()
            SettingRow("Play the other hand for me", "When you practise one hand, the piano plays the other", d.settings.otherHandForMe) { v -> Store.update { it.copy(settings = it.settings.copy(otherHandForMe = v)) } }
            Hairline()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Timing", style = T.body, color = MC.Ink)
                    Text(if (d.settings.calibrated) "${d.settings.latencyMs} ms in songs, ${d.settings.latencyAudioMs} ms with clicks" else "Not checked yet", style = T.small, color = MC.InkSoft)
                }
                Chip(if (d.settings.calibrated) "Check again" else "Check", bg = MC.Porcelain, onClick = onCalibrate, modifier = Modifier.height(36.dp))
            }
            Hairline()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Recordings", style = T.body, color = MC.Ink)
                    Text("${app.mochipiano.record.Recordings.takes.size} saved", style = T.small, color = MC.InkSoft)
                }
                Chip("Open", bg = MC.Porcelain, onClick = onRecord, modifier = Modifier.height(36.dp))
            }
            Hairline()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Piano", style = T.body, color = MC.Ink)
                    Text(if (conn.link == Link.Connected) "${conn.name} over ${conn.kind}" else d.lastBtName?.let { "Last used: $it" } ?: "Not connected", style = T.small, color = MC.InkSoft)
                }
                ConnPill(conn, onConnect)
            }
        }
        Text("Mochi Piano · fonts: Bricolage Grotesque, Geist and Noto Music (SIL Open Font License). Songs are public domain.", style = T.small, color = MC.InkSoft)
    }
}

@Composable
private fun Stat(v: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MC.Surface).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, style = T.monoBig, color = MC.Ink)
        Text(label, style = T.small, color = MC.InkSoft, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteChips(pool: List<Int>, prefix: String, solfege: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pool.forEach { p ->
            val s = Store.data.notes[prefix + p]
            val bg = when { s == null -> MC.Mist; s.b >= 5 -> MC.MintSoft; s.b >= 3 -> MC.LeftSoft; else -> MC.ButterSoft }
            Column(Modifier.widthIn(min = 46.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Pitch.display(Pitch.letter(p), solfege), style = T.bodyStrong, color = if (s == null) MC.InkSoft else MC.Ink)
                Text(Pitch.name(p), style = T.monoS, color = MC.InkSoft)
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.body, color = MC.Ink)
            Text(sub, style = T.small, color = MC.InkSoft)
        }
        Toggle(on, onChange, title)
    }
}
