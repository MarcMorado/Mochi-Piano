package app.mochipiano.music

/**
 * The course songs, all public domain. Each line is "Note:beats" separated by spaces, lines by "|".
 * Right hand sits in C position (thumb on Middle C), left hand in left C position (pinky on C3).
 */
object BuiltInSongs {
    private val RH_FINGERS = mapOf(60 to 1, 62 to 2, 64 to 3, 65 to 4, 67 to 5)
    private val LH_FINGERS = mapOf(48 to 5, 50 to 4, 52 to 3, 53 to 2, 55 to 1)

    private fun line(src: String, hand: Hand, fingers: Boolean = true): List<SNote> {
        var t = 0.0
        val out = mutableListOf<SNote>()
        src.split('|').forEach { part ->
            part.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { tok ->
                val (n, d) = tok.split(':')
                val dur = d.toDouble()
                if (n != "R") {
                    val p = Pitch.parse(n)
                    val f = if (fingers) (if (hand == Hand.R) RH_FINGERS else LH_FINGERS)[p] ?: 0 else 0
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
        key: String = "C major", category: String = "Course", fingers: Boolean = true,
    ): Song {
        val notes = (rh?.let { line(it, Hand.R, fingers) } ?: emptyList()) + (lh?.let { line(it, Hand.L, fingers) } ?: emptyList())
        val lineLen = (rh ?: lh)!!.split('|').map { l -> l.trim().split(Regex("\\s+")).sumOf { it.split(':')[1].toDouble() } }
        var s = 0.0
        val secs = lineLen.mapIndexed { i, len -> Section(sections.getOrElse(i) { ('A' + i).toString() }, s, s + len).also { s += len } }
        return Song(id, title, by, bpm, barBeats, sig, key, notes.sortedBy { it.start }, secs, lessonId = lesson, coverSeed = seed, category = category)
    }

    /** A library song: open to everyone, no course lesson, fingering only when asked. */
    private fun lib(
        id: String, title: String, by: String, bpm: Double, cat: String, rh: String, lh: String? = null,
        sections: List<String> = emptyList(), barBeats: Double = 4.0, sig: String = "4/4", key: String = "C major",
        seed: Int, fingers: Boolean = false,
    ) = song(id, title, by, bpm, rh, lh, sections, null, barBeats, sig, seed, key, cat, fingers)

    private val core: List<Song> = listOf(
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

        // ---------------------------------------------------------------- kids & folk
        lib(
            "lightlyrow", "Lightly Row", "German folk song", 88.0, KIDS,
            rh = "G4:1 E4:1 E4:2 F4:1 D4:1 D4:2 C4:1 D4:1 E4:1 F4:1 G4:1 G4:1 G4:2|G4:1 E4:1 E4:2 F4:1 D4:1 D4:2 C4:1 E4:1 G4:1 G4:1 C4:4|" +
                "D4:1 D4:1 D4:1 D4:1 D4:1 E4:1 F4:2 E4:1 E4:1 E4:1 E4:1 E4:1 F4:1 G4:2|G4:1 E4:1 E4:2 F4:1 D4:1 D4:2 C4:1 E4:1 G4:1 G4:1 C4:4",
            lh = "C3:4 G3:4 C3:4 C3:4|C3:4 G3:4 C3:2 G3:2 C3:4|G3:4 G3:4 C3:4 C3:4|C3:4 G3:4 C3:2 G3:2 C3:4",
            sections = listOf("A", "A′", "B", "A′"), seed = 7, fingers = true,
        ),
        lib(
            "entchen", "Alle meine Entchen", "German folk song", 92.0, KIDS,
            rh = "C4:1 D4:1 E4:1 F4:1 G4:2 G4:2|A4:1 A4:1 A4:1 A4:1 G4:4 A4:1 A4:1 A4:1 A4:1 G4:4|F4:1 F4:1 F4:1 F4:1 E4:2 E4:2 D4:1 D4:1 D4:1 D4:1 C4:4",
            lh = "C3:4 C3:4|F3:4 C3:4 F3:4 C3:4|G3:4 C3:4 G3:4 C3:4",
            sections = listOf("A", "B", "C"), seed = 8, fingers = true,
        ),
        lib(
            "twinkle", "Twinkle Twinkle Little Star", "Traditional", 90.0, KIDS,
            rh = "C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2|F4:1 F4:1 E4:1 E4:1 D4:1 D4:1 C4:2|G4:1 G4:1 F4:1 F4:1 E4:1 E4:1 D4:2|" +
                "G4:1 G4:1 F4:1 F4:1 E4:1 E4:1 D4:2|C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2|F4:1 F4:1 E4:1 E4:1 D4:1 D4:1 C4:2",
            lh = "C3:4 F3:2 C3:2|F3:2 C3:2 G3:2 C3:2|C3:2 F3:2 C3:2 G3:2|C3:2 F3:2 C3:2 G3:2|C3:4 F3:2 C3:2|F3:2 C3:2 G3:2 C3:2",
            sections = listOf("A", "B", "C", "C′", "A", "B"), seed = 9,
        ),
        lib(
            "frere", "Frère Jacques", "French round", 96.0, KIDS,
            rh = "C4:1 D4:1 E4:1 C4:1 C4:1 D4:1 E4:1 C4:1|E4:1 F4:1 G4:2 E4:1 F4:1 G4:2|" +
                "G4:.5 A4:.5 G4:.5 F4:.5 E4:1 C4:1 G4:.5 A4:.5 G4:.5 F4:.5 E4:1 C4:1|C4:1 G3:1 C4:2 C4:1 G3:1 C4:2",
            lh = "C3:4 C3:4|C3:4 C3:4|C3:4 C3:4|C3:4 C3:4",
            sections = listOf("A", "B", "C", "D"), seed = 10,
        ),
        lib(
            "rowboat", "Row, Row, Row Your Boat", "Traditional", 66.0, KIDS, barBeats = 3.0, sig = "6/8",
            rh = "C4:1.5 C4:1.5 C4:1 D4:.5 E4:1.5|E4:1 D4:.5 E4:1 F4:.5 G4:3|" +
                "C5:.5 C5:.5 C5:.5 G4:.5 G4:.5 G4:.5 E4:.5 E4:.5 E4:.5 C4:.5 C4:.5 C4:.5|G4:1 F4:.5 E4:1 D4:.5 C4:3",
            lh = "C3:3 C3:3|C3:3 C3:3|C3:3 C3:3|G2:3 C3:3",
            sections = listOf("A", "B", "C", "D"), seed = 11,
        ),
        lib(
            "london", "London Bridge", "Traditional", 96.0, KIDS,
            rh = "G4:1.5 A4:.5 G4:1 F4:1 E4:1 F4:1 G4:2 D4:1 E4:1 F4:2 E4:1 F4:1 G4:2|" +
                "G4:1.5 A4:.5 G4:1 F4:1 E4:1 F4:1 G4:2 D4:2 G4:2 E4:1 C4:3",
            lh = "C3:4 C3:4 G2:4 C3:4|C3:4 C3:4 G2:4 C3:4",
            sections = listOf("A", "A′"), seed = 12,
        ),
        lib(
            "yankee", "Yankee Doodle", "Traditional", 112.0, KIDS,
            rh = "C4:1 C4:1 D4:1 E4:1 C4:1 E4:1 D4:1 G3:1|C4:1 C4:1 D4:1 E4:1 C4:2 B3:2|C4:1 C4:1 D4:1 E4:1 F4:1 E4:1 D4:1 C4:1|B3:1 G3:1 A3:1 B3:1 C4:2 C4:2",
            lh = "C3:4 C3:2 G2:2|C3:4 C3:2 G2:2|C3:4 F2:2 C3:2|G2:4 C3:4",
            sections = listOf("A", "A′", "B", "C"), seed = 13,
        ),
        lib(
            "birthday", "Happy Birthday", "Traditional", 92.0, KIDS, barBeats = 3.0, sig = "3/4",
            rh = "R:2 G3:.75 G3:.25 A3:1 G3:1 C4:1 B3:2 G3:.75 G3:.25|A3:1 G3:1 D4:1 C4:2 G3:.75 G3:.25|G4:1 E4:1 C4:1 B3:1 A3:1 F4:.75 F4:.25|E4:1 C4:1 D4:1 C4:3",
            lh = "R:3 C3:3 G2:3|G2:3 C3:3|C3:3 F2:3|C3:2 G2:1 C3:3",
            sections = listOf("A", "B", "C", "D"), seed = 14,
        ),
        lib(
            "saints", "When the Saints Go Marching In", "Traditional spiritual", 120.0, KIDS,
            rh = "R:1 C4:1 E4:1 F4:1 G4:4 R:1 C4:1 E4:1 F4:1 G4:4|R:1 C4:1 E4:1 F4:1 G4:2 E4:2 C4:2 E4:2 D4:4|" +
                "R:1 E4:1 E4:1 D4:1 C4:3 C4:1 E4:2 G4:1 G4:1 F4:4|R:2 E4:1 F4:1 G4:2 E4:2 C4:2 D4:2 C4:4",
            lh = "C3:4 C3:4 C3:4 C3:4|C3:4 C3:4 C3:4 G2:4|C3:4 C3:4 C3:4 F2:4|C3:4 C3:4 G2:4 C3:4",
            sections = listOf("A", "B", "C", "D"), seed = 15,
        ),
        lib(
            "auldlang", "Auld Lang Syne", "Scottish traditional", 80.0, KIDS,
            rh = "R:3 G3:1 C4:1.5 C4:.5 C4:1 E4:1 D4:1.5 C4:.5 D4:1 E4:1 C4:1.5 C4:.5 E4:1 G4:1 A4:3 A4:1|" +
                "G4:1.5 E4:.5 E4:1 C4:1 D4:1.5 C4:.5 D4:1 E4:.5 D4:.5 C4:1.5 A3:.5 A3:1 G3:1 C4:3",
            lh = "R:4 C3:4 G2:4 C3:4 F2:4|C3:4 G2:4 F2:2 G2:2 C3:3",
            sections = listOf("A", "B"), seed = 16,
        ),
        lib(
            "amazing", "Amazing Grace", "J. Newton, 1779", 72.0, KIDS, barBeats = 3.0, sig = "3/4", key = "G major",
            rh = "R:2 D4:1 G4:2 B4:.5 G4:.5 B4:2 A4:1 G4:2 E4:1 D4:2 D4:1|G4:2 B4:.5 G4:.5 B4:2 A4:1 D5:3 D5:2 B4:1|" +
                "D5:2 B4:.5 G4:.5 B4:2 A4:1 G4:2 E4:1 D4:2 D4:1|G4:2 B4:.5 G4:.5 B4:2 A4:1 G4:3",
            lh = "R:3 G2:3 G2:3 C3:3 G2:3|G2:3 D3:3 G2:3 G2:3|G2:3 G2:3 C3:3 G2:3|G2:3 D3:3 G2:3",
            sections = listOf("A", "B", "C", "D"), seed = 17,
        ),
        lib(
            "greensleeves", "Greensleeves", "English traditional", 84.0, KIDS, barBeats = 3.0, sig = "3/4", key = "A minor",
            rh = "R:2 A4:1 C5:2 D5:1 E5:1.5 F5:.5 E5:1 D5:2 B4:1 G4:1.5 A4:.5 B4:1|C5:2 A4:1 A4:1.5 G#4:.5 A4:1 B4:2 G#4:1 E4:2 A4:1|" +
                "C5:2 D5:1 E5:1.5 F5:.5 E5:1 D5:2 B4:1 G4:1.5 A4:.5 B4:1|C5:1.5 B4:.5 A4:1 G#4:1.5 F#4:.5 G#4:1 A4:3|" +
                "G5:3 G5:1.5 F#5:.5 E5:1 D5:2 B4:1 G4:1.5 A4:.5 B4:1|C5:2 A4:1 A4:1.5 G#4:.5 A4:1 B4:2 G#4:1 E4:3|" +
                "G5:3 G5:1.5 F#5:.5 E5:1 D5:2 B4:1 G4:1.5 A4:.5 B4:1|C5:1.5 B4:.5 A4:1 G#4:1.5 F#4:.5 G#4:1 A4:3",
            lh = "R:3 A2:3 C3:3 G2:3 E2:3|A2:3 F2:3 E2:3 E2:3|A2:3 C3:3 G2:3 E2:3|A2:3 E2:3 A2:3|" +
                "C3:3 C3:3 G2:3 E2:3|A2:3 F2:3 E2:3 E2:3|C3:3 C3:3 G2:3 E2:3|A2:3 E2:3 A2:3",
            sections = listOf("A", "A′", "A", "A″", "B", "B′", "B", "B″"), seed = 18,
        ),

        // ---------------------------------------------------------------- holidays
        lib(
            "silentnight", "Silent Night", "F. X. Gruber, 1818", 76.0, HOLIDAYS, barBeats = 3.0, sig = "3/4",
            rh = "G4:1.5 A4:.5 G4:1 E4:3 G4:1.5 A4:.5 G4:1 E4:3|D5:2 D5:1 B4:3 C5:2 C5:1 G4:3|" +
                "A4:2 A4:1 C5:1.5 B4:.5 A4:1 G4:1.5 A4:.5 G4:1 E4:3|A4:2 A4:1 C5:1.5 B4:.5 A4:1 G4:1.5 A4:.5 G4:1 E4:3|" +
                "D5:2 D5:1 F5:1.5 D5:.5 B4:1 C5:3 E5:3|C5:1.5 G4:.5 E4:1 G4:1.5 F4:.5 D4:1 C4:6",
            lh = "C3:3 C3:3 C3:3 C3:3|G2:3 G2:3 C3:3 C3:3|F2:3 F2:3 C3:3 C3:3|F2:3 F2:3 C3:3 C3:3|G2:3 G2:3 C3:3 C3:3|C3:3 G2:3 C3:6",
            sections = listOf("A", "B", "C", "C′", "D", "E"), seed = 19,
        ),
        lib(
            "wewish", "We Wish You a Merry Christmas", "English carol", 100.0, HOLIDAYS, barBeats = 3.0, sig = "3/4",
            rh = "R:2 G3:1 C4:1 C4:.5 D4:.5 C4:.5 B3:.5 A3:1 A3:1 A3:1|D4:1 D4:.5 E4:.5 D4:.5 C4:.5 B3:1 G3:1 G3:1|" +
                "E4:1 E4:.5 F4:.5 E4:.5 D4:.5 C4:1 A3:1 G3:.5 G3:.5|A3:1 D4:1 B3:1 C4:2",
            lh = "R:3 C3:3 A2:3|D3:3 G2:3|E3:3 A2:3|F2:2 G2:1 C3:2",
            sections = listOf("A", "B", "C", "D"), seed = 20,
        ),
        lib(
            "deckhalls", "Deck the Halls", "Welsh carol", 104.0, HOLIDAYS,
            rh = "G4:1.5 F4:.5 E4:1 D4:1 C4:1 D4:1 E4:1 C4:1 D4:.5 E4:.5 F4:.5 D4:.5 E4:1.5 D4:.5 C4:1 B3:1 C4:2|" +
                "G4:1.5 F4:.5 E4:1 D4:1 C4:1 D4:1 E4:1 C4:1 D4:.5 E4:.5 F4:.5 D4:.5 E4:1.5 D4:.5 C4:1 B3:1 C4:2|" +
                "D4:1.5 E4:.5 F4:1 D4:1 E4:1.5 F4:.5 G4:1 D4:1 E4:.5 F#4:.5 G4:1 A4:.5 B4:.5 C5:1 B4:1 A4:1 G4:2|" +
                "G4:1.5 F4:.5 E4:1 D4:1 C4:1 D4:1 E4:1 C4:1 A4:.5 A4:.5 A4:.5 A4:.5 G4:1.5 F4:.5 E4:1 D4:1 C4:2",
            lh = "C3:4 C3:4 G2:2 C3:2 G2:2 C3:2|C3:4 C3:4 G2:2 C3:2 G2:2 C3:2|G2:4 C3:2 G2:2 C3:2 D3:2 G2:4|C3:4 C3:4 F2:2 C3:2 G2:2 C3:2",
            sections = listOf("A", "A′", "B", "A″"), seed = 21,
        ),
        lib(
            "joyworld", "Joy to the World", "G. F. Handel, arr. L. Mason", 92.0, HOLIDAYS, barBeats = 2.0, sig = "2/4",
            rh = "C5:1 B4:.75 A4:.25 G4:1.5 F4:.5 E4:1 D4:1 C4:1.5 G4:.5|A4:1.5 A4:.5 B4:1.5 B4:.5 C5:1.5 C5:.5 C5:.5 B4:.5 A4:.5 G4:.5|" +
                "G4:.75 F4:.25 E4:.5 C5:.5 C5:.5 B4:.5 A4:.5 G4:.5 G4:.75 F4:.25 E4:.5 E4:.5 E4:.5 E4:.5 E4:.5 E4:.25 F4:.25|" +
                "G4:1.5 F4:.25 E4:.25 D4:.5 D4:.5 D4:.5 D4:.25 E4:.25 F4:1.5 E4:.25 D4:.25 C4:.5 C5:1 A4:.5|G4:.75 F4:.25 E4:.5 F4:.5 E4:1 D4:1 C4:2",
            lh = "C3:2 C3:2 G2:2 C3:2|F2:2 G2:2 C3:2 C3:2|C3:2 C3:2 C3:2 C3:2|C3:2 G2:2 G2:2 C3:1 F2:1|C3:2 G2:2 C3:2",
            sections = listOf("A", "B", "C", "D", "E"), seed = 22,
        ),
        lib(
            "jinglefull", "Jingle Bells (both hands)", "J. Pierpont, 1857", 100.0, HOLIDAYS,
            rh = "E4:1 E4:1 E4:2 E4:1 E4:1 E4:2|E4:1 G4:1 C4:1.5 D4:.5 E4:4|F4:1 F4:1 F4:1.5 F4:.5 F4:1 E4:1 E4:1 E4:.5 E4:.5|E4:1 D4:1 D4:1 E4:1 D4:2 G4:2|" +
                "E4:1 E4:1 E4:2 E4:1 E4:1 E4:2|E4:1 G4:1 C4:1.5 D4:.5 E4:4|F4:1 F4:1 F4:1.5 F4:.5 F4:1 E4:1 E4:1 E4:.5 E4:.5|G4:1 G4:1 F4:1 D4:1 C4:4",
            lh = "C3:4 C3:4|C3:4 C3:4|F2:4 C3:4|G2:4 G2:4|C3:4 C3:4|C3:4 C3:4|F2:4 C3:4|G2:4 C3:4",
            sections = listOf("A", "A′", "B", "C", "A", "A′", "B", "D"), seed = 24,
        ),

    )

    const val KIDS = "Kids & folk"
    const val HOLIDAYS = "Holidays"
    const val CLASSICAL = "Classical"
    val categories = listOf("Course", KIDS, CLASSICAL, HOLIDAYS)

    /** Course songs, the hand-written library, and complete scores bundled in assets/library. */
    var all: List<Song> = core
        private set

    /** Reads the bundled complete scores (Mutopia Project and other free libraries). Called once at startup. */
    fun load(ctx: android.content.Context) {
        val am = ctx.assets
        val bundled = (am.list("library") ?: emptyArray()).filter { it.endsWith(".json") }.mapNotNull { f ->
            try { Song.fromJson(org.json.JSONObject(am.open("library/$f").bufferedReader().use { it.readText() }), imported = false) } catch (_: Exception) { null }
        }
        val ids = bundled.map { it.id }.toSet()
        all = core.filterNot { it.id in ids } + bundled
    }

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
