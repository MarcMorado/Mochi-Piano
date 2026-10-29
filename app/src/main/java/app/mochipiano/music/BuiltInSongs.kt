package app.mochipiano.music

/**
 * The course songs, all public domain. Each line is "Note:beats" separated by spaces, lines by "|".
 * Right hand sits in C position (thumb on Middle C), left hand in left C position (pinky on C3).
 */
object BuiltInSongs {
    private val RH_FINGERS = mapOf(60 to 1, 62 to 2, 64 to 3, 65 to 4, 67 to 5)
    private val LH_FINGERS = mapOf(48 to 5, 50 to 4, 52 to 3, 53 to 2, 55 to 1)

    private fun line(src: String, hand: Hand): List<SNote> {
        var t = 0.0
        val out = mutableListOf<SNote>()
        src.split('|').forEach { part ->
            part.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { tok ->
                val (n, d) = tok.split(':')
                val dur = d.toDouble()
                if (n != "R") {
                    val p = Pitch.parse(n)
                    val f = (if (hand == Hand.R) RH_FINGERS else LH_FINGERS)[p] ?: 0
                    out += SNote(t, dur, p, hand, f)
                }
                t += dur
            }
        }
        return out
    }

    private fun song(
        id: String, title: String, by: String, bpm: Double, rh: String?, lh: String?,
        sections: List<String>, lesson: String?, barBeats: Double = 4.0, sig: String = "4/4", seed: Int,
    ): Song {
        val notes = (rh?.let { line(it, Hand.R) } ?: emptyList()) + (lh?.let { line(it, Hand.L) } ?: emptyList())
        val lineLen = (rh ?: lh)!!.split('|').map { l -> l.trim().split(Regex("\\s+")).sumOf { it.split(':')[1].toDouble() } }
        var s = 0.0
        val secs = lineLen.mapIndexed { i, len -> Section(sections.getOrElse(i) { ('A' + i).toString() }, s, s + len).also { s += len } }
        return Song(id, title, by, bpm, barBeats, sig, "C major", notes.sortedBy { it.start }, secs, lessonId = lesson, coverSeed = seed)
    }

    val all: List<Song> = listOf(
        song(
            "hotcross", "Hot Cross Buns", "Traditional", 66.0,
            rh = "E4:1 D4:1 C4:2 E4:1 D4:1 C4:2|C4:.5 C4:.5 C4:.5 C4:.5 D4:.5 D4:.5 D4:.5 D4:.5 E4:1 D4:1 C4:2",
            lh = null, sections = listOf("A", "B"), lesson = "cpos", seed = 1,
        ),
        song(
            "mary", "Mary Had a Little Lamb", "Traditional", 72.0,
            rh = "E4:1 D4:1 C4:1 D4:1 E4:1 E4:1 E4:2|D4:1 D4:1 D4:2 E4:1 G4:1 G4:2|E4:1 D4:1 C4:1 D4:1 E4:1 E4:1 E4:1 E4:1|D4:1 D4:1 E4:1 D4:1 C4:4",
            lh = "C3:4 C3:4|G3:4 C3:4|C3:4 C3:4|G3:4 C3:4",
            sections = listOf("A", "B", "A′", "C"), lesson = "mary", seed = 2,
        ),
        song(
            "lune", "Au Clair de la Lune", "French folk song", 72.0,
            rh = "C4:1 C4:1 C4:1 D4:1 E4:2 D4:2|C4:1 E4:1 D4:1 D4:1 C4:4|C4:1 C4:1 C4:1 D4:1 E4:2 D4:2|C4:1 E4:1 D4:1 D4:1 C4:4",
            lh = "C3:4 G3:4|C3:2 G3:2 C3:4|C3:4 G3:4|C3:2 G3:2 C3:4",
            sections = listOf("A", "A′", "A", "A′"), lesson = "lune", seed = 3,
        ),
        song(
            "jingle", "Jingle Bells", "J. Pierpont, 1857", 84.0,
            rh = "E4:1 E4:1 E4:2 E4:1 E4:1 E4:2|E4:1 G4:1 C4:1.5 D4:.5 E4:4|F4:1 F4:1 F4:1 F4:1 F4:1 E4:1 E4:1 E4:.5 E4:.5|E4:1 D4:1 D4:1 E4:1 D4:2 G4:2",
            lh = "C3:4 C3:4|C3:4 C3:4|F3:4 C3:4|G3:4 G3:4",
            sections = listOf("A", "B", "C", "D"), lesson = "jingle", seed = 4,
        ),
        song(
            "lhbuns", "Hot Cross Buns, left hand", "Traditional", 66.0,
            rh = null,
            lh = "E3:1 D3:1 C3:2 E3:1 D3:1 C3:2|C3:.5 C3:.5 C3:.5 C3:.5 D3:.5 D3:.5 D3:.5 D3:.5 E3:1 D3:1 C3:2",
            sections = listOf("A", "B"), lesson = "left", seed = 5,
        ),
        song(
            "ode", "Ode to Joy", "L. van Beethoven, 1824", 100.0,
            rh = "E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1|C4:1 C4:1 D4:1 E4:1 E4:1.5 D4:.5 D4:2|E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1|C4:1 C4:1 D4:1 E4:1 D4:1.5 C4:.5 C4:2",
            lh = "C3:4 G3:4|C3:4 G3:4|C3:4 G3:4|C3:4 G3:2 C3:2",
            sections = listOf("A", "B", "A′", "C"), lesson = "ode", seed = 6,
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
