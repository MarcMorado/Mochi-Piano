package app.mochipiano.music

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class Hand { R, L }

/** One note. Times are in quarter-note beats from the start of the song. */
data class SNote(val start: Double, val dur: Double, val pitch: Int, val hand: Hand, val finger: Int = 0)

data class Section(val name: String, val start: Double, val end: Double)

data class Song(
    val id: String,
    val title: String,
    val composer: String,
    val bpm: Double,
    val barBeats: Double,
    val timeSig: String,
    val keyName: String,
    val notes: List<SNote>,
    val sections: List<Section>,
    val imported: Boolean = false,
    val lessonId: String? = null,
    val coverSeed: Int = 0,
) {
    val length: Double get() = notes.maxOfOrNull { it.start + it.dur } ?: 0.0
    val hands: Set<Hand> get() = notes.map { it.hand }.toSet()
    val bars: Int get() = ceil(length / barBeats - 1e-6).toInt().coerceAtLeast(1)
    fun count(h: Hand) = notes.count { it.hand == h }
    val pitchRange: IntRange get() = (notes.minOfOrNull { it.pitch } ?: 60)..(notes.maxOfOrNull { it.pitch } ?: 72)

    /** 1..10, from note density, hands, range and black keys. */
    val difficulty: Int
        get() {
            if (notes.isEmpty()) return 1
            val perBeat = notes.size / length.coerceAtLeast(1.0)
            val both = if (hands.size > 1) 2.0 else 0.0
            val span = (pitchRange.last - pitchRange.first) / 12.0
            val black = notes.count { Pitch.isBlack(it.pitch) }.toDouble() / notes.size
            val shortest = notes.minOf { it.dur }
            val fast = if (shortest < 0.5) 1.5 else if (shortest < 1.0) 0.5 else 0.0
            return (1 + perBeat * 1.6 + both + span + black * 3 + fast).roundToInt().coerceIn(1, 10)
        }

    fun sectionAt(beat: Double): Section? = sections.firstOrNull { beat >= it.start && beat < it.end }
    fun barOf(beat: Double): Int = (beat / barBeats).toInt() + 1

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("composer", composer); put("bpm", bpm); put("barBeats", barBeats)
        put("timeSig", timeSig); put("keyName", keyName); put("coverSeed", coverSeed)
        put("notes", JSONArray().apply {
            notes.forEach { n -> put(JSONArray().apply { put(n.start); put(n.dur); put(n.pitch); put(if (n.hand == Hand.R) 0 else 1); put(n.finger) }) }
        })
        put("sections", JSONArray().apply { sections.forEach { s -> put(JSONArray().apply { put(s.name); put(s.start); put(s.end) }) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Song {
            val na = o.getJSONArray("notes")
            val notes = (0 until na.length()).map { i ->
                val a = na.getJSONArray(i)
                SNote(a.getDouble(0), a.getDouble(1), a.getInt(2), if (a.getInt(3) == 0) Hand.R else Hand.L, a.optInt(4, 0))
            }
            val sa = o.optJSONArray("sections") ?: JSONArray()
            val sections = (0 until sa.length()).map { i -> val a = sa.getJSONArray(i); Section(a.getString(0), a.getDouble(1), a.getDouble(2)) }
            return Song(
                id = o.getString("id"), title = o.getString("title"), composer = o.optString("composer", ""),
                bpm = o.optDouble("bpm", 90.0), barBeats = o.optDouble("barBeats", 4.0), timeSig = o.optString("timeSig", "4/4"),
                keyName = o.optString("keyName", ""), notes = notes, sections = sections, imported = true,
                coverSeed = o.optInt("coverSeed", 0),
            )
        }

        /** Sections of [barsPer] bars named A, B, C… */
        fun autoSections(notes: List<SNote>, barBeats: Double, barsPer: Int = 4): List<Section> {
            val len = notes.maxOfOrNull { it.start + it.dur } ?: return emptyList()
            val span = barBeats * barsPer
            val out = mutableListOf<Section>()
            var s = 0.0
            var i = 0
            while (s < len - 1e-6) {
                out += Section(('A' + (i % 26)).toString(), s, minOf(s + span, len))
                s += span; i++
            }
            // Fold a tiny tail into the previous section.
            if (out.size > 1 && out.last().end - out.last().start < barBeats) {
                val tail = out.removeAt(out.size - 1)
                val prev = out.removeAt(out.size - 1)
                out += prev.copy(end = tail.end)
            }
            return out
        }

        fun keyName(fifths: Int, minor: Boolean): String {
            val majors = listOf("C♭", "G♭", "D♭", "A♭", "E♭", "B♭", "F", "C", "G", "D", "A", "E", "B", "F♯", "C♯")
            val minors = listOf("A♭", "E♭", "B♭", "F", "C", "G", "D", "A", "E", "B", "F♯", "C♯", "G♯", "D♯", "A♯")
            val i = (fifths + 7).coerceIn(0, 14)
            return if (minor) minors[i] + " minor" else majors[i] + " major"
        }

        fun nearly(a: Double, b: Double) = abs(a - b) < 1e-6
    }
}
