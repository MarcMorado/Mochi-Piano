package app.mochipiano.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mochipiano.audio.Sound
import app.mochipiano.game.GameConfig
import app.mochipiano.game.GameEngine
import app.mochipiano.game.GameResult
import app.mochipiano.game.Judge
import app.mochipiano.game.drawSheet
import app.mochipiano.record.Capture
import app.mochipiano.record.LastTake
import app.mochipiano.data.Store
import app.mochipiano.midi.Link
import app.mochipiano.midi.MidiHub
import app.mochipiano.music.Hand
import app.mochipiano.music.Pitch
import app.mochipiano.ui.components.Bar
import app.mochipiano.ui.components.Chip
import app.mochipiano.ui.components.Ic
import app.mochipiano.ui.components.KeyGeo
import app.mochipiano.ui.components.PrimaryButton
import app.mochipiano.ui.components.RoundIcon
import app.mochipiano.ui.components.SoftButton
import app.mochipiano.ui.theme.Bricolage
import app.mochipiano.ui.theme.GeistMono
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T
import kotlinx.coroutines.isActive
import java.text.NumberFormat
import kotlin.math.ceil

@Composable
fun GameScreen(cfg: GameConfig, solfege: Boolean, onQuit: () -> Unit, onDone: (GameResult) -> Unit) {
    val engine = remember(cfg) { GameEngine(cfg) }
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val conn by MidiHub.state.collectAsStateWithLifecycle()
    val held by MidiHub.held.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        val prev = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val ctl = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = prev
            Sound.stopAll()
        }
    }

    LaunchedEffect(engine) {
        while (isActive) {
            withFrameNanos { engine.tick(it) }
            if (engine.finished) { onDone(engine.result()); break }
        }
    }
    LaunchedEffect(engine) { MidiHub.events.collect { engine.onNote(it) } }
    LaunchedEffect(engine) {
        val cap = Capture()
        LastTake.capture = cap
        LastTake.title = cfg.song.title
        MidiHub.raw.collect { cap.add(it) }
    }

    BackHandler { engine.paused = !engine.paused }

    val range = remember(cfg) { Pitch.octaveRange(cfg.song.pitchRange.first, cfg.song.pitchRange.last, 2) }
    val tm = rememberTextMeasurer()
    val labelCache = remember { HashMap<String, TextLayoutResult>() }
    val nf = remember { NumberFormat.getIntegerInstance() }

    Box(Modifier.fillMaxSize().background(MC.Stage)) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(range) {
                detectTapGestures(onPress = { o ->
                    val kbTop = size.height * (if (cfg.sheet) 0.80f else 0.70f)
                    if (o.y < kbTop) return@detectTapGestures
                    val pad = 16.dp.toPx()
                    val g = KeyGeo(range, size.width - pad * 2, size.height - kbTop)
                    val p = g.hit(Offset(o.x - pad, o.y - kbTop)) ?: return@detectTapGestures
                    if (!MidiHub.canSendToPiano) Sound.note(p, 500)
                    MidiHub.inject(p, true)
                    tryAwaitRelease()
                    MidiHub.inject(p, false)
                })
            }
        ) {
            engine.frame // redraw every frame
            val now = System.nanoTime()
            val pad = 16.dp.toPx()
            val hudH = (if (cfg.sheet) 92 else 60).dp.toPx()
            val kbTop = size.height * (if (cfg.sheet) 0.80f else 0.70f)
            val hitY = kbTop - 3.dp.toPx()
            val g = KeyGeo(range, size.width - pad * 2, size.height - kbTop)
            val spb = engine.secPerBeat
            val lookBeats = (2.8 / spb)
            val ppb = ((hitY - hudH) / lookBeats).toFloat()
            val beat = engine.beat

            val targets = engine.targetsNow()
            var cursorX = size.width / 2
            if (cfg.sheet) {
                cursorX = drawSheet(engine, pad, hudH + 4.dp.toPx(), size.width - pad * 2, kbTop - hudH - 12.dp.toPx(), tm, labelCache, solfege, Store.data.settings.letters)
            } else {
                // Lanes.
                g.whites.forEachIndexed { i, p ->
                    val x = pad + i * g.whiteW
                    drawLine(Color.White.copy(alpha = if (Pitch.pc(p) == 0) 0.13f else 0.045f), Offset(x, hudH), Offset(x, hitY), 1.dp.toPx())
                }
                targets.forEach { t ->
                    val c = if (t.n.hand == Hand.R) MC.StageRight else MC.StageLeft
                    drawRect(c.copy(alpha = 0.07f), Offset(pad + g.left(t.n.pitch), hudH), Size(g.w(t.n.pitch), hitY - hudH))
                }

                // Notes.
                val inset = 3.dp.toPx()
                val r = CornerRadius(8.dp.toPx())
                engine.notes.forEach { pn ->
                    val n = pn.n
                    if (n.start + n.dur < beat - 0.2 || n.start > beat + lookBeats + 0.5) return@forEach
                    if (n.pitch !in range) return@forEach
                    val x = pad + g.left(n.pitch) + inset
                    val nw = g.w(n.pitch) - inset * 2
                    val yBottom = hitY - ((n.start - beat) * ppb).toFloat()
                    val yTop = yBottom - (n.dur * ppb).toFloat() + inset
                    val top = maxOf(yTop, hudH)
                    val bottom = minOf(yBottom, hitY)
                    if (bottom - top < 2f) return@forEach
                    val hand = if (n.hand == Hand.R) MC.StageRight else MC.StageLeft
                    val color = when {
                        !pn.mine -> MC.StageMuted.copy(alpha = 0.28f)
                        pn.judge == Judge.Miss -> Color(0xFF4A4060)
                        pn.judge != null -> lerpWhite(hand, 0.5f)
                        !pn.required -> hand.copy(alpha = 0.18f)
                        else -> hand
                    }
                    drawRoundRect(color, Offset(x, top), Size(nw, bottom - top), r)
                    if (pn.mine && !pn.required && pn.judge == null) drawRoundRect(hand.copy(alpha = 0.6f), Offset(x, top), Size(nw, bottom - top), r, style = Stroke(1.5.dp.toPx()))
                    if (pn.mine && pn.required && bottom - top > 26.dp.toPx() && nw > 22.dp.toPx()) {
                        val key = "${n.pitch}:${n.finger}:${n.hand}:$solfege"
                        val lab = labelCache.getOrPut(key) {
                            tm.measure(Pitch.displayPitch(n.pitch, solfege) + (if (n.finger > 0) " ${n.finger}" else ""),
                                TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF1A0A14)))
                        }
                        if (lab.size.width < nw) drawText(lab, topLeft = Offset(x + (nw - lab.size.width) / 2, top + 5.dp.toPx()))
                    }
                }

                // Hit line.
                drawLine(MC.StageRight.copy(alpha = 0.18f), Offset(pad, hitY), Offset(size.width - pad, hitY), 8.dp.toPx())
                drawLine(Color(0xFFFFD1E0), Offset(pad, hitY), Offset(size.width - pad, hitY), 2.dp.toPx())

            }

            // Keyboard.
            val targetPitches = targets.associate { it.n.pitch to it.n.hand }
            val strayOn = now - engine.strayNanos < 220_000_000L
            g.whites.forEach { p ->
                val x = pad + g.left(p)
                val c = when {
                    p in held -> targetPitches[p]?.let { if (it == Hand.R) MC.StageRight else MC.StageLeft } ?: if (strayOn && p == engine.strayPitch) Color(0xFF6B5A80) else MC.StageRight.copy(alpha = 0.75f)
                    else -> MC.StageKey
                }
                drawRoundRect(c, Offset(x + 1.5f, kbTop), Size(g.whiteW - 3f, size.height - kbTop - 2f), CornerRadius(6.dp.toPx()))
                targetPitches[p]?.let { h -> if (p !in held) drawRoundRect((if (h == Hand.R) MC.StageRight else MC.StageLeft).copy(alpha = 0.9f), Offset(x + 3f, kbTop + 2f), Size(g.whiteW - 6f, size.height - kbTop - 6f), CornerRadius(6.dp.toPx()), style = Stroke(3.dp.toPx())) }
                if (Pitch.pc(p) == 0) {
                    val key = "c$p"
                    val lab = labelCache.getOrPut(key) { tm.measure(Pitch.name(p), TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF8E86A0))) }
                    drawText(lab, topLeft = Offset(x + (g.whiteW - lab.size.width) / 2, size.height - lab.size.height - 6.dp.toPx()))
                }
            }
            range.filter { Pitch.isBlack(it) }.forEach { p ->
                val c = when {
                    p in held -> targetPitches[p]?.let { if (it == Hand.R) MC.StageRight else MC.StageLeft } ?: MC.StageRight.copy(alpha = 0.75f)
                    targetPitches.containsKey(p) -> (if (targetPitches[p] == Hand.R) MC.StageRight else MC.StageLeft).copy(alpha = 0.55f)
                    else -> MC.StageBlack
                }
                drawRoundRect(c, Offset(pad + g.left(p), kbTop), Size(g.blackW, g.blackH), CornerRadius(5.dp.toPx()))
            }

            // Feedback text.
            val centerY = if (cfg.sheet) hudH + 26.dp.toPx() else hudH + (hitY - hudH) * 0.38f
            val centerX = if (cfg.sheet) cursorX else size.width / 2
            if (beat < (cfg.loop?.start ?: 0.0)) {
                val left = ceil((cfg.loop?.start ?: 0.0) - beat).toInt().coerceAtLeast(1)
                val l = tm.measure(left.toString(), TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 72.sp, color = MC.StageRight))
                drawText(l, topLeft = Offset((size.width - l.size.width) / 2, centerY - l.size.height / 2))
            } else {
                val age = (now - engine.lastJudgeNanos) / 1e9
                val j = engine.lastJudge
                if (j != null && age < 0.7) {
                    val (txt, col) = when (j) {
                        Judge.Perfect -> "Perfect" to MC.Butter
                        Judge.Good -> "Good" to MC.Good
                        Judge.Off -> "A bit off" to MC.Butter.copy(alpha = 0.8f)
                        Judge.Miss -> "Missed" to MC.StageRight
                    }
                    val a = (1 - age / 0.7).toFloat().coerceIn(0f, 1f)
                    val l = tm.measure(txt, TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = if (cfg.sheet) 22.sp else 34.sp, color = col.copy(alpha = a * col.alpha)))
                    drawText(l, topLeft = Offset(centerX - l.size.width / 2, centerY - l.size.height / 2))
                    if (engine.combo >= 4 && j != Judge.Miss) {
                        val c2 = tm.measure("${engine.combo} IN A ROW", TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MC.StageMuted.copy(alpha = a)))
                        drawText(c2, topLeft = Offset(centerX - c2.size.width / 2, centerY + l.size.height / 2))
                    }
                }
                if (engine.waiting) {
                    val l = tm.measure("Play the glowing key", TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MC.StageMuted))
                    drawText(l, topLeft = Offset((size.width - l.size.width) / 2, hitY - l.size.height - 10.dp.toPx()))
                }
            }
        }

        Hud(engine, cfg, conn.link == Link.Connected, conn.name, nf) { engine.paused = true }

        if (engine.paused) {
            Box(Modifier.fillMaxSize().background(Color(0xCC0E0A16)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.widthIn(max = 360.dp).clip(RoundedCornerShape(24.dp)).background(MC.Stage2).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Paused", style = T.title, color = MC.StageText)
                    Text(cfg.song.title, style = T.body, color = MC.StageMuted)
                    PrimaryButton("Resume", { engine.paused = false }, Modifier.fillMaxWidth(), icon = Ic.Play)
                    SoftButton("Start over", { engine.restart() }, Modifier.fillMaxWidth(), bg = MC.StageLine)
                    SoftButton(if (engine.judgedCount > 0) "Finish and see results" else "Leave", {
                        if (engine.judgedCount > 0) onDone(engine.result()) else onQuit()
                    }, Modifier.fillMaxWidth(), bg = MC.StageLine)
                }
            }
        }
    }
}

