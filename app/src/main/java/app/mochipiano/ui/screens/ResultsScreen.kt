package app.mochipiano.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.mochipiano.course.Mood
import app.mochipiano.game.GameConfig
import app.mochipiano.game.GameResult
import app.mochipiano.music.Hand
import app.mochipiano.music.Pitch
import app.mochipiano.ui.components.Chip
import app.mochipiano.ui.components.Ic
import app.mochipiano.ui.components.MCard
import app.mochipiano.ui.components.Mochi
import app.mochipiano.ui.components.Page
import app.mochipiano.ui.components.PrimaryButton
import app.mochipiano.ui.components.Ring
import app.mochipiano.ui.components.SoftButton
import app.mochipiano.ui.components.TopBar
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ResultsScreen(
    r: GameResult, cfg: GameConfig, name: String, previousBest: Double, fromLesson: Boolean,
    onClose: () -> Unit, onAgain: (GameConfig) -> Unit, onLoop: (GameConfig) -> Unit, onContinueLesson: () -> Unit,
) {
    val pct = (r.accuracy * 100).roundToInt()
    val song = cfg.song
    Page {
        TopBar(onClose, icon = Ic.Close, center = {
            Column(Modifier.weight(1f)) {
                Text(song.title, style = T.bodyStrong, color = MC.Ink, maxLines = 1)
                val hands = when { cfg.hands.size == 2 -> "BOTH HANDS"; Hand.R in cfg.hands -> "RIGHT HAND"; else -> "LEFT HAND" }
                Text("${(r.speed * 100).roundToInt()}% SPEED · $hands" + if (cfg.dynamic) " · LEVEL ${r.level}" else "", style = T.monoS, color = MC.InkSoft)
            }
        })

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Ring(r.accuracy.toFloat(), if (pct >= 85) MC.Mint else if (pct >= 60) MC.Butter else MC.Right, Modifier.size(132.dp), stroke = 12.dp, label = "$pct%", labelStyle = T.displayS)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when { pct >= 90 -> "Lovely playing, $name"; pct >= 70 -> "Nice work, $name"; pct >= 45 -> "Getting there, $name"; else -> "Keep at it, $name" },
                    style = T.displayS, color = MC.Ink,
                )
                if (r.mastery > previousBest + 0.005 && previousBest > 0) Chip("New best · +${((r.mastery - previousBest) * 100).roundToInt()}%", bg = MC.MintSoft, fg = MC.MintText)
                else if (r.loops > 0) Chip("${r.loops} loops played", bg = MC.ButterSoft, fg = MC.ButterText)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Perfect", r.perfect, MC.Mint, Modifier.weight(1f))
            Stat("Good", r.good, MC.Good, Modifier.weight(1f))
            Stat("A bit off", r.off, MC.Butter, Modifier.weight(1f))
            Stat("Missed", r.miss, MC.Right, Modifier.weight(1f))
        }

        r.meanOffsetMs?.let { ms ->
            MCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Timing", style = T.bodyStrong, color = MC.Ink)
                    Text(if (abs(ms) < 12) "on the beat" else "${abs(ms).roundToInt()} ms ${if (ms < 0) "early" else "late"}", style = T.mono, color = MC.Ink)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().height(30.dp)) {
                    Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center).clip(RoundedCornerShape(99.dp)).background(MC.Line))
                    Box(Modifier.align(Alignment.Center).size(2.dp, 22.dp).background(MC.InkSoft))
                    val f = ((ms.coerceIn(-150.0, 150.0) + 150) / 300).toFloat()
                    Box(Modifier.offset(x = maxWidth * f - 9.dp).align(Alignment.CenterStart).size(18.dp).clip(CircleShape).background(if (abs(ms) < 25) MC.Mint else MC.Butter))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Early", style = T.small, color = MC.InkSoft); Text("On the beat", style = T.small, color = MC.InkSoft); Text("Late", style = T.small, color = MC.InkSoft)
                }
                Text(
                    when {
                        abs(ms) < 25 -> "Your timing is steady. Try the next speed up."
                        ms < 0 -> "You rush a little. Count the long notes all the way to their end."
                        else -> "You come in a little late. Look one note ahead while you play."
                    },
                    style = T.body, color = MC.InkSoft,
                )
            }
        }

        if (r.sectionAcc.isNotEmpty()) {
            MCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("By section", style = T.bodyStrong, color = MC.Ink)
                    Text("mastery ${(previousBest * 100).roundToInt()} → ${(maxOf(previousBest, r.mastery) * 100).roundToInt()}%", style = T.mono, color = MC.InkSoft)
                }
                Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    r.sectionAcc.entries.forEach { (k, v) ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text("${(v * 100).roundToInt()}", style = T.monoS, color = MC.Ink)
                            Box(Modifier.fillMaxWidth().fillMaxHeight(v.toFloat().coerceIn(0.05f, 1f) * 0.72f).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(if (v >= 0.85) MC.Mint else if (v >= 0.6) MC.Butter else MC.Right))
                            Text(k.substringBefore('#'), style = T.h3.copy(fontSize = T.small.fontSize), color = MC.Ink)
                        }
                    }
                }
                val worst = r.missedPitches.entries.sortedByDescending { it.value }.take(3)
                if (worst.isNotEmpty()) Text("Most missed: " + worst.joinToString { "${Pitch.name(it.key)} (${it.value})" }, style = T.small, color = MC.InkSoft)
            }
        }

        val weakest = r.sectionAcc.entries.minByOrNull { it.value }
        val weakSection = weakest?.let { e -> song.sections.getOrNull(e.key.substringAfter('#').toIntOrNull() ?: -1) }
        if (fromLesson) PrimaryButton("Continue the lesson", onContinueLesson, Modifier.fillMaxWidth(), icon = Ic.Next)
        if (weakSection != null && (weakest?.value ?: 1.0) < 0.85 && cfg.loop == null) {
            PrimaryButton("Loop section ${weakSection.name}", { onLoop(cfg.copy(loop = weakSection, autoSpeed = true)) }, Modifier.fillMaxWidth(), icon = Ic.Loop,
                color = if (fromLesson) MC.Ink else MC.Right)
        }
        val nextSpeed = if (pct >= 90) (r.speed + 0.05f).coerceAtMost(1f) else r.speed
        SoftButton(if (nextSpeed > r.speed) "Play again at ${(nextSpeed * 100).roundToInt()}%" else "Play again", {
            onAgain(cfg.copy(speed = nextSpeed, level = r.level))
        }, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            Mochi(if (pct >= 70) Mood.Cheer else Mood.Calm, Modifier.size(40.dp))
            Text(
                if (cfg.dynamic && r.level < 10) "Next run starts at level ${r.level}, with more of the song's notes." else "Every run teaches your fingers a little more.",
                style = T.small, color = MC.InkSoft,
            )
        }
    }
}

@Composable
private fun Stat(label: String, n: Int, dot: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MC.Surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text("$n", style = T.monoBig, color = MC.Ink)
        Text(label, style = T.small, color = MC.InkSoft, maxLines = 1)
    }
}
