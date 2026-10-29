package app.mochipiano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.mochipiano.course.Clef
import app.mochipiano.course.Mood
import app.mochipiano.music.Pitch
import app.mochipiano.ui.theme.Bricolage
import app.mochipiano.ui.theme.ClefFont
import app.mochipiano.ui.theme.GeistMono
import app.mochipiano.ui.theme.MC

// ---------------------------------------------------------------- keyboard geometry

class KeyGeo(val range: IntRange, val width: Float, val height: Float) {
    val whites: List<Int> = range.filter { !Pitch.isBlack(it) }
    val whiteW = width / whites.size.coerceAtLeast(1)
    val blackW = whiteW * 0.62f
    val blackH = height * 0.6f
    private val firstWhite = Pitch.whiteIndex(whites.firstOrNull() ?: 60)

    fun left(p: Int): Float =
        if (!Pitch.isBlack(p)) (Pitch.whiteIndex(p) - firstWhite) * whiteW
        else (Pitch.whiteIndex(p - 1) - firstWhite + 1) * whiteW - blackW / 2

    fun w(p: Int) = if (Pitch.isBlack(p)) blackW else whiteW
    fun center(p: Int) = left(p) + w(p) / 2

    fun hit(o: Offset): Int? {
        if (o.y < blackH) range.filter { Pitch.isBlack(it) }.firstOrNull { o.x >= left(it) && o.x <= left(it) + blackW }?.let { return it }
        val i = (o.x / whiteW).toInt()
        return whites.getOrNull(i)
    }
}

/**
 * The keyboard. [fill] colours keys, [fingers] draws finger badges, [labels] names white keys.
 * When [onKey] is set, tapping keys plays them like the piano would.
 */
@Composable
fun PianoKeys(
    range: IntRange,
    modifier: Modifier = Modifier,
    fill: Map<Int, Color> = emptyMap(),
    outline: Map<Int, Color> = emptyMap(),
    fingers: Map<Int, Int> = emptyMap(),
    fingerBg: Color = MC.RightSoft,
    fingerFg: Color = MC.RightText,
    labels: Set<Int> = emptySet(),
    solfege: Boolean = false,
    middleC: Boolean = false,
    held: Set<Int> = emptySet(),
    heldColor: Color = MC.Mint,
    whiteColor: Color = MC.Surface,
    blackColor: Color = MC.Ink,
    lineColor: Color = MC.KeyLine,
    aspect: Float = 2.2f,
    onKey: ((Int, Boolean) -> Unit)? = null,
) {
    val tm = rememberTextMeasurer()
    val base = modifier.fillMaxWidth().aspectRatio(aspect * (range.count { !Pitch.isBlack(it) } / 8f).coerceAtLeast(0.6f))
    val m = if (onKey != null) base.pointerInput(range) {
        detectTapGestures(onPress = { o ->
            val g = KeyGeo(range, size.width.toFloat(), size.height.toFloat())
            val p = g.hit(o)
            if (p != null) {
                onKey(p, true)
                tryAwaitRelease()
                onKey(p, false)
            }
        })
    } else base
    Canvas(m) {
        val g = KeyGeo(range, size.width, size.height)
        val r = CornerRadius(g.whiteW * 0.16f)
        g.whites.forEach { p ->
            val x = g.left(p)
            val c = when { p in held -> heldColor; else -> fill[p] ?: whiteColor }
            drawRoundRect(c, Offset(x + 1.5f, 0f), Size(g.whiteW - 3f, size.height), r)
            drawRoundRect(outline[p] ?: lineColor, Offset(x + 1.5f, 0f), Size(g.whiteW - 3f, size.height), r, style = Stroke(if (outline[p] != null) 5f else 3f))
        }
        range.filter { Pitch.isBlack(it) }.forEach { p ->
            val c = when { p in held -> heldColor; else -> fill[p] ?: blackColor }
            drawRoundRect(c, Offset(g.left(p), 0f), Size(g.blackW, g.blackH), CornerRadius(g.blackW * 0.18f))
        }
        val fs = (g.whiteW * 0.36f).coerceIn(18f, 46f)
        fingers.forEach { (p, f) ->
            if (p !in range || Pitch.isBlack(p)) return@forEach
            val cx = g.center(p)
            val cy = size.height * 0.72f
            drawCircle(fingerBg, fs * 0.72f, Offset(cx, cy))
            centeredText(tm, f.toString(), Offset(cx, cy), TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = (fs / density).sp, color = fingerFg))
        }
        labels.forEach { p ->
            if (p !in range || Pitch.isBlack(p)) return@forEach
            centeredText(tm, Pitch.display(Pitch.letter(p), solfege), Offset(g.center(p), size.height * 0.9f),
                TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = (fs * 0.95f / density).sp, color = MC.Ink))
        }
        if (middleC && 60 in range) drawCircle(MC.Left, g.whiteW * 0.12f, Offset(g.center(60), g.blackH + g.whiteW * 0.3f))
    }
}

