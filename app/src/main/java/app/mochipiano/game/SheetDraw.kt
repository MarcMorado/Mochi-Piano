package app.mochipiano.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.mochipiano.music.Hand
import app.mochipiano.music.Pitch
import app.mochipiano.ui.components.drawSharp
import app.mochipiano.ui.theme.Bricolage
import app.mochipiano.ui.theme.ClefFont
import app.mochipiano.ui.theme.GeistMono
import app.mochipiano.ui.theme.MC
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private val Paper = Color(0xFFFBFAFD)
private val PaperInk = Color(0xFF1C1530)

/**
 * The score view: a grand staff (or one staff for one-hand songs) that scrolls past a fixed cursor.
 * Notes are spaced by time so what she sees lines up with when to play it.
 * Returns the cursor's x position so feedback can be drawn next to it.
 */
fun DrawScope.drawSheet(
    engine: GameEngine,
    left: Float, top: Float, width: Float, height: Float,
    tm: TextMeasurer, cache: HashMap<String, TextLayoutResult>,
    solfege: Boolean, showFingers: Boolean,
): Float {
    val song = engine.song
    val beat = engine.beat
    val hands = song.hands
    val grand = hands.size > 1
    val singleClefBass = !grand && (song.notes.map { it.pitch }.average() < 57)
    val g = height / if (grand) 22f else 12f
    val trebleBottom = top + g * 8f
    val bassBottom = if (grand) trebleBottom + g * 10f else top + g * 8f
    val firstStaffTop = trebleBottom - g * 4
    val lastStaffBottom = if (grand) bassBottom else trebleBottom

    drawRoundRect(Paper, Offset(left, top), Size(width, height), CornerRadius(g * 1.4f))

    val panel = g * 6.2f
    val cursorX = left + panel + (width - panel) * 0.24f
    val minDur = song.notes.minOfOrNull { it.dur } ?: 1.0
    val ppb = g * when { minDur < 0.5 -> 9.5f; minDur < 1.0 -> 7f; else -> 5.4f }

    fun staffOf(h: Hand): Boolean = if (grand) h == Hand.R else !singleClefBass // true = treble
    fun yOf(pitch: Int, treble: Boolean): Float {
        val base = if (treble) 30 else 18
        val bottom = if (treble || !grand) (if (grand) trebleBottom else trebleBottom) else bassBottom
        return bottom - (Pitch.diatonic(pitch) - base) * g / 2
    }
    val bottomOf = { treble: Boolean -> if (grand && !treble) bassBottom else trebleBottom }

    // Staff lines.
    val staves = if (grand) listOf(true, false) else listOf(!singleClefBass)
    staves.forEach { treble ->
        val b = bottomOf(treble)
        for (i in 0 until 5) drawLine(PaperInk.copy(alpha = 0.7f), Offset(left + g, b - i * g), Offset(left + width - g, b - i * g), g * 0.09f)
    }

    // Cursor band.
    drawRect(MC.StageRight.copy(alpha = 0.10f), Offset(cursorX - g * 1.3f, firstStaffTop - g * 2.5f), Size(g * 2.6f, lastStaffBottom - firstStaffTop + g * 5f))

    clipRect(left + panel, top, left + width - g * 0.5f, top + height) {
        // Bar lines and numbers.
        val firstBar = floor((beat - (cursorX - left) / ppb) / song.barBeats).toInt().coerceAtLeast(0)
        val lastBar = ceil((beat + (left + width - cursorX) / ppb) / song.barBeats).toInt()
        for (b in firstBar..lastBar) {
            val x = cursorX + ((b * song.barBeats - beat) * ppb).toFloat() - g * 0.9f
            drawLine(PaperInk.copy(alpha = 0.55f), Offset(x, firstStaffTop), Offset(x, lastStaffBottom), g * 0.1f)
            if (b > 0) {
                val lab = cache.getOrPut("bar$b") { tm.measure("${b + 1}", TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MC.InkSoft)) }
                drawText(lab, topLeft = Offset(x + g * 0.3f, firstStaffTop - g * 2.2f))
            }
        }

        // Notes, grouped into chords by hand and start time so a chord shares one stem.
        val visible = engine.notes.filter { pn ->
            val x = cursorX + ((pn.n.start - beat) * ppb).toFloat()
            x > left + panel - g * 3 && x < left + width + g * 2
        }
        val targets = engine.targetsNow().toSet()
        visible.groupBy { it.n.hand to it.n.start }.forEach { (key, chord) ->
            val (hand, start) = key
            val treble = staffOf(hand)
            val staffBottom = bottomOf(treble)
            val base = if (treble) 30 else 18
            val x = cursorX + ((start - beat) * ppb).toFloat()
            val dur = chord.maxOf { it.n.dur }
            val ys = chord.map { yOf(it.n.pitch, treble) }
            val ds = chord.map { Pitch.diatonic(it.n.pitch) }
            val hw = g * 0.6f
            val hh = g * 0.43f

            // Ledger lines.
            ds.forEach { d ->
                var k = base - 2
                while (k >= d) { val ly = staffBottom - (k - base) * g / 2; drawLine(PaperInk, Offset(x - g * 1.05f, ly), Offset(x + g * 1.05f, ly), g * 0.09f); k -= 2 }
                k = base + 10
                while (k <= d) { val ly = staffBottom - (k - base) * g / 2; drawLine(PaperInk, Offset(x - g * 1.05f, ly), Offset(x + g * 1.05f, ly), g * 0.09f); k += 2 }
            }

            val chordColor = { pn: PlayNote ->
                val handColor = if (pn.n.hand == Hand.R) MC.Right else MC.Left
                when {
                    !pn.mine -> PaperInk.copy(alpha = 0.35f)
                    pn.judge == Judge.Perfect || pn.judge == Judge.Good -> MC.Mint
                    pn.judge == Judge.Off -> MC.Butter
                    pn.judge == Judge.Miss -> Color(0xFFB8AFC6)
                    pn in targets -> handColor
                    !pn.required -> PaperInk.copy(alpha = 0.28f)
                    else -> PaperInk
                }
            }
            val stemColor = chordColor(chord.first())

            chord.forEachIndexed { i, pn ->
                val y = ys[i]
                val c = chordColor(pn)
                // Neighbouring notes a step apart sit on opposite sides of the stem.
                val clash = chord.any { o -> o !== pn && Pitch.diatonic(o.n.pitch) == ds[i] - 1 }
                val hx = if (clash) x + hw * 1.9f else x
                rotate(-20f, Offset(hx, y)) {
                    if (dur < 1.75) drawOval(c, Offset(hx - hw, y - hh), Size(hw * 2, hh * 2))
                    else drawOval(c, Offset(hx - hw, y - hh), Size(hw * 2, hh * 2), style = Stroke(g * 0.17f))
                }
                if (Pitch.isSharp(pn.n.pitch)) drawSharp(Offset(hx - g * 1.45f, y), g, c)
                if (abs(dur - 1.5) < 0.01 || abs(dur - 3.0) < 0.01 || abs(dur - 0.75) < 0.01) {
                    val onLine = (ds[i] - base) % 2 == 0
                    drawCircle(c, g * 0.16f, Offset(hx + g * 1.05f, if (onLine) y - g / 2 else y))
                }
                if (showFingers && pn.mine && pn.n.finger > 0) {
                    val lab = cache.getOrPut("f${pn.n.finger}") { tm.measure("${pn.n.finger}", TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MC.InkSoft)) }
                    val fy = if (hand == Hand.R) staffBottom - g * 4 - g * 1.6f - lab.size.height else staffBottom + g * 1.4f
                    drawText(lab, topLeft = Offset(hx - lab.size.width / 2f, minOf(fy, if (hand == Hand.R) y - g * 1.2f - lab.size.height else fy).coerceAtLeast(top)))
                }
                if (pn in targets) {
                    val lab = cache.getOrPut("n${pn.n.pitch}$solfege") { tm.measure(Pitch.displayPitch(pn.n.pitch, solfege), TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (pn.n.hand == Hand.R) MC.Right else MC.Left)) }
                    drawText(lab, topLeft = Offset(hx + g * 1.1f, y - lab.size.height / 2f))
                }
            }

            // Stem and flags.
            if (dur < 3.75) {
                val mid = base + 4
                val up = ds.average() < mid
                val sx = if (up) x + hw * 0.92f else x - hw * 0.92f
                val yTop = ys.minOrNull()!!
                val yBot = ys.maxOrNull()!!
                val from = if (up) yBot else yTop
                val to = if (up) yTop - g * 3.4f else yBot + g * 3.4f
                drawLine(stemColor, Offset(sx, from), Offset(sx, to), g * 0.13f)
                val flags = when { dur <= 0.26 -> 2; dur <= 0.76 -> 1; else -> 0 }
                for (f in 0 until flags) {
                    val fy = to + (if (up) 1 else -1) * f * g * 0.8f
                    val p = Path().apply { moveTo(sx, fy); quadraticBezierTo(sx + g * 0.3f, fy + (if (up) 1 else -1) * g * 0.8f, sx + g * 0.95f, fy + (if (up) 1 else -1) * g * 1.4f) }
                    drawPath(p, stemColor, style = Stroke(g * 0.17f, cap = StrokeCap.Round))
                }
            }
        }
    }

    // Fixed left panel: clefs and time signature, covering notes that have passed.
    drawRect(Paper, Offset(left + g * 0.6f, firstStaffTop - g * 3), Size(panel - g * 0.6f, lastStaffBottom - firstStaffTop + g * 6))
    staves.forEach { treble ->
        val b = bottomOf(treble)
        for (i in 0 until 5) drawLine(PaperInk.copy(alpha = 0.7f), Offset(left + g, b - i * g), Offset(left + panel, b - i * g), g * 0.09f)
        val glyph = if (treble) "𝄞" else "𝄢"
        val cl = cache.getOrPut("clef$treble$g") { tm.measure(glyph, TextStyle(fontFamily = ClefFont, fontSize = (g * 4 / density / fontScale).sp, color = PaperInk)) }
        drawText(cl, topLeft = Offset(left + g * 1.3f, b - cl.firstBaseline))
        val parts = song.timeSig.split('/')
        if (parts.size == 2) {
            val st = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = (g * 2.1f / density / fontScale).sp, color = PaperInk)
            val n1 = cache.getOrPut("ts1${parts[0]}$g") { tm.measure(parts[0], st) }
            val n2 = cache.getOrPut("ts2${parts[1]}$g") { tm.measure(parts[1], st) }
            val tx = left + panel - g * 1.6f
            drawText(n1, topLeft = Offset(tx - n1.size.width / 2f, b - g * 4 - g * 0.1f))
            drawText(n2, topLeft = Offset(tx - n2.size.width / 2f, b - g * 2 - g * 0.1f))
        }
    }
    if (grand) drawLine(PaperInk, Offset(left + g, trebleBottom - g * 4), Offset(left + g, bassBottom), g * 0.12f)

    // Cursor.
    drawLine(MC.Right, Offset(cursorX, firstStaffTop - g * 2.5f), Offset(cursorX, lastStaffBottom + g * 2.5f), g * 0.16f)
    return cursorX
}
