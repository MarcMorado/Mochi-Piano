package app.mochipiano.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mochipiano.course.Course
import app.mochipiano.course.Lesson
import app.mochipiano.course.Mood
import app.mochipiano.course.Practice
import app.mochipiano.course.Tint
import app.mochipiano.data.Store
import app.mochipiano.midi.MidiHub
import app.mochipiano.ui.components.Bar
import app.mochipiano.ui.components.ConnPill
import app.mochipiano.ui.components.Cover
import app.mochipiano.ui.components.Ic
import app.mochipiano.ui.components.Label
import app.mochipiano.ui.components.MCard
import app.mochipiano.ui.components.Mochi
import app.mochipiano.ui.components.Page
import app.mochipiano.ui.components.PrimaryButton
import app.mochipiano.ui.components.Ring
import app.mochipiano.ui.components.Stars
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onLesson: (Lesson) -> Unit,
    onPlan: (Practice.Item) -> Unit,
    onSong: (String) -> Unit,
    onConnect: () -> Unit,
) {
    val d = Store.data
    val conn by MidiHub.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val plan = Practice.plan(d)
    val doneKeys = if (d.planDay == Store.today()) d.planDone else emptySet()
    val left = plan.filter { it.key !in doneKeys }
    val hour = LocalTime.now().hour

    Page(bottomPad = 32.dp) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + if (d.streak > 0) " · ${d.streak}-day streak" else "",
                    style = T.smallStrong, color = MC.InkSoft,
                )
                Text((if (hour < 12) "Good morning" else if (hour < 19) "Good afternoon" else "Good evening") + ",\n" + d.name, style = T.display, color = MC.Ink)
            }
            ConnPill(conn, onConnect)
        }

        Row(Modifier.fillMaxWidth()) {
            (6 downTo 0).forEach { back ->
                val day = today.minusDays(back.toLong())
                val practiced = (d.days[day.toString()] ?: 0) > 0
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), style = T.smallStrong, color = if (back == 0) MC.Ink else MC.InkSoft)
                    Box(
                        Modifier.size(32.dp).clip(CircleShape)
                            .background(if (practiced) MC.Right else if (back == 0) MC.RightSoft else MC.Mist)
                            .then(if (back == 0 && !practiced) Modifier.border(3.dp, MC.Right, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { if (practiced) Icon(Ic.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
        }

        MCard(radius = 28.dp, pad = 20.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Label("Today's practice")
                Text(if (left.isEmpty()) "all done" else "${left.sumOf { it.minutes }} min left", style = T.mono, color = MC.InkSoft)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Mochi(if (left.isEmpty()) Mood.Cheer else Mood.Calm, Modifier.size(60.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (left.isEmpty()) "Beautiful work" else left.first().title.substringAfter(" · "), style = T.title, color = MC.Ink)
                    Text(if (left.isEmpty()) "Your fingers will remember this tomorrow." else Practice.planReason(d), style = T.small, color = MC.InkSoft)
                }
            }
            Column {
                plan.forEachIndexed { i, it ->
                    val isDone = it.key in doneKeys
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MC.Line))
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onPlan(it) }.padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val first = !isDone && it == left.firstOrNull()
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(if (isDone) MC.MintSoft else if (first) MC.Right else Color.Transparent)
                                .then(if (!isDone && !first) Modifier.border(2.dp, MC.KeyLine, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isDone) Icon(Ic.Check, null, tint = MC.MintText, modifier = Modifier.size(14.dp))
                            else if (first) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(it.title, style = if (first) T.bodyStrong else T.body, color = if (isDone) MC.InkSoft else MC.Ink, textDecoration = if (isDone) TextDecoration.LineThrough else null, maxLines = 1)
                            Text(it.meta, style = T.small, color = MC.InkSoft, maxLines = 1)
                        }
                        Text("${it.minutes} min", style = T.mono, color = MC.InkSoft)
                    }
                }
            }
            left.firstOrNull()?.let { PrimaryButton(if (doneKeys.isEmpty()) "Start practice" else "Continue", { onPlan(it) }, Modifier.fillMaxWidth(), icon = Ic.Next) }
        }

        val recent = Store.allSongs().filter { d.songs[it.id]?.plays ?: 0 > 0 }.maxByOrNull { (d.songs[it.id]?.plays ?: 0) - (d.songs[it.id]?.mastery ?: 0.0) * 4 }
        if (recent != null) {
            val st = Store.songStat(recent.id)
            Text("Keep playing", style = T.h3, color = MC.Ink)
            MCard(radius = 22.dp, pad = 12.dp, onClick = { onSong(recent.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Cover(recent.coverSeed, size = 60.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(recent.title, style = T.bodyStrong, color = MC.Ink, maxLines = 1, modifier = Modifier.weight(1f))
                            Text("${(st.mastery * 100).roundToInt()}%", style = T.mono, color = MC.Ink)
                        }
                        Bar(st.mastery.toFloat(), MC.Right)
                        val weak = st.sectionAcc.minByOrNull { it.value }
                        Text(if (weak != null && weak.value < 0.85) "Section ${weak.key.substringBefore('#')} still trips you up" else "Try it a little faster", style = T.small, color = MC.InkSoft)
                    }
                }
            }
        }

        Text("Your path", style = T.h3, color = MC.Ink, modifier = Modifier.padding(top = 8.dp))
        val next = Practice.nextLesson(d)
        Course.units.forEachIndexed { ui, u ->
            val ls = Course.lessons.filter { it.unit == ui }
            val bg = when (u.tint) { Tint.Right -> MC.RightSoft; Tint.Left -> MC.LeftSoft; Tint.Twin -> MC.MintSoft; else -> MC.ButterSoft }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Label("Unit ${ui + 1}", color = MC.Ink.copy(alpha = 0.7f))
                    Text(u.title, style = T.title, color = MC.Ink)
                }
                Ring(ls.count { Practice.done(d, it.id) }.toFloat() / ls.size, MC.Ink, Modifier.size(42.dp), track = Color.White.copy(alpha = 0.6f), stroke = 4.dp,
                    label = "${ls.count { Practice.done(d, it.id) }}/${ls.size}")
            }
            ls.forEachIndexed { k, l ->
                val state = when { Practice.done(d, l.id) -> 2; l == next || Practice.lessonOpen(d, l) -> 1; else -> 0 }
                val indent = listOf(0.dp, 56.dp, 110.dp, 56.dp)[k % 4]
                Row(
                    Modifier.padding(start = indent).clip(RoundedCornerShape(20.dp)).clickable(enabled = state > 0, role = Role.Button) { onLesson(l) }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(when (state) { 2 -> MC.Mint; 1 -> MC.Right; else -> MC.Mist }),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (state) {
                            2 -> Icon(Ic.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            1 -> Text("${l.number}", style = T.title, color = Color.White)
                            else -> Icon(Ic.Lock, null, tint = MC.InkSoft, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(Modifier.width(150.dp)) {
                        if (state == 1 && l == next) Text("START HERE", style = T.label, color = MC.Right)
                        Text(l.title, style = T.bodyStrong, color = if (state == 0) MC.InkSoft else MC.Ink)
                        d.lessons[l.id]?.let { Stars(it.stars, 14.dp) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