fun DrawScope.centeredText(tm: TextMeasurer, s: String, c: Offset, style: TextStyle) {
    val l = tm.measure(s, style)
    drawText(l, topLeft = Offset(c.x - l.size.width / 2f, c.y - l.size.height / 2f))
}

// ---------------------------------------------------------------- staff

data class StaffNote(val pitch: Int, val dur: Double = 1.0, val color: Color = MC.Ink, val mark: Color? = null)

/** Draws one staff with notes. Sharps are shown with a ♯ drawn in front of the head. */
@Composable
fun StaffView(
    notes: List<StaffNote>,
    clef: Clef,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    highlightColor: Color = MC.RightSoft,
    letters: Boolean = false,
    solfege: Boolean = false,
    lineColor: Color = MC.Ink,
    aspect: Float = 2.6f,
) {
    val tm = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().aspectRatio(aspect)) {
        val rows = if (letters) 12f else 10f
        val gp = size.height / rows
        val top = gp * 2.6f
        val bottom = top + gp * 4
        val base = if (clef == Clef.Treble) 30 else 18
        val left = gp * 4.6f
        val n = notes.size
        val step = if (n == 0) 0f else ((size.width - left - gp) / n).coerceAtMost(gp * 5.5f)
        fun yOf(d: Int) = bottom - (d - base) * gp / 2

        if (highlight != null && highlight in notes.indices) {
            val x = left + highlight * step + step / 2
            drawRoundRect(highlightColor, Offset(x - step * 0.42f, top - gp * 2.2f), Size(step * 0.84f, gp * 8.4f), CornerRadius(gp * 0.9f))
        }
        for (i in 0 until 5) drawLine(lineColor.copy(alpha = 0.72f), Offset(0f, top + i * gp), Offset(size.width, top + i * gp), strokeWidth = gp * 0.1f)

        val glyph = if (clef == Clef.Treble) "𝄞" else "𝄢"
        val cl = tm.measure(glyph, TextStyle(fontFamily = ClefFont, fontSize = (gp * 4 / density / fontScale).sp, color = lineColor))
        drawText(cl, topLeft = Offset(gp * 0.3f, bottom - cl.firstBaseline))

        notes.forEachIndexed { i, nt ->
            val x = left + i * step + step / 2
            val d = Pitch.diatonic(nt.pitch)
            val y = yOf(d)
            var k = base - 2
            while (k >= d) { drawLine(lineColor, Offset(x - gp, yOf(k)), Offset(x + gp, yOf(k)), gp * 0.1f); k -= 2 }
            k = base + 10
            while (k <= d) { drawLine(lineColor, Offset(x - gp, yOf(k)), Offset(x + gp, yOf(k)), gp * 0.1f); k += 2 }
            val hw = gp * 0.58f
            val hh = gp * 0.42f
            rotate(-20f, Offset(x, y)) {
                if (nt.dur < 2) drawOval(nt.color, Offset(x - hw, y - hh), Size(hw * 2, hh * 2))
                else drawOval(nt.color, Offset(x - hw, y - hh), Size(hw * 2, hh * 2), style = Stroke(gp * 0.16f))
            }
            if (nt.dur < 4) {
                val up = d < base + 4
                val sx = if (up) x + hw * 0.92f else x - hw * 0.92f
                val ey = if (up) y - gp * 3.4f else y + gp * 3.4f
                drawLine(nt.color, Offset(sx, y), Offset(sx, ey), gp * 0.13f)
                if (nt.dur <= 0.5) {
                    val p = Path().apply { moveTo(sx, ey); quadraticBezierTo(sx + gp * 0.2f, ey + (if (up) gp else -gp) * 0.7f, sx + gp * 0.8f, ey + (if (up) gp else -gp) * 1.1f) }
                    drawPath(p, nt.color, style = Stroke(gp * 0.16f, cap = StrokeCap.Round))
                }
            }
            if (nt.dur == 1.5 || nt.dur == 3.0) {
                val onLine = (d - base) % 2 == 0
                drawCircle(nt.color, gp * 0.16f, Offset(x + gp * 1.0f, if (onLine) y - gp / 2 else y))
            }
            if (Pitch.isSharp(nt.pitch)) drawSharp(Offset(x - gp * 1.35f, y), gp, nt.color)
            nt.mark?.let { mk -> drawCircle(mk, gp * 0.45f, Offset(x, top - gp * 1.6f)) }
            if (letters) centeredText(tm, Pitch.displayPitch(nt.pitch, solfege), Offset(x, size.height - gp * 1.0f),
                TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = (gp * 1.15f / density).sp, color = if (i == highlight) MC.Right else MC.InkSoft))
        }
    }
}