private fun lerpWhite(c: Color, t: Float) = Color(c.red + (1 - c.red) * t, c.green + (1 - c.green) * t, c.blue + (1 - c.blue) * t, c.alpha)

@Composable
private fun Hud(engine: GameEngine, cfg: GameConfig, connected: Boolean, pianoName: String, nf: NumberFormat, onPause: () -> Unit) {
    engine.frame // recompose with the clock
    val song = engine.song
    val sec = song.sectionAt(engine.beat.coerceAtLeast(0.0))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoundIcon(Ic.Pause, "Pause", onPause, bg = MC.Stage2, tint = MC.StageText, size = 40.dp)
            Column(Modifier.width(170.dp)) {
                Text(song.title, style = T.bodyStrong, color = MC.StageText, maxLines = 1)
                Text(
                    ((sec?.name?.let { "$it · " } ?: "") + "BAR ${song.barOf(engine.beat.coerceAtLeast(0.0)).coerceAtMost(song.bars)} / ${song.bars}").uppercase(),
                    style = T.monoS, color = MC.StageMuted,
                )
            }
            Bar(engine.progress, MC.StageRight, Modifier.weight(1f), track = MC.StageLine)
            Column(horizontalAlignment = Alignment.End) {
                Text(nf.format(engine.score), style = T.mono.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = MC.StageText)
                Text("SCORE", style = T.monoS.copy(fontSize = 10.sp), color = MC.StageMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${Math.round(engine.accuracy * 100)}%", style = T.mono.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = MC.StageText)
                Text("ACCURACY", style = T.monoS.copy(fontSize = 10.sp), color = MC.StageMuted)
            }
            val mult = 1 + (engine.combo.coerceAtMost(24) / 8)
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (mult > 1) MC.Butter else MC.Stage2).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("×$mult", style = T.h3, color = if (mult > 1) MC.Ink else MC.StageMuted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(54.dp))
            Chip("${Math.round(engine.speed * 100)}% · ${Math.round(song.bpm * engine.speed)} BPM", bg = MC.Stage2, fg = Color(0xFFD9D2E6), style = T.monoS)
            Chip(
                when { cfg.hands.size == 2 -> "BOTH"; Hand.R in cfg.hands -> "RIGHT"; else -> "LEFT" }, bg = MC.Stage2, fg = Color(0xFFD9D2E6), style = T.monoS,
                dot = if (cfg.hands.size == 2) null else if (Hand.R in cfg.hands) MC.StageRight else MC.StageLeft,
            )
            if (cfg.dynamic) Chip("LEVEL ${engine.level}" + if (System.nanoTime() - engine.levelChangedNanos < 2_500_000_000L) " ↑" else "", bg = MC.Stage2, fg = MC.Butter, style = T.monoS)
            if (cfg.wait) Chip("WAIT MODE", bg = MC.Stage2, fg = Color(0xFFD9D2E6), style = T.monoS)
            if (cfg.loop != null) Chip("LOOP ${cfg.loop.name} · ${engine.loops}×", bg = MC.Stage2, fg = Color(0xFFD9D2E6), style = T.monoS)
            Spacer(Modifier.weight(1f))
            Chip(if (connected) pianoName.ifBlank { "Piano" }.uppercase().take(12) else "TAP KEYS", bg = MC.Stage2, fg = Color(0xFFD9D2E6), style = T.monoS, dot = if (connected) MC.Mint else MC.Butter)
        }
    }
}
