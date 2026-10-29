package app.mochipiano.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke icons drawn on a 24-unit grid, matching the design canvas. */
object Ic {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy} a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

    private fun stroke(name: String, vararg d: String, width: Float = 2.2f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            d.forEach {
                addPath(
                    pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.Black),
                    strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun filled(name: String, vararg d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            d.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    val Back = stroke("back", "M15 5l-7 7 7 7", width = 2.6f)
    val Next = stroke("next", "M9 5l7 7-7 7", width = 2.6f)
    val Close = stroke("close", "M6 6l12 12M18 6L6 18", width = 2.6f)
    val Check = stroke("check", "M5 12.5l4.5 4.5L19 7.5", width = 3.2f)
    val Plus = stroke("plus", "M12 5v14M5 12h14", width = 2.6f)
    val Play = filled("play", "M7 5.5v13a1 1 0 0 0 1.5.9l10.4-6.5a1 1 0 0 0 0-1.7L8.5 4.6A1 1 0 0 0 7 5.5z")
    val Pause = filled("pause", "M6.5 5h3a.5.5 0 0 1 .5.5v13a.5.5 0 0 1-.5.5h-3a.5.5 0 0 1-.5-.5v-13a.5.5 0 0 1 .5-.5z", "M14.5 5h3a.5.5 0 0 1 .5.5v13a.5.5 0 0 1-.5.5h-3a.5.5 0 0 1-.5-.5v-13a.5.5 0 0 1 .5-.5z")
    val Stop = filled("stop", "M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z")
    val Ear = stroke("ear", "M11 5L6 9H3v6h3l5 4V5z", "M15.5 8.5a5 5 0 0 1 0 7", "M18.5 5.5a9 9 0 0 1 0 13")
    val Search = stroke("search", circle(11f, 11f, 6.5f), "M16 16l4.5 4.5", width = 2.4f)
    val Lock = stroke("lock", "M7.5 11h9a2.5 2.5 0 0 1 2.5 2.5v4a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 17.5v-4A2.5 2.5 0 0 1 7.5 11z", "M8 11V8a4 4 0 0 1 8 0v3")
    val Heart = stroke("heart", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")
    val Loop = stroke("loop", "M17 2l3 3-3 3", "M4 11V9a4 4 0 0 1 4-4h12", "M7 22l-3-3 3-3", "M20 13v2a4 4 0 0 1-4 4H4", width = 2.4f)
    val Learn = stroke("learn", "M4 19V9l8-5 8 5v10", "M9.5 19v-5h5v5")
    val Songs = stroke("songs", "M9 18V5l11-2v13", circle(6.5f, 18f, 2.5f), circle(17.5f, 16f, 2.5f))
    val Games = stroke("games", "M7 7h10a4 4 0 0 1 4 4v3a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4v-3a4 4 0 0 1 4-4z", "M8 11v3", "M6.5 12.5h3", circle(15.5f, 11.5f, 0.6f), circle(17.5f, 13.5f, 0.6f))
    val Me = stroke("me", "M4 20c1.5-4 4.5-6 8-6s6.5 2 8 6", circle(12f, 8f, 4f))
    val Bluetooth = stroke("bt", "M7 7l10 10-5 5V2l5 5L7 17")
    val Usb = stroke("usb", "M12 3v13", "M8 7l4-4 4 4", "M7 13v3a5 5 0 0 0 10 0v-3", width = 2f)
    val Piano = stroke("piano", "M5.5 6h13a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 15.5v-7A2.5 2.5 0 0 1 5.5 6z", "M8 6v7", "M12 6v7", "M16 6v7", width = 2f)
    val File = stroke("file", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5", "M12 11v6", "M9 14l3 3 3-3", width = 2f)
    val Trash = stroke("trash", "M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13", width = 2f)
    val Metronome = stroke("metro", "M8 21h8l-2-17h-4z", "M12 15l5-8", width = 2f)
    val Star = filled("star", "M12 3l2.6 5.6 6.1.7-4.5 4.2 1.2 6L12 16.6 6.6 19.5l1.2-6-4.5-4.2 6.1-.7z")
}