fun DrawScope.drawSharp(c: Offset, gp: Float, color: Color) {
    val w = gp * 0.5f
    drawLine(color, Offset(c.x - w * 0.35f, c.y - gp * 0.9f), Offset(c.x - w * 0.35f, c.y + gp * 0.8f), gp * 0.1f)
    drawLine(color, Offset(c.x + w * 0.35f, c.y - gp * 1.0f), Offset(c.x + w * 0.35f, c.y + gp * 0.7f), gp * 0.1f)
    drawLine(color, Offset(c.x - w, c.y - gp * 0.15f), Offset(c.x + w, c.y - gp * 0.4f), gp * 0.2f)
    drawLine(color, Offset(c.x - w, c.y + gp * 0.4f), Offset(c.x + w, c.y + gp * 0.15f), gp * 0.2f)
}

// ---------------------------------------------------------------- rhythm strip

/** Notes spaced by duration on one line. Negative durations are rests. [results] colours a dot under each note. */
@Composable
fun RhythmStrip(pattern: List<Double>, modifier: Modifier = Modifier, current: Int = -1, results: List<Color?> = emptyList()) {
    val tm = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().aspectRatio(3.6f)) {
        val total = pattern.sumOf { kotlin.math.abs(it) }.coerceAtLeast(1.0)
        val pad = size.width * 0.04f
        val unit = (size.width - pad * 2) / total.toFloat()
        val y = size.height * 0.46f
        val gp = size.height * 0.12f
        drawLine(MC.Ink.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), gp * 0.12f)
        var t = 0.0
        pattern.forEachIndexed { i, d ->
            val ad = kotlin.math.abs(d)
            if (t > 0 && t % 4.0 == 0.0) { val bx = pad + t.toFloat() * unit - unit * 0.15f; drawLine(MC.Ink.copy(alpha = 0.35f), Offset(bx, y - gp * 2), Offset(bx, y + gp * 2), gp * 0.12f) }
            val x = pad + t.toFloat() * unit + unit * 0.3f
            val col = if (i == current) MC.Right else MC.Ink
            if (d > 0) {
                rotate(-20f, Offset(x, y)) {
                    if (ad < 2) drawOval(col, Offset(x - gp * 0.8f, y - gp * 0.58f), Size(gp * 1.6f, gp * 1.16f))
                    else drawOval(col, Offset(x - gp * 0.8f, y - gp * 0.58f), Size(gp * 1.6f, gp * 1.16f), style = Stroke(gp * 0.2f))
                }
                if (ad < 4) drawLine(col, Offset(x + gp * 0.74f, y), Offset(x + gp * 0.74f, y - gp * 3.6f), gp * 0.16f)
            } else {
                when (ad) {
                    1.0 -> {
                        val p = Path().apply { moveTo(x - gp * 0.3f, y - gp * 1.8f); lineTo(x + gp * 0.4f, y - gp * 0.9f); lineTo(x - gp * 0.2f, y - gp * 0.2f); lineTo(x + gp * 0.5f, y + gp * 0.7f); quadraticBezierTo(x - gp * 0.4f, y + gp * 0.3f, x - gp * 0.1f, y + gp * 1.2f) }
                        drawPath(p, col, style = Stroke(gp * 0.24f, cap = StrokeCap.Round))
                    }
                    2.0 -> drawRect(col, Offset(x - gp * 0.75f, y - gp * 0.6f), Size(gp * 1.5f, gp * 0.6f))
                    else -> drawRect(col, Offset(x - gp * 0.75f, y), Size(gp * 1.5f, gp * 0.6f))
                }
            }
            val syl = if (d > 0) when (ad) { 1.0 -> "ta"; 2.0 -> "ta-a"; 4.0 -> "ta-a-a-a"; 0.5 -> "ti"; else -> "ta" } else "shh"
            centeredText(tm, syl, Offset(x, y + gp * 2.6f), TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = (gp * 0.95f / density).sp, color = if (i == current) MC.Right else MC.InkSoft))
            results.getOrNull(i)?.let { drawCircle(it, gp * 0.45f, Offset(x, y + gp * 3.7f)) }
            t += ad
        }
    }
}

// ---------------------------------------------------------------- mascot

