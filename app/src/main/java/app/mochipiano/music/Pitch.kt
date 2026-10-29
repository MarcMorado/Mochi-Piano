package app.mochipiano.music

/** MIDI pitch helpers. Middle C = 60 = "C4". */
object Pitch {
    private val SHARP_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val LETTERS = listOf('C', 'D', 'E', 'F', 'G', 'A', 'B')
    private val LETTER_SEMI = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val SOLFEGE = mapOf('C' to "Do", 'D' to "Re", 'E' to "Mi", 'F' to "Fa", 'G' to "Sol", 'A' to "La", 'B' to "Si")

    const val MIDDLE_C = 60

    fun pc(p: Int) = ((p % 12) + 12) % 12
    fun octave(p: Int) = p / 12 - 1
    fun isBlack(p: Int) = pc(p) in intArrayOf(1, 3, 6, 8, 10)
    fun name(p: Int) = SHARP_NAMES[pc(p)] + octave(p)
    fun letter(p: Int): Char = SHARP_NAMES[pc(p)][0]
    fun isSharp(p: Int) = SHARP_NAMES[pc(p)].length > 1

    /** Display name for a letter in the user's chosen naming. */
    fun display(letter: Char, solfege: Boolean): String = if (solfege) SOLFEGE[letter] ?: letter.toString() else letter.toString()
    fun displayPitch(p: Int, solfege: Boolean): String = display(letter(p), solfege) + if (isSharp(p)) "♯" else ""

    fun of(letter: Char, octave: Int, alter: Int = 0): Int =
        (octave + 1) * 12 + LETTER_SEMI[LETTERS.indexOf(letter)] + alter

    /** Parse "C4", "F#3", "Bb2". */
    fun parse(s: String): Int {
        val l = s[0]
        var i = 1
        var alter = 0
        while (i < s.length && (s[i] == '#' || s[i] == 'b')) { alter += if (s[i] == '#') 1 else -1; i++ }
        return of(l, s.substring(i).toInt(), alter)
    }

    /** Diatonic step index used for staff placement (C4 = 28). Sharps sit on the letter below. */
    fun diatonic(p: Int): Int = octave(p) * 7 + LETTERS.indexOf(letter(p))

    /** Index of a white key counted from C-1. */
    fun whiteIndex(p: Int): Int = diatonic(if (isBlack(p)) p - 1 else p)

    fun whiteFromIndex(i: Int): Int = of(LETTERS[((i % 7) + 7) % 7], Math.floorDiv(i, 7))

    /** Round a range out to whole octaves, C..B, with at least [minWhite] white keys. */
    fun octaveRange(lo: Int, hi: Int, minOctaves: Int = 1): IntRange {
        var a = (lo / 12) * 12
        var b = (hi / 12) * 12 + 11
        while ((b - a + 1) / 12 < minOctaves) { if (a > 36) a -= 12 else b += 12 }
        return a..b
    }
}
