package app.mochipiano.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mochipiano.course.Course
import app.mochipiano.course.Practice
import app.mochipiano.data.Store
import app.mochipiano.music.Hand
import app.mochipiano.ui.components.Chip
import app.mochipiano.ui.components.Cover
import app.mochipiano.ui.components.Ic
import app.mochipiano.ui.components.Page
import app.mochipiano.ui.components.Ring
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T
import kotlin.math.roundToInt

@Composable
fun SongsScreen(onSong: (String) -> Unit, onImport: () -> Unit) {
    val d = Store.data
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val all = Store.allSongs()
    val shown = all.filter { s ->
        (filter == "All" || (filter == "Course" && !s.imported) || (filter == "Imported" && s.imported)) &&
            (q.isBlank() || s.title.contains(q, true) || s.composer.contains(q, true))
    }

    Page(bottomPad = 32.dp) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Songs", style = T.display, color = MC.Ink)
            Text("${all.size} songs · ${all.count { (d.songs[it.id]?.mastery ?: 0.0) >= 0.9 }} mastered", style = T.mono, color = MC.InkSoft)
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(MC.Surface).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Ic.Search, null, tint = MC.InkSoft, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Search songs or composers", style = T.body, color = MC.InkSoft)
                BasicTextField(q, { q = it }, singleLine = true, textStyle = T.body.copy(color = MC.Ink), cursorBrush = SolidColor(MC.Right), modifier = Modifier.fillMaxWidth())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Course", "Imported").forEach { f ->
                Chip(f, bg = if (f == filter) MC.Ink else MC.Surface, fg = if (f == filter) MC.Surface else MC.Ink, onClick = { filter = f }, modifier = Modifier.height(36.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MC.Ink).clickable(role = Role.Button, onClick = onImport).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MC.Right), contentAlignment = Alignment.Center) {
                Icon(Ic.Plus, null, tint = MC.Surface, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Add your own sheet music", style = T.bodyStrong, color = MC.Surface)
                Text("MusicXML from MuseScore, or a MIDI file", style = T.small, color = MC.InkFaint)
            }
            Icon(Ic.Next, null, tint = MC.InkFaint, modifier = Modifier.size(18.dp))
        }
        if (shown.isEmpty()) Text(if (filter == "Imported") "No imported songs yet. Add a MusicXML or MIDI file to play anything you like." else "No songs match.", style = T.body, color = MC.InkSoft)
        shown.forEach { s ->
            val open = Practice.songOpen(d, s)
            val st = d.songs[s.id]
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MC.Surface)
                    .clickable(role = Role.Button) { if (open) onSong(s.id) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box { Cover(s.coverSeed, size = 54.dp); if (!open) Box(Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(MC.Porcelain.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) { Icon(Ic.Lock, null, tint = MC.InkSoft, modifier = Modifier.size(22.dp)) } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.title, style = T.bodyStrong, color = if (open) MC.Ink else MC.InkSoft, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (s.imported) Chip("Imported", bg = MC.ButterSoft, fg = MC.ButterText, style = T.monoS)
                        else if (st == null && open) Chip("New", bg = MC.RightSoft, fg = MC.RightText, style = T.monoS)
                    }
                    val hands = when { s.hands.size == 2 -> "both hands"; Hand.R in s.hands -> "right hand"; else -> "left hand" }
                    Text(
                        if (open) listOf(s.composer, hands, s.timeSig).filter { it.isNotBlank() }.joinToString(" · ")
                        else "Unlocks in lesson ${s.lessonId?.let { Course.byId(it)?.number } ?: ""}",
                        style = T.small, color = MC.InkSoft, maxLines = 1,
                    )
                }
                if (st != null) Ring(st.mastery.toFloat(), if (st.mastery >= 0.8) MC.Mint else if (st.mastery >= 0.5) MC.Butter else MC.Right, Modifier.size(44.dp), label = "${(st.mastery * 100).roundToInt()}")
                else Text("Lv ${s.difficulty}", style = T.mono, color = MC.InkSoft)
            }
        }
    }
}