/** Mochi, drawn on a 64-unit grid. */
@Composable
fun Mochi(mood: Mood, modifier: Modifier = Modifier) {
    val body = remember {
        Path().apply {
            moveTo(8f, 40f); cubicTo(8f, 26f, 18f, 16f, 32f, 16f); cubicTo(46f, 16f, 56f, 26f, 56f, 40f)
            cubicTo(56f, 50f, 47f, 56f, 32f, 56f); cubicTo(17f, 56f, 8f, 50f, 8f, 40f); close()
        }
    }
    Canvas(modifier.aspectRatio(1f)) {
        val s = size.minDimension / 64f
        scale(s, s, pivot = Offset.Zero) {
            val ink = MC.Ink
            drawPath(body, MC.MochiBody)
            val stem = Path().apply { moveTo(37f, 18f); lineTo(37f, 6f); cubicTo(42f, 7f, 44f, 10f, 45f, 13f) }
            drawPath(stem, ink, style = Stroke(2.6f, cap = StrokeCap.Round))
            rotate(-20f, Offset(33.6f, 18.4f)) { drawOval(ink, Offset(33.6f - 4.3f, 18.4f - 3.3f), Size(8.6f, 6.6f)) }
            drawOval(MC.Blush, Offset(14f, 41.6f), Size(8f, 4.8f))
            drawOval(MC.Blush, Offset(42f, 41.6f), Size(8f, 4.8f))
            when (mood) {
                Mood.Calm -> {
                    drawCircle(ink, 2.8f, Offset(24f, 38f)); drawCircle(ink, 2.8f, Offset(40f, 38f))
                    drawPath(Path().apply { moveTo(29f, 44f); quadraticBezierTo(32f, 47f, 35f, 44f) }, ink, style = Stroke(2.4f, cap = StrokeCap.Round))
                }
                Mood.Listening -> {
                    drawCircle(ink, 2.8f, Offset(24f, 38f))
                    drawLine(ink, Offset(37f, 38f), Offset(43f, 38f), 2.8f, cap = StrokeCap.Round)
                    drawPath(Path().apply { moveTo(29f, 45f); quadraticBezierTo(32f, 43.5f, 35f, 45f) }, ink, style = Stroke(2.4f, cap = StrokeCap.Round))
                }
                Mood.Cheer -> {
                    drawPath(Path().apply { moveTo(20f, 39f); quadraticBezierTo(24f, 34f, 28f, 39f); moveTo(36f, 39f); quadraticBezierTo(40f, 34f, 44f, 39f) }, ink, style = Stroke(2.6f, cap = StrokeCap.Round))
                    drawPath(Path().apply { moveTo(27f, 44f); quadraticBezierTo(32f, 50f, 37f, 44f); close() }, MC.Right)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- hand

/** A right hand with finger numbers on the tips. */
@Composable
fun HandView(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(modifier.aspectRatio(210f / 230f)) {
        val s = size.width / 210f
        scale(s, s, pivot = Offset.Zero) {
            val skin = MC.MochiBody
            val line = MC.Ink
            val palm = Path().apply { moveTo(40f, 130f); cubicTo(40f, 112f, 60f, 108f, 100f, 108f); cubicTo(150f, 108f, 186f, 110f, 186f, 136f); lineTo(182f, 190f); cubicTo(180f, 214f, 160f, 224f, 120f, 224f); cubicTo(76f, 224f, 54f, 214f, 48f, 192f); close() }
            drawPath(palm, skin); drawPath(palm, line, style = Stroke(2.5f))
            val thumb = Path().apply { moveTo(28f, 150f); cubicTo(10f, 128f, 6f, 104f, 18f, 96f); cubicTo(30f, 88f, 44f, 104f, 56f, 124f); close() }
            drawPath(thumb, skin); drawPath(thumb, line, style = Stroke(2.5f))
            listOf(Triple(58f, 36f, 96f), Triple(92f, 20f, 112f), Triple(126f, 30f, 102f)).forEach { (x, y, h) ->
                drawRoundRect(skin, Offset(x, y), Size(28f, h), CornerRadius(14f)); drawRoundRect(line, Offset(x, y), Size(28f, h), CornerRadius(14f), style = Stroke(2.5f))
            }
            drawRoundRect(skin, Offset(158f, 58f), Size(25f, 78f), CornerRadius(12.5f)); drawRoundRect(line, Offset(158f, 58f), Size(25f, 78f), CornerRadius(12.5f), style = Stroke(2.5f))
            val tips = listOf(22f to 104f, 72f to 56f, 106f to 40f, 140f to 50f, 170f to 76f)
            tips.forEachIndexed { i, (x, y) ->
                drawCircle(MC.Surface, 13f, Offset(x, y)); drawCircle(line, 13f, Offset(x, y), style = Stroke(2f))
            }
        }
        val tips = listOf(22f to 104f, 72f to 56f, 106f to 40f, 140f to 50f, 170f to 76f)
        tips.forEachIndexed { i, (x, y) ->
            translate(0f, 0f) {
                centeredText(tm, (i + 1).toString(), Offset(x * s, y * s), TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = (15f * s / density).sp, color = MC.Ink))
            }
        }
    }
}
